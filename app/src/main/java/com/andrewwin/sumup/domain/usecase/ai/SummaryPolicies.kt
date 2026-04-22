package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.service.ExtractiveSummarizer
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.support.DebugTrace
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
    suspend fun summarize(content: String, pointsPerNews: Int? = null): String {
        DebugTrace.d(
            "summary_cloud",
            "summarize pointsPerNews=$pointsPerNews contentChars=${content.length} contentPreview=${DebugTrace.preview(content, 240)}"
        )
        return aiRepository.summarize(content = content, pointsPerNews = pointsPerNews)
    }

    suspend fun askQuestion(content: String, prompt: String): String =
        aiRepository.askWithPrompt(content, prompt)
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
        val lines = mutableListOf<String>()
        val commonBlockLines = parsed.commonFacts
            .filter { it.text.isNotBlank() }
            .flatMap { commonFact ->
                val factLines = mutableListOf("— ${commonFact.text.trim()}")
                commonFact.sourceIds.mapNotNull { articleById[it] }.forEach { article ->
                    val sName = articleRepository.getSourceById(article.sourceId)?.name?.ifBlank { "Джерело" } ?: "Джерело"
                    val sUrl = article.url.takeIf { it.isNotBlank() } ?: articleRepository.getSourceById(article.sourceId)?.url.orEmpty()
                    factLines += "${SummarySourceMeta.PREFIX}$sName|$sUrl"
                }
                factLines
            }
            
        val differentBySource = mutableMapOf<String, MutableList<String>>()
        parsed.items.forEachIndexed { index, item ->
            val article = item.sourceId?.let { articleById[it] } ?: articles.getOrNull(index) ?: return@forEachIndexed
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceName = source?.name?.ifBlank { null } ?: "Джерело"
            val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
            val key = "$sourceName|$sourceUrl"
            val target = differentBySource.getOrPut(key) { mutableListOf() }
            target += item.uniqueDetails.map { it.trim() }.filter { it.isNotBlank() }
        }

        lines += ""
        lines += "Спільне:"
        lines += if (commonBlockLines.isNotEmpty()) {
            commonBlockLines
        } else {
            val fallbackText = parsed.commonTopic
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { "Хоча новини стосуються теми $it, спільних фрагментів знайдено не було" }
                ?: parsed.fallbackMessage?.ifBlank { null }
                ?: "Спільних фрагментів знайдено не було"
            listOf("— $fallbackText")
        }
        if (differentBySource.isNotEmpty()) {
            lines += ""
            lines += renderUniqueCompareBlocks(differentBySource)
        }
        return lines.joinToString("\n").trim()
    }

    suspend fun renderLocalCompare(articles: List<Article>): String {
        if (articles.isEmpty()) return ""
        val lines = mutableListOf<String>()
        val commonBySource = mutableMapOf<String, MutableList<String>>()
        val differentBySource = mutableMapOf<String, MutableList<String>>()
        for (article in articles) {
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceName = source?.name?.ifBlank { null } ?: "Джерело"
            val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
            val key = "$sourceName|$sourceUrl"
            val sentences = ExtractiveSummarizer.summarize(
                articleRepository.fetchFullContent(article),
                2
            )
            val prepared = sentences.map { it.trim() }.filter { it.isNotBlank() }
            if (prepared.isNotEmpty()) commonBySource.getOrPut(key) { mutableListOf() } += prepared.take(1)
            if (prepared.size > 1) differentBySource.getOrPut(key) { mutableListOf() } += prepared.drop(1).take(1)
        }
        lines += renderCompareBlocks(
            commonBySource = commonBySource,
            differentBySource = differentBySource,
            softDedupe = true
        )
        return lines.joinToString("\n").trim()
    }

    suspend fun appendSourceMetadata(summaryText: String, articles: List<Article>): String {
        if (summaryText.isBlank() || articles.isEmpty()) return summaryText
        val existingSourceMetaCount = summaryText.lines().count { it.trim().startsWith(SummarySourceMeta.PREFIX) }
        if (existingSourceMetaCount > 1) {
            DebugTrace.d("summary_render", "appendSourceMetadata skip existingMetaCount=$existingSourceMetaCount")
            return summaryText.trim()
        }

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
        return enrichedSections.joinToString("\n\n").also {
            DebugTrace.d("summary_render", "appendSourceMetadata sections=${enrichedSections.size} articles=${articles.size}")
        }
    }

    fun moveSourceToEndForSingleArticle(summaryText: String): String =
        normalizeSectionSourcePlacement(summaryText)

    private fun renderCompareBlocks(
        commonBySource: Map<String, List<String>>,
        differentBySource: Map<String, List<String>>,
        softDedupe: Boolean
    ): String {
        val sourceKeys = (commonBySource.keys + differentBySource.keys).distinct().toList()
        if (sourceKeys.isEmpty()) return "Немає достатньо даних для порівняння."

        val commonLines = mutableListOf<String>()
        val differentEntries = mutableListOf<Pair<String, String>>()
        val seenCommonStatements = mutableSetOf<String>()
        val seenDifferentStatements = mutableSetOf<String>()
        val seenCommonTokenSets = mutableListOf<Set<String>>()
        val seenDifferentTokenSets = mutableListOf<Set<String>>()

        sourceKeys.forEach { sourceKey ->
            if (commonLines.size >= MAX_COMPARE_LINES_PER_BLOCK && differentEntries.size >= MAX_COMPARE_LINES_PER_BLOCK) {
                return@forEach
            }
            val sourceName = sourceKey.substringBefore('|').ifBlank { "Джерело" }
            val sourceUrl = sourceKey.substringAfter('|', "")
            commonBySource[sourceKey]
                .orEmpty()
                .distinctBy { normalizeKey(it) }
                .take(2)
                .forEach { statement ->
                    if (commonLines.size >= MAX_COMPARE_LINES_PER_BLOCK) return@forEach
                    val normalized = normalizeKey(statement)
                    if (normalized.isBlank()) return@forEach
                    if (!seenCommonStatements.add(normalized)) return@forEach
                    if (softDedupe) {
                        val tokenSet = toComparableTokenSet(normalized)
                        if (isNearDuplicateTokenSet(tokenSet, seenCommonTokenSets)) return@forEach
                        if (tokenSet.isNotEmpty()) seenCommonTokenSets += tokenSet
                    }
                    commonLines += "— ${statement.trim()}"
                }
            differentBySource[sourceKey]
                .orEmpty()
                .distinctBy { normalizeKey(it) }
                .take(2)
                .forEach { statement ->
                    if (differentEntries.size >= MAX_COMPARE_LINES_PER_BLOCK) return@forEach
                    val normalized = normalizeKey(statement)
                    if (normalized.isBlank()) return@forEach
                    if (!seenDifferentStatements.add(normalized)) return@forEach
                    if (softDedupe) {
                        val tokenSet = toComparableTokenSet(normalized)
                        if (isNearDuplicateTokenSet(tokenSet, seenDifferentTokenSets)) return@forEach
                        if (tokenSet.isNotEmpty()) seenDifferentTokenSets += tokenSet
                    }
                    differentEntries += statement.trim() to "${SummarySourceMeta.PREFIX}$sourceName|${sourceUrl.trim()}"
                }
        }

        val lines = mutableListOf<String>()
        lines += "Спільне:"
        if (commonLines.isEmpty()) {
            lines += "— Спільних фрагментів не було знайдено"
        } else {
            lines.addAll(commonLines)
        }
        lines += ""
        lines += "Унікальне:"
        if (differentEntries.isEmpty()) {
            lines += "— Немає достатньо даних."
        } else {
            differentEntries.forEach { (text, sourceMeta) ->
                lines += "— $text"
                lines += sourceMeta
            }
        }
        return lines.joinToString("\n").trim()
    }

    private fun renderUniqueCompareBlocks(
        differentBySource: Map<String, List<String>>
    ): String {
        val differentEntries = mutableListOf<Pair<String, String>>()
        val seenDifferentStatements = mutableSetOf<String>()

        differentBySource.forEach { (sourceKey, statements) ->
            if (differentEntries.size >= MAX_COMPARE_LINES_PER_BLOCK) return@forEach
            val sourceName = sourceKey.substringBefore('|').ifBlank { "Джерело" }
            val sourceUrl = sourceKey.substringAfter('|', "")
            statements
                .distinctBy { normalizeKey(it) }
                .take(2)
                .forEach { statement ->
                    if (differentEntries.size >= MAX_COMPARE_LINES_PER_BLOCK) return@forEach
                    val normalized = normalizeKey(statement)
                    if (normalized.isBlank()) return@forEach
                    if (!seenDifferentStatements.add(normalized)) return@forEach
                    differentEntries += statement.trim() to "${SummarySourceMeta.PREFIX}$sourceName|${sourceUrl.trim()}"
                }
        }

        val lines = mutableListOf<String>()
        lines += "Відмінне:"
        if (differentEntries.isEmpty()) {
            lines += "— Немає достатньо даних."
        } else {
            differentEntries.forEach { (text, sourceMeta) ->
                lines += "— $text"
                lines += sourceMeta
            }
        }
        return lines.joinToString("\n").trim()
    }

    private fun normalizeKey(value: String): String = value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun toComparableTokenSet(normalized: String): Set<String> {
        return normalized
            .split(" ")
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 4 }
            .filterNot { it in SOFT_DEDUP_STOPWORDS }
            .toSet()
    }

    private fun isNearDuplicateTokenSet(
        candidate: Set<String>,
        existing: List<Set<String>>
    ): Boolean {
        if (candidate.isEmpty()) return false
        return existing.any { base ->
            if (base.isEmpty()) return@any false
            val intersection = candidate.intersect(base).size.toFloat()
            val union = candidate.union(base).size.toFloat().coerceAtLeast(1f)
            val jaccard = intersection / union
            val overlapOnCandidate = intersection / candidate.size.toFloat()
            val overlapOnBase = intersection / base.size.toFloat()
            jaccard >= 0.68f || overlapOnCandidate >= 0.82f || overlapOnBase >= 0.82f
        }
    }

    private companion object {
        const val MAX_COMPARE_LINES_PER_BLOCK = 5
        val SOFT_DEDUP_STOPWORDS = setOf(
            "який", "яка", "яке", "які", "цього", "цьому", "цьому", "цього", "цьому",
            "було", "була", "були", "дуже", "також", "після", "через", "може", "можуть",
            "about", "with", "from", "that", "this", "these", "those", "will", "have"
        )
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

class SummaryFallbackPolicy @Inject constructor(
    private val formatExtractiveSummaryUseCase: FormatExtractiveSummaryUseCase
) {
    fun singleArticleFallback(
        title: String,
        fullContent: String,
        sentenceLimit: Int
    ): String {
        val extractive = ExtractiveSummarizer.summarize(fullContent, sentenceLimit)
        DebugTrace.d(
            "summary_fallback",
            "singleArticleFallback title=${DebugTrace.preview(title, 120)} sentenceLimit=$sentenceLimit extractiveCount=${extractive.size} first=${DebugTrace.preview(extractive.firstOrNull(), 160)}"
        )
        return formatExtractiveSummaryUseCase.formatItem(
            title = title,
            sentences = extractive,
            isScheduledReport = false,
            maxBullets = sentenceLimit
        ).also {
            DebugTrace.d("summary_fallback", "singleArticleFallback renderedPreview=${DebugTrace.preview(it)}")
        }
    }
}









