package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.support.DispatcherProvider
import com.andrewwin.sumup.domain.usecase.common.GetExtractiveSummaryUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.sqrt

class CompareNewsUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val articleRepository: ArticleRepository,
    private val shrinkTextForAdaptiveStrategyUseCase: ShrinkTextForAdaptiveStrategyUseCase,
    private val sendCloudAiRequestUseCase: SendCloudAiRequestUseCase,
    private val parseAiJsonResponseUseCase: ParseAiJsonResponseUseCase,
    private val generateLocalEmbeddingUseCase: GenerateLocalEmbeddingUseCase,
    private val getExtractiveSummaryUseCase: GetExtractiveSummaryUseCase,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(articles: List<Article>): Result<SummaryResult.Compare> = withContext(dispatcherProvider.default) {
        if (articles.size < 2) {
            return@withContext Result.failure(IllegalStateException("Недостатньо джерел для порівняння."))
        }

        val prefs = userPreferencesRepository.preferences.first()
        val strategy = prefs.aiStrategy

        // 1. Local Strategy
        if (strategy == AiStrategy.LOCAL) {
            return@withContext runCatching { performLocalComparison(articles, prefs) }
        }

        // 2. Cloud or Adaptive
        val cloudInput = buildString {
            for (article in articles) {
                val source = articleRepository.getSourceById(article.sourceId)
                val sourceName = source?.name?.trim()?.ifBlank { "Джерело" } ?: "Джерело"
                val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
                
                val fullContent = articleRepository.fetchFullContent(article)
                val contentToProcess = fullContent.ifBlank { article.content }
                
                val textForCloud = if (strategy == AiStrategy.ADAPTIVE) {
                    shrinkTextForAdaptiveStrategyUseCase(contentToProcess, prefs)
                } else {
                    contentToProcess.take(prefs.aiMaxCharsPerArticle.coerceAtLeast(1000))
                }

                append("source_id: ${article.id}\n")
                append("source_name: $sourceName\n")
                append("source_url: $sourceUrl\n")
                append("title: ${article.title}\n")
                append("content: $textForCloud\n\n")
            }
        }

        val prompt = AiPromptBuilder.buildComparePrompt(prefs.summaryLanguage)

        return@withContext runCatching {
            val jsonResponse = sendCloudAiRequestUseCase(prompt, cloudInput)
            parseAiJsonResponseUseCase.parseCompare(jsonResponse, cloudInput)
        }.recoverCatching {
            performLocalComparison(articles, prefs)
        }
    }

    private data class CompareCandidate(val text: String, val source: SummarySourceRef)

    private suspend fun performLocalComparison(articles: List<Article>, prefs: com.andrewwin.sumup.data.local.entities.UserPreferences): SummaryResult.Compare {
        if (articles.isEmpty()) return SummaryResult.Compare(emptyList(), emptyList())
        val candidates = mutableListOf<CompareCandidate>()
        var hasLocalModel = false
        
        for (article in articles) {
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceName = source?.name?.trim()?.ifBlank { "Джерело" } ?: "Джерело"
            val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
            if (sourceUrl.isBlank()) continue
            val sourceMeta = SummarySourceRef(name = sourceName, url = sourceUrl)
            
            val fullContent = articleRepository.fetchFullContent(article)
            val sentences = getExtractiveSummaryUseCase(fullContent, 5)
            val prepared = sentences.map { it.trim() }.filter { it.isNotBlank() }
            prepared.forEach { sentence ->
                candidates += CompareCandidate(text = sentence, source = sourceMeta)
            }
        }

        if (candidates.isEmpty()) return SummaryResult.Compare(emptyList(), emptyList())

        val embeddings = candidates.mapNotNull { candidate ->
            val embedding = generateLocalEmbeddingUseCase(candidate.text)
            if (embedding != null) hasLocalModel = true
            embedding
        }

        val jaccardSets = if (!hasLocalModel) {
            candidates.map { toJaccardWordSet(it.text) }
        } else emptyList()

        val matchedIndexes = List(candidates.size) { mutableSetOf<Int>() }

        for (left in 0 until candidates.lastIndex) {
            for (right in left + 1 until candidates.size) {
                val isMatch = if (hasLocalModel && left < embeddings.size && right < embeddings.size) {
                    cosineSimilarity(embeddings[left], embeddings[right]) >= SummaryLimits.Compare.localSimilarityThreshold
                } else {
                    jaccardSimilarity(jaccardSets[left], jaccardSets[right]) >= SummaryLimits.Compare.jaccardThreshold
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
                add(SummaryItem(text = candidate.text.trim(), sources = sources))
            }
        }
        .sortedWith(compareByDescending<SummaryItem> { it.sources.size }.thenByDescending { it.text.length })
        .take(5)

        val differentItems = buildList {
            val seen = mutableSetOf<String>()
            candidates.forEachIndexed { index, candidate ->
                if (matchedIndexes[index].isNotEmpty()) return@forEachIndexed
                val normalized = normalizeKey(candidate.text)
                if (!seen.add(normalized)) return@forEachIndexed
                add(SummaryItem(text = candidate.text.trim(), sources = listOf(candidate.source)))
            }
        }.take(5)

        if (commonItems.isEmpty()) {
            val fallbackItem = buildCentralFallbackItem(candidates)
            return SummaryResult.Compare(
                common = listOf(fallbackItem),
                unique = differentItems
            )
        }

        return SummaryResult.Compare(
            common = commonItems,
            unique = differentItems
        )
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

    private fun buildCentralFallbackItem(candidates: List<CompareCandidate>): SummaryItem {
        if (candidates.isEmpty()) {
            return SummaryItem(text = "Немає достатньо даних.", sources = emptyList())
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
        return SummaryItem(
            text = selectedText,
            sources = candidates.map { it.source }.distinctBy { it.url }
        )
    }
}
