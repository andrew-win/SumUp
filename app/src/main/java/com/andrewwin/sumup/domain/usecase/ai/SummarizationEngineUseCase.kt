package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.ExtractiveSummarizer
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.summary.SummarySourceMeta
import com.andrewwin.sumup.domain.usecase.BuildExtractiveSummaryUseCase
import com.andrewwin.sumup.domain.usecase.FormatArticleHeadlineUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SummarizationEngineUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val articleRepository: ArticleRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val preprocessorUseCase: ArticlePreprocessorUseCase,
    private val formatExtractiveSummaryUseCase: FormatExtractiveSummaryUseCase,
    private val formatArticleHeadlineUseCase: FormatArticleHeadlineUseCase,
    private val buildExtractiveSummaryUseCase: BuildExtractiveSummaryUseCase
) {

    suspend fun summarizeSingleArticle(article: Article): Result<String> = runCatching {
        summarizeSingleArticle(article, emptyList()).getOrThrow()
    }

    suspend fun summarizeSingleArticle(
        representative: Article,
        duplicates: List<Article>
    ): Result<String> = runCatching {
        val prefs = userPreferencesRepository.preferences.first()
        val source = articleRepository.getSourceById(representative.sourceId)
        val sourceType = source?.type ?: SourceType.RSS
        val formatted = formatArticleHeadlineUseCase(representative, sourceType)
        val fullContent = articleRepository.fetchFullContent(representative)
        val hasDuplicates = duplicates.isNotEmpty()
        val context = SummaryContext.SingleArticle(hasClusterDuplicates = hasDuplicates)
        val allArticles = listOf(representative) + duplicates

        if (hasDuplicates) {
            return@runCatching if (prefs.aiStrategy == AiStrategy.LOCAL) {
                buildLocalComparisonSummary(allArticles)
            } else {
                runCatching { buildCloudComparisonSummary(allArticles, context, prefs) }
                    .getOrElse { buildLocalComparisonSummary(allArticles) }
            }
        }

        if (prefs.aiStrategy == AiStrategy.LOCAL) {
            val local = formatExtractiveSummaryUseCase.formatItem(
                title = formatted.displayTitle,
                sentences = ExtractiveSummarizer.summarize(fullContent, context.extractiveSentencesLimit(prefs)),
                isScheduledReport = false
            )
            return@runCatching moveSourceToEndForSingleArticle(
                appendSourceMetadata(local, listOf(representative))
            )
        }

        val prep = preprocessorUseCase.preprocess(
            rawText = if (formatted.displayContent.isNotBlank()) formatted.displayContent else fullContent,
            prefs = prefs,
            context = context
        )
        prep.finalExtractiveText?.let {
            val local = formatExtractiveSummaryUseCase.formatItem(
                title = formatted.displayTitle,
                sentences = ExtractiveSummarizer.summarize(it, context.extractiveSentencesLimit(prefs)),
                isScheduledReport = false
            )
            return@runCatching moveSourceToEndForSingleArticle(
                appendSourceMetadata(local, listOf(representative))
            )
        }

        val cloudInput = "${formatted.displayTitle}: ${prep.textForCloud}"
        val cloud = try {
            aiRepository.summarize(content = cloudInput, pointsPerNews = context.pointsPerNews(prefs))
        } catch (_: Exception) {
            formatExtractiveSummaryUseCase.formatItem(
                title = formatted.displayTitle,
                sentences = ExtractiveSummarizer.summarize(fullContent, context.extractiveSentencesLimit(prefs)),
                isScheduledReport = false
            )
        }
        moveSourceToEndForSingleArticle(
            appendSourceMetadata(cloud, listOf(representative))
        )
    }

    suspend fun summarizeArticles(articles: List<Article>, context: SummaryContext): Result<String> = runCatching {
        if (articles.isEmpty()) return@runCatching ""
        val prefs = userPreferencesRepository.preferences.first()

        if (prefs.aiStrategy == AiStrategy.LOCAL) {
            val extractiveArticles = articles.take(context.extractiveNewsLimit(prefs))
            return@runCatching appendSourceMetadata(
                buildExtractiveSummary(extractiveArticles, context, prefs),
                extractiveArticles
            )
        }

        val cloudArticles = articles.take(context.cloudNewsLimit(prefs))
        val cloudInput = buildCloudInput(cloudArticles, context, prefs)

        val raw = try {
            aiRepository.summarize(content = cloudInput, pointsPerNews = context.pointsPerNews(prefs))
        } catch (_: Exception) {
            val fallbackArticles = articles.take(context.extractiveNewsLimit(prefs))
            return@runCatching appendSourceMetadata(
                buildExtractiveSummary(fallbackArticles, context, prefs),
                fallbackArticles
            )
        }

        appendSourceMetadata(raw, cloudArticles)
    }

    suspend fun summarizeRawContent(content: String): Result<String> = runCatching {
        val prefs = userPreferencesRepository.preferences.first()
        aiRepository.summarize(content = content, pointsPerNews = prefs.summaryItemsPerNewsInFeed)
    }

    private suspend fun buildCloudInput(
        articles: List<Article>,
        context: SummaryContext,
        prefs: UserPreferences
    ): String {
        val totalLimit = prefs.aiMaxCharsTotal.coerceAtLeast(1000)
        var remaining = totalLimit
        val chunks = mutableListOf<String>()

        for (article in articles) {
            if (remaining <= 0) break
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceType = source?.type ?: SourceType.RSS
            val formatted = formatArticleHeadlineUseCase(article, sourceType)
            val fullContent = articleRepository.fetchFullContent(article)
            val prepared = preprocessorUseCase.preprocess(fullContent, prefs, context)
            val content = prepared.textForCloud.take(remaining)
            remaining -= content.length
            chunks += "${formatted.displayTitle}: $content"
        }

        return chunks.joinToString("\n\n")
    }

    private suspend fun buildExtractiveSummary(
        articles: List<Article>,
        context: SummaryContext,
        prefs: UserPreferences
    ): String {
        val contentMap = linkedMapOf<String, String>()
        for (article in articles) {
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceType = source?.type ?: SourceType.RSS
            val formatted = formatArticleHeadlineUseCase(article, sourceType)
            contentMap[formatted.displayTitle] = articleRepository.fetchFullContent(article)
        }
        return buildExtractiveSummaryUseCase(
            headlines = contentMap.keys.toList(),
            contentMap = contentMap,
            topCount = articles.size.coerceAtLeast(1),
            sentencesPerArticle = context.extractiveSentencesLimit(prefs)
        )
    }

    private suspend fun buildCloudComparisonSummary(
        articles: List<Article>,
        context: SummaryContext,
        prefs: UserPreferences
    ): String {
        val input = buildComparisonCloudInput(articles, context, prefs)
        val prompt = """
            Return ONLY valid JSON.
            Schema:
            {
              "headline": "short event title",
              "items": [
                {
                  "source_id": "source id from input",
                  "common": ["sentence 1", "sentence 2"],
                  "different": ["sentence 1", "sentence 2"]
                }
              ]
            }
            Rules:
            - include each source from input.
            - exactly 2 sentences in common and different arrays.
            - use the same language as the source content.
            - avoid repeated or semantically duplicate sentences.
            - no markdown, no extra prose.
        """.trimIndent()
        val raw = aiRepository.askQuestion(input, prompt)
        val parsed = AiJsonResponseParser.parseCompare(raw)
        val articleById = articles.associateBy { it.id.toString() }
        val headline = parsed.headline?.ifBlank { null } ?: "Порівняння джерел"
        val lines = mutableListOf<String>()
        lines += "$headline:"
        val commonBySource = mutableMapOf<String, MutableList<String>>()
        val differentBySource = mutableMapOf<String, MutableList<String>>()
        parsed.items.forEach { item ->
            val article = item.sourceId?.let { articleById[it] } ?: return@forEach
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceName = source?.name?.ifBlank { null } ?: "Джерело"
            val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
            val key = "$sourceName|$sourceUrl"
            val target = commonBySource.getOrPut(key) { mutableListOf() }
            target += item.common.take(2).map { it.trim() }.filter { it.isNotBlank() }
        }
        parsed.items.forEach { item ->
            val article = item.sourceId?.let { articleById[it] } ?: return@forEach
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceName = source?.name?.ifBlank { null } ?: "Джерело"
            val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
            val key = "$sourceName|$sourceUrl"
            val target = differentBySource.getOrPut(key) { mutableListOf() }
            target += item.different.take(2).map { it.trim() }.filter { it.isNotBlank() }
        }
        if (commonBySource.isNotEmpty()) {
            lines += "Спільне:"
            appendGroupedSourceBlocks(lines, commonBySource)
        }
        if (differentBySource.isNotEmpty()) {
            if (commonBySource.isNotEmpty()) lines += ""
            lines += "Відмінне:"
            appendGroupedSourceBlocks(lines, differentBySource)
        }
        return lines.joinToString("\n").trim()
    }

    private suspend fun buildLocalComparisonSummary(articles: List<Article>): String {
        if (articles.isEmpty()) return ""
        val title = articles.first().title.ifBlank { "Порівняння джерел" }
        val lines = mutableListOf<String>()
        lines += "$title:"
        for (article in articles) {
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceName = source?.name?.ifBlank { null } ?: "Джерело"
            val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
            val sentences = ExtractiveSummarizer.summarize(
                articleRepository.fetchFullContent(article),
                2
            )
            for (sentence in sentences) {
                lines += "• ${sentence.trim()}"
            }
            if (sourceUrl.isNotBlank()) lines += "${SummarySourceMeta.PREFIX}$sourceName|$sourceUrl"
            lines += ""
        }
        return lines.joinToString("\n").trim()
    }

    private suspend fun buildComparisonCloudInput(
        articles: List<Article>,
        context: SummaryContext,
        prefs: UserPreferences
    ): String {
        val totalLimit = prefs.aiMaxCharsTotal.coerceAtLeast(1000)
        var remaining = totalLimit
        val blocks = mutableListOf<String>()
        for (article in articles) {
            if (remaining <= 0) break
            val source = articleRepository.getSourceById(article.sourceId)
            val prepared = preprocessorUseCase.preprocess(
                rawText = articleRepository.fetchFullContent(article),
                prefs = prefs,
                context = context
            )
            val content = prepared.textForCloud.take(remaining)
            remaining -= content.length
            blocks += buildString {
                append("source_id: ${article.id}\n")
                append("source_name: ${source?.name ?: "Невідоме"}\n")
                append("source_url: ${article.url}\n")
                append("title: ${article.title}\n")
                append("content: $content")
            }
        }
        return blocks.joinToString("\n\n")
    }

    private suspend fun appendSourceMetadata(summaryText: String, articles: List<Article>): String {
        if (summaryText.isBlank() || articles.isEmpty()) return summaryText

        data class SourceMeta(val titleKey: String, val sourceName: String, val sourceUrl: String)
        val metas = mutableListOf<SourceMeta>()
        for (article in articles) {
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceName = source?.name?.trim().orEmpty()
            val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
            if (sourceName.isBlank() || sourceUrl.isBlank()) continue
            val formatted = formatArticleHeadlineUseCase(article, source?.type ?: SourceType.RSS)
            metas.add(SourceMeta(normalizeKey(formatted.displayTitle), sourceName, sourceUrl))
        }
        if (metas.isEmpty()) return summaryText

        val sections = summaryText
            .split(Regex("\\n\\s*\\n"))
            .filter { it.isNotBlank() }
            .map { normalizeSectionSourcePlacement(it) }
        if (sections.isEmpty()) return summaryText

        val used = BooleanArray(metas.size)
        var fallbackIndex = 0
        val enrichedSections = sections.map { section ->
            val lines = section.lines().map { it.trimEnd() }
            if (lines.any { it.startsWith(SummarySourceMeta.PREFIX) }) {
                return@map normalizeSectionSourcePlacement(section)
            }
            val titleLine = lines.firstOrNull()?.trim().orEmpty()
            val titleKey = normalizeKey(titleLine.removeSuffix(":"))
            var exactIndex = -1
            for (i in metas.indices) {
                if (used[i]) continue
                if (metas[i].titleKey == titleKey) {
                    exactIndex = i
                    break
                }
            }
            val pickedIndex = if (exactIndex >= 0) {
                exactIndex
            } else {
                while (fallbackIndex < metas.size && used[fallbackIndex]) fallbackIndex++
                if (fallbackIndex < metas.size) fallbackIndex else -1
            }
            if (pickedIndex == -1) return@map normalizeSectionSourcePlacement(section)
            used[pickedIndex] = true
            val meta = metas[pickedIndex]
            normalizeSectionSourcePlacement(
                "$section\n${SummarySourceMeta.PREFIX}${meta.sourceName}|${meta.sourceUrl}"
            )
        }
        return enrichedSections.joinToString("\n\n")
    }

    private fun normalizeKey(value: String): String = value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun appendGroupedSourceBlocks(
        lines: MutableList<String>,
        grouped: Map<String, List<String>>
    ) {
        grouped.forEach { (sourceKey, statements) ->
            statements
                .distinctBy { normalizeKey(it) }
                .forEach { lines += "• $it" }
            val separator = sourceKey.lastIndexOf('|')
            if (separator > 0 && separator < sourceKey.lastIndex) {
                val sourceName = sourceKey.substring(0, separator).trim()
                val sourceUrl = sourceKey.substring(separator + 1).trim()
                if (sourceName.isNotBlank() && sourceUrl.isNotBlank()) {
                    lines += "${SummarySourceMeta.PREFIX}$sourceName|$sourceUrl"
                }
            }
            lines += ""
        }
        if (lines.lastOrNull().isNullOrBlank()) lines.removeAt(lines.lastIndex)
    }

    private fun moveSourceToEndForSingleArticle(summaryText: String): String {
        return normalizeSectionSourcePlacement(summaryText)
    }

    private fun normalizeSectionSourcePlacement(section: String): String {
        val lines = section.lines().map { it.trimEnd() }
        if (lines.isEmpty()) return section
        val sourceLines = lines
            .filter { it.startsWith(SummarySourceMeta.PREFIX) }
            .distinct()
        val contentLines = lines.filterNot { it.startsWith(SummarySourceMeta.PREFIX) }
        val normalizedContent = contentLines
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        if (sourceLines.isEmpty()) return normalizedContent
        return buildString {
            append(normalizedContent)
            append("\n")
            sourceLines.forEachIndexed { index, src ->
                if (index > 0) append("\n")
                append(src)
            }
        }.trim()
    }
}
