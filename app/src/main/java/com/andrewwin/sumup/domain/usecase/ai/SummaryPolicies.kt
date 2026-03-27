package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.service.ExtractiveSummarizer
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.support.SummarySourceMeta
import com.andrewwin.sumup.domain.usecase.common.FormatArticleHeadlineUseCase
import javax.inject.Inject

class SummaryPreprocessPolicy @Inject constructor(
    private val articlePreprocessorUseCase: ArticlePreprocessorUseCase
) {
    fun preprocess(
        rawText: String,
        prefs: UserPreferences,
        context: SummaryContext
    ): ArticlePreprocessorUseCase.Output = articlePreprocessorUseCase.preprocess(rawText, prefs, context)
}

class SummaryCloudCallPolicy @Inject constructor(
    private val aiRepository: AiRepository
) {
    suspend fun summarize(content: String, pointsPerNews: Int): String =
        aiRepository.summarize(content = content, pointsPerNews = pointsPerNews)

    suspend fun askQuestion(content: String, prompt: String): String =
        aiRepository.askQuestion(content, prompt)
}

class SummaryParsePolicy @Inject constructor() {
    fun parseCompare(raw: String): CompareResponseJson = AiJsonResponseParser.parseCompare(raw)
}

class SummaryRenderPolicy @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val formatArticleHeadlineUseCase: FormatArticleHeadlineUseCase
) {
    suspend fun renderCloudCompare(
        parsed: CompareResponseJson,
        articles: List<Article>
    ): String {
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

    suspend fun renderLocalCompare(articles: List<Article>): String {
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

    suspend fun appendSourceMetadata(summaryText: String, articles: List<Article>): String {
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

    fun moveSourceToEndForSingleArticle(summaryText: String): String =
        normalizeSectionSourcePlacement(summaryText)

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

    private fun normalizeKey(value: String): String = value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

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

class SummaryFallbackPolicy @Inject constructor(
    private val formatExtractiveSummaryUseCase: FormatExtractiveSummaryUseCase
) {
    fun singleArticleFallback(
        title: String,
        fullContent: String,
        sentenceLimit: Int
    ): String = formatExtractiveSummaryUseCase.formatItem(
        title = title,
        sentences = ExtractiveSummarizer.summarize(fullContent, sentenceLimit),
        isScheduledReport = false,
        maxBullets = sentenceLimit
    )
}









