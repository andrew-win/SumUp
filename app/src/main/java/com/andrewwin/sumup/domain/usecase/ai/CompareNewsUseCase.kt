package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.service.LocalEmbeddingService
import com.andrewwin.sumup.domain.service.SimilarityScorer
import com.andrewwin.sumup.domain.support.DispatcherProvider
import com.andrewwin.sumup.domain.support.LocalModelMissingException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject


class CompareNewsUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val articleRepository: ArticleRepository,
    private val shrinkTextForAdaptiveStrategyUseCase: ShrinkTextForAdaptiveStrategyUseCase,
    private val sendCloudAiRequestUseCase: SendCloudAiRequestUseCase,
    private val parseAiJsonResponseUseCase: ParseAiJsonResponseUseCase,
    private val getExtractiveSummaryUseCase: GetExtractiveSummaryUseCase,
    private val localEmbeddingService: LocalEmbeddingService,
    private val similarityScorer: SimilarityScorer,
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
                    contentToProcess.take(prefs.aiMaxCharsPerArticle)
                }

                append("source_id: ${article.id}\n")
                append("source_name: $sourceName\n")
                append("source_url: $sourceUrl\n")
                append("title: ${article.title}\n")
                append("content: $textForCloud\n\n")
            }
        }

        val customPrompt = prefs.summaryPrompt.takeIf { prefs.isCustomSummaryPromptEnabled }
        val prompt = AiPromptBuilder.buildComparePrompt(prefs.summaryLanguage, customPrompt)

        return@withContext runCatching {
            val jsonResponse = sendCloudAiRequestUseCase(prompt, cloudInput)
            parseAiJsonResponseUseCase.parseCompare(jsonResponse, cloudInput)
        }.recoverCatching {
            performLocalComparison(articles, prefs)
        }
    }

    private data class CompareCandidate(
        val text: String,
        val source: SummarySourceRef,
        val articleId: Long
    )

    private data class LocalClusterSentenceCandidate(
        val text: String,
        val source: SummarySourceRef,
        val articleId: Long
    )

    private data class ScoredSentenceMatch(
        val leftIndex: Int,
        val rightIndex: Int,
        val score: Float
    )

    private suspend fun performLocalComparison(articles: List<Article>, prefs: com.andrewwin.sumup.data.local.entities.UserPreferences): SummaryResult.Compare {
        if (articles.isEmpty()) return SummaryResult.Compare(emptyList(), emptyList())

        if (!localEmbeddingService.initialize()) {
            throw LocalModelMissingException()
        }

        val candidatesByArticle = articles.associate { article ->
            article.id to buildLocalClusterSentenceCandidates(article)
        }

        val requiredSourceItems = buildRequiredLocalClusterSummaryItems(
            articles = articles,
            candidatesByArticle = candidatesByArticle
        )
        val selectedItems = requiredSourceItems.toMutableList()
        val selectedTexts = requiredSourceItems.map { it.text }.toMutableList()

        val remainingSlots = (SummaryLimits.LocalClusterSummary.maxSummarySentences - selectedItems.size)
            .coerceAtLeast(0)
        if (remainingSlots > 0) {
            val remainingCandidates = articles
                .flatMap { article -> candidatesByArticle[article.id].orEmpty() }
                .filterNot { candidate -> selectedTexts.any { it == candidate.text } }

            for (candidate in remainingCandidates) {
                if (selectedItems.size >= SummaryLimits.LocalClusterSummary.maxSummarySentences) break
                if (isNearDuplicateOfSelected(candidate.text, selectedTexts)) continue

                selectedItems += SummaryItem(
                    text = candidate.text,
                    sources = listOf(candidate.source)
                )
                selectedTexts += candidate.text
            }
        }

        return SummaryResult.Compare(
            common = selectedItems,
            unique = emptyList()
        )
    }

    private suspend fun buildLocalClusterSentenceCandidates(article: Article): List<LocalClusterSentenceCandidate> {
        val source = articleRepository.getSourceById(article.sourceId)
        val sourceName = source?.name?.trim()?.ifBlank { "Джерело" } ?: "Джерело"
        val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
        val sourceMeta = SummarySourceRef(name = sourceName, url = sourceUrl)

        val fullContent = articleRepository.fetchFullContent(article)
        return getExtractiveSummaryUseCase(
            fullContent,
            SummaryLimits.LocalClusterSummary.candidateSentencesPerSource
        )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .map { sentence ->
                LocalClusterSentenceCandidate(
                    text = sentence,
                    source = sourceMeta,
                    articleId = article.id
                )
            }
    }

    private suspend fun buildRequiredLocalClusterSummaryItems(
        articles: List<Article>,
        candidatesByArticle: Map<Long, List<LocalClusterSentenceCandidate>>
    ): List<SummaryItem> {
        val maxRequiredSources = SummaryLimits.LocalClusterSummary.maxSummarySentences /
            SummaryLimits.LocalClusterSummary.minSentencesPerSource
        val selectedTexts = mutableListOf<String>()
        val items = mutableListOf<SummaryItem>()

        for (article in articles.take(maxRequiredSources)) {
            val articleCandidates = candidatesByArticle[article.id].orEmpty()
            val selectedCandidate = articleCandidates.firstOrNull { candidate ->
                !isNearDuplicateOfSelected(candidate.text, selectedTexts)
            } ?: articleCandidates.firstOrNull()

            if (selectedCandidate != null) {
                items += SummaryItem(
                    text = selectedCandidate.text,
                    sources = listOf(selectedCandidate.source)
                )
                selectedTexts += selectedCandidate.text
            }
        }

        return items
    }

    private suspend fun isNearDuplicateOfSelected(
        candidateText: String,
        selectedTexts: List<String>
    ): Boolean {
        if (selectedTexts.isEmpty()) return false
        val candidateEmbedding = localEmbeddingService.computeLocalEmbedding(candidateText)
        if (candidateEmbedding.isEmpty()) return selectedTexts.any { it == candidateText }

        return selectedTexts.any { selectedText ->
            val selectedEmbedding = localEmbeddingService.computeLocalEmbedding(selectedText)
            selectedEmbedding.isNotEmpty() &&
                similarityScorer.calculateSimilarity(candidateEmbedding, selectedEmbedding) >=
                SummaryLimits.LocalClusterSummary.nearDuplicateThreshold
        }
    }

    private fun calculateCandidateSimilarity(
        leftIndex: Int,
        rightIndex: Int,
        embeddings: List<FloatArray>,
        bestScoreToOtherArticle: FloatArray,
        bestScoreBelowThreshold: FloatArray,
        commonThreshold: Float
    ): Float {
        val score = similarityScorer.calculateSimilarity(
            embeddingA = embeddings[leftIndex],
            embeddingB = embeddings[rightIndex]
        )

        if (score >= commonThreshold) {
            bestScoreToOtherArticle[leftIndex] = maxOf(bestScoreToOtherArticle[leftIndex], score)
            bestScoreToOtherArticle[rightIndex] = maxOf(bestScoreToOtherArticle[rightIndex], score)
        } else {
            bestScoreBelowThreshold[leftIndex] = maxOf(bestScoreBelowThreshold[leftIndex], score)
            bestScoreBelowThreshold[rightIndex] = maxOf(bestScoreBelowThreshold[rightIndex], score)
        }

        return score
    }

    private fun buildBestMatchClusters(
        candidateCount: Int,
        graphEdges: List<ScoredSentenceMatch>
    ): List<List<Int>> {
        val adjacency = Array(candidateCount) { mutableSetOf<Int>() }

        graphEdges.forEach { edge ->
            adjacency[edge.leftIndex].add(edge.rightIndex)
            adjacency[edge.rightIndex].add(edge.leftIndex)
        }

        val visited = BooleanArray(candidateCount)
        val clusters = mutableListOf<List<Int>>()

        for (startIndex in 0 until candidateCount) {
            if (visited[startIndex]) continue

            val stack = ArrayDeque<Int>()
            val cluster = mutableListOf<Int>()
            stack.add(startIndex)
            visited[startIndex] = true

            while (stack.isNotEmpty()) {
                val currentIndex = stack.removeLast()
                cluster.add(currentIndex)

                adjacency[currentIndex].forEach { nextIndex ->
                    if (!visited[nextIndex]) {
                        visited[nextIndex] = true
                        stack.add(nextIndex)
                    }
                }
            }

            clusters.add(cluster)
        }

        return clusters
    }

    private fun selectRepresentativeCandidateIndex(
        cluster: List<Int>,
        graphEdges: List<ScoredSentenceMatch>,
        bestScoreToOtherArticle: FloatArray
    ): Int {
        if (cluster.size == 1) return cluster.first()

        val clusterSet = cluster.toSet()
        val summedClusterEdgeScores = mutableMapOf<Int, Float>()

        graphEdges.forEach { edge ->
            if (edge.leftIndex in clusterSet && edge.rightIndex in clusterSet) {
                summedClusterEdgeScores[edge.leftIndex] =
                    (summedClusterEdgeScores[edge.leftIndex] ?: 0f) + edge.score
                summedClusterEdgeScores[edge.rightIndex] =
                    (summedClusterEdgeScores[edge.rightIndex] ?: 0f) + edge.score
            }
        }

        return cluster.maxWithOrNull(
            compareBy<Int> { summedClusterEdgeScores[it] ?: 0f }
                .thenBy { bestScoreToOtherArticle[it] }
                .thenBy { -it }
        ) ?: cluster.first()
    }

    private fun tightenClusterAroundRepresentative(
        cluster: List<Int>,
        representativeIdx: Int,
        graphEdges: List<ScoredSentenceMatch>,
        minScore: Float
    ): List<Int> {
        if (cluster.size <= 2) return cluster

        val clusterSet = cluster.toSet()
        val directMatches = graphEdges
            .asSequence()
            .filter { edge ->
                edge.score >= minScore && (
                        (edge.leftIndex == representativeIdx && edge.rightIndex in clusterSet) ||
                                (edge.rightIndex == representativeIdx && edge.leftIndex in clusterSet)
                        )
            }
            .map { edge ->
                if (edge.leftIndex == representativeIdx) edge.rightIndex else edge.leftIndex
            }
            .toSet()

        return cluster.filter { it == representativeIdx || it in directMatches }
    }

    private fun limitClusterToOneSentencePerArticle(
        cluster: List<Int>,
        representativeIdx: Int,
        candidates: List<CompareCandidate>,
        graphEdges: List<ScoredSentenceMatch>,
        bestScoreToOtherArticle: FloatArray,
        maxClusterSize: Int
    ): List<Int> {
        if (cluster.size <= 1) return cluster

        val representativeArticleId = candidates[representativeIdx].articleId
        val clusterSet = cluster.toSet()
        val directScoreToRepresentative = buildMap {
            graphEdges.forEach { edge ->
                when {
                    edge.leftIndex == representativeIdx && edge.rightIndex in clusterSet -> {
                        put(edge.rightIndex, edge.score)
                    }
                    edge.rightIndex == representativeIdx && edge.leftIndex in clusterSet -> {
                        put(edge.leftIndex, edge.score)
                    }
                }
            }
        }

        val oneCandidatePerArticle = cluster
            .filter { it != representativeIdx }
            .groupBy { candidates[it].articleId }
            .mapNotNull { (articleId, indices) ->
                if (articleId == representativeArticleId) return@mapNotNull null
                indices.maxWithOrNull(
                    compareBy<Int> { directScoreToRepresentative[it] ?: 0f }
                        .thenBy { bestScoreToOtherArticle[it] }
                        .thenBy { -it }
                )
            }
            .sortedWith(
                compareByDescending<Int> { directScoreToRepresentative[it] ?: 0f }
                    .thenByDescending { bestScoreToOtherArticle[it] }
                    .thenBy { it }
            )

        return buildList {
            add(representativeIdx)
            addAll(oneCandidatePerArticle.take((maxClusterSize - 1).coerceAtLeast(0)))
        }
    }
}
