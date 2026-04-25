package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.service.ExtractiveSummarizer
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.EmbeddingService
import com.andrewwin.sumup.domain.repository.ModelRepository
import com.andrewwin.sumup.domain.support.DebugTrace
import com.andrewwin.sumup.domain.support.SummarySourceMeta
import com.andrewwin.sumup.domain.usecase.common.FormatArticleHeadlineUseCase
import kotlin.math.sqrt
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
    private val formatArticleHeadlineUseCase: FormatArticleHeadlineUseCase,
    private val modelRepository: ModelRepository,
    private val embeddingService: EmbeddingService
) {
    private data class CompareCandidate(
        val text: String,
        val source: CompareSourceMeta
    )

    private data class CompareSourceMeta(
        val name: String,
        val url: String
    )

    private data class CompareRenderedItem(
        val text: String,
        val sources: List<CompareSourceMeta>
    )

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
                commonFact.sourceIds
                    .mapNotNull { sourceRef -> resolveArticleBySourceRef(sourceRef, articleById, articles) }
                    .distinctBy { it.id }
                    .forEach { article ->
                    val sName = articleRepository.getSourceById(article.sourceId)?.name?.ifBlank { "Джерело" } ?: "Джерело"
                    val sUrl = article.url.takeIf { it.isNotBlank() } ?: articleRepository.getSourceById(article.sourceId)?.url.orEmpty()
                    if (sUrl.isBlank()) return@forEach
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

    private fun resolveArticleBySourceRef(
        sourceRef: String,
        articleById: Map<String, Article>,
        articles: List<Article>
    ): Article? {
        val normalized = sourceRef.trim()
        if (normalized.isBlank()) return null

        articleById[normalized]?.let { return it }

        val digits = Regex("\\d+").find(normalized)?.value?.toLongOrNull()
        if (digits != null) {
            articleById[digits.toString()]?.let { return it }

            // Some models return ordinal refs like s1/source_1 (1-based index from input order).
            val ordinalIndex = (digits - 1L).toInt()
            if (ordinalIndex in articles.indices) {
                return articles[ordinalIndex]
            }
        }

        return null
    }

    suspend fun renderLocalCompare(
        articles: List<Article>,
        localSimilarityThreshold: Float
    ): String {
        if (articles.isEmpty()) return ""
        val candidates = mutableListOf<CompareCandidate>()
        for (article in articles) {
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceName = source?.name?.ifBlank { null } ?: "Джерело"
            val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
            if (sourceUrl.isBlank()) continue
            val sourceMeta = CompareSourceMeta(name = sourceName, url = sourceUrl)
            val sentences = ExtractiveSummarizer.summarize(
                articleRepository.fetchFullContent(article),
                LOCAL_COMPARE_SENTENCE_LIMIT
            )
            val prepared = sentences.map { it.trim() }.filter { it.isNotBlank() }
            prepared.forEach { sentence ->
                candidates += CompareCandidate(text = sentence, source = sourceMeta)
            }
        }
        val hasLocalModel = modelRepository.isModelExists() &&
            embeddingService.initialize(modelRepository.getModelPath())
        return renderCompareBlocks(
            candidates = candidates,
            hasLocalModel = hasLocalModel,
            localSimilarityThreshold = localSimilarityThreshold
        )
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

    private suspend fun renderCompareBlocks(
        candidates: List<CompareCandidate>,
        hasLocalModel: Boolean,
        localSimilarityThreshold: Float
    ): String {
        if (candidates.isEmpty()) return "Немає достатньо даних для порівняння."

        val embeddings = if (hasLocalModel) {
            candidates.map { embeddingService.getEmbedding(it.text.trim()) }
        } else {
            emptyList()
        }
        val jaccardSets = if (!hasLocalModel) {
            candidates.map { toJaccardWordSet(it.text) }
        } else {
            emptyList()
        }
        val matchedIndexes = List(candidates.size) { mutableSetOf<Int>() }

        for (left in 0 until candidates.lastIndex) {
            for (right in left + 1 until candidates.size) {
                val isMatch = if (hasLocalModel) {
                    cosineSimilarity(embeddings[left], embeddings[right]) >= localSimilarityThreshold
                } else {
                    jaccardSimilarity(jaccardSets[left], jaccardSets[right]) >= JACCARD_COMMON_THRESHOLD
                }
                if (isMatch) {
                    matchedIndexes[left] += right
                    matchedIndexes[right] += left
                }
            }
        }

        val commonItems = buildList {
            val seen = mutableSetOf<String>()
            candidates.forEachIndexed { index, candidate ->
                val matches = matchedIndexes[index]
                if (matches.isEmpty()) return@forEachIndexed
                val sources = buildList {
                    add(candidate.source)
                    matches.forEach { matchIndex -> add(candidates[matchIndex].source) }
                }.distinctBy { it.url }
                if (sources.size < 2) return@forEachIndexed
                val normalized = normalizeKey(candidate.text)
                val sourceKey = sources.joinToString("|") { it.url }
                val dedupeKey = "$normalized::$sourceKey"
                if (!seen.add(dedupeKey)) return@forEachIndexed
                add(
                    CompareRenderedItem(
                        text = candidate.text.trim(),
                        sources = sources
                    )
                )
            }
        }
            .sortedWith(compareByDescending<CompareRenderedItem> { it.sources.size }.thenByDescending { it.text.length })
            .take(MAX_COMPARE_LINES_PER_BLOCK)

        val differentItems = buildList {
            val seen = mutableSetOf<String>()
            candidates.forEachIndexed { index, candidate ->
                if (matchedIndexes[index].isNotEmpty()) return@forEachIndexed
                val normalized = normalizeKey(candidate.text)
                if (!seen.add(normalized)) return@forEachIndexed
                add(CompareRenderedItem(text = candidate.text.trim(), sources = listOf(candidate.source)))
            }
        }.take(MAX_COMPARE_LINES_PER_BLOCK)

        val lines = mutableListOf<String>()
        lines += "Спільне:"
        if (commonItems.isEmpty()) {
            val fallbackItem = buildCentralFallbackItem(candidates)
            lines += "— ${fallbackItem.text}"
            fallbackItem.sources.forEach { source ->
                lines += "${SummarySourceMeta.PREFIX}${source.name}|${source.url.trim()}"
            }
        } else {
            commonItems.forEach { item ->
                lines += "— ${item.text}"
                item.sources.forEach { source ->
                    lines += "${SummarySourceMeta.PREFIX}${source.name}|${source.url.trim()}"
                }
            }
        }
        lines += ""
        lines += "Унікальне:"
        if (differentItems.isEmpty()) {
            lines += "— Немає достатньо даних."
        } else {
            differentItems.forEach { item ->
                lines += "— ${item.text}"
                item.sources.forEach { source ->
                    lines += "${SummarySourceMeta.PREFIX}${source.name}|${source.url.trim()}"
                }
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

    private fun toJaccardWordSet(sentence: String): Set<String> {
        return sentence
            .lowercase()
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun jaccardSimilarity(candidate: Set<String>, base: Set<String>): Float {
        if (candidate.isEmpty() || base.isEmpty()) return 0f
        val intersection = candidate.intersect(base).size.toFloat()
        val union = candidate.union(base).size.toFloat().coerceAtLeast(1f)
        return intersection / union
    }

    private fun cosineSimilarity(candidate: FloatArray, base: FloatArray): Float {
        if (candidate.isEmpty() || base.isEmpty() || candidate.size != base.size) return 0f
        var dot = 0f
        var candidateNorm = 0f
        var baseNorm = 0f
        for (index in candidate.indices) {
            val c = candidate[index]
            val b = base[index]
            dot += c * b
            candidateNorm += c * c
            baseNorm += b * b
        }
        val denominator = sqrt(candidateNorm) * sqrt(baseNorm)
        if (denominator <= 0f) return 0f
        return dot / denominator
    }

    private fun buildCentralFallbackItem(candidates: List<CompareCandidate>): CompareRenderedItem {
        if (candidates.isEmpty()) {
            return CompareRenderedItem(
                text = "Немає достатньо даних.",
                sources = emptyList()
            )
        }
        val normalizedFrequencies = candidates
            .groupingBy { normalizeKey(it.text) }
            .eachCount()
        val selectedText = candidates
            .asSequence()
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
            .maxWithOrNull(
                compareBy<String> { normalizedFrequencies[normalizeKey(it)] ?: 0 }
                    .thenBy { it.length }
            )
            ?: "Немає достатньо даних."
        return CompareRenderedItem(
            text = selectedText,
            sources = candidates
                .map { it.source }
                .distinctBy { it.url }
        )
    }

    private companion object {
        const val MAX_COMPARE_LINES_PER_BLOCK = 5
        const val JACCARD_COMMON_THRESHOLD = 0.2f
        const val LOCAL_COMPARE_SENTENCE_LIMIT = 5
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









