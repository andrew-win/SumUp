package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.DeduplicationStrategy
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.service.EmbeddingUtils
import com.andrewwin.sumup.domain.service.SimilarityScorer
import com.andrewwin.sumup.domain.support.DispatcherProvider
import com.andrewwin.sumup.domain.support.LocalModelMissingException
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject


class CompareNewsUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val articleRepository: ArticleRepository,
    private val shrinkTextForAdaptiveStrategyUseCase: ShrinkTextForAdaptiveStrategyUseCase,
    private val sendCloudAiRequestUseCase: SendCloudAiRequestUseCase,
    private val parseAiJsonResponseUseCase: ParseAiJsonResponseUseCase,
    private val generateLocalEmbeddingUseCase: GenerateLocalEmbeddingUseCase,
    private val getExtractiveSummaryUseCase: GetExtractiveSummaryUseCase,
    private val similarityScorer: SimilarityScorer,
    private val manageModelUseCase: ManageModelUseCase,
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

        val prompt = AiPromptBuilder.buildComparePrompt(prefs.summaryLanguage)

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

    private data class ScoredSentenceMatch(
        val leftIndex: Int,
        val rightIndex: Int,
        val score: Float
    )

    private suspend fun performLocalComparison(articles: List<Article>, prefs: com.andrewwin.sumup.data.local.entities.UserPreferences): SummaryResult.Compare {
        if (articles.isEmpty()) return SummaryResult.Compare(emptyList(), emptyList())

        if (!manageModelUseCase.isModelExists()) {
            throw LocalModelMissingException()
        }

        val candidates = mutableListOf<CompareCandidate>()
        
        for (article in articles) {
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceName = source?.name?.trim()?.ifBlank { "Джерело" } ?: "Джерело"
            val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
            val sourceMeta = SummarySourceRef(name = sourceName, url = sourceUrl)
            
            val fullContent = articleRepository.fetchFullContent(article)
            // Збільшуємо кількість речень для аналізу, щоб знайти унікальні деталі
            val sentences = getExtractiveSummaryUseCase(fullContent, SummaryLimits.Compare.sentencesLocalCompare)
            val prepared = sentences.map { it.trim() }.filter { it.isNotBlank() }
            prepared.forEach { sentence ->
                candidates += CompareCandidate(text = sentence, source = sourceMeta, articleId = article.id)
            }
        }

        if (candidates.isEmpty()) return SummaryResult.Compare(emptyList(), emptyList())

        val embeddings = candidates.map { candidate ->
            generateLocalEmbeddingUseCase(candidate.text) ?: FloatArray(EmbeddingUtils.EMBEDDING_DIM)
        }
        val featuresCache = candidates.map { EmbeddingUtils.extractTextFeatures(it.text) }

        // Попередньо розраховуємо найкращий бал схожості з іншими статтями для кожного речення
        val bestScoreToOtherArticle = FloatArray(candidates.size) { 0f }
        val threshold = SummaryLimits.Compare.localSimilarityThreshold
        val graphEdges = mutableListOf<ScoredSentenceMatch>()

        for (candidateIdx in candidates.indices) {
            val bestMatchesByArticle = mutableMapOf<Long, ScoredSentenceMatch>()

            for (otherIdx in candidates.indices) {
                if (candidateIdx == otherIdx) continue

                val candidateArticleId = candidates[candidateIdx].articleId
                val otherArticleId = candidates[otherIdx].articleId
                if (candidateArticleId == otherArticleId) continue

                val score = calculateCandidateSimilarity(
                    leftIndex = candidateIdx,
                    rightIndex = otherIdx,
                    embeddings = embeddings,
                    featuresCache = featuresCache,
                    bestScoreToOtherArticle = bestScoreToOtherArticle
                )

                if (score < threshold) continue

                val currentBest = bestMatchesByArticle[otherArticleId]
                if (currentBest == null || score > currentBest.score) {
                    bestMatchesByArticle[otherArticleId] = ScoredSentenceMatch(
                        leftIndex = candidateIdx,
                        rightIndex = otherIdx,
                        score = score
                    )
                }
            }

            graphEdges += bestMatchesByArticle.values
        }

        val clusters = buildBestMatchClusters(candidates.size, graphEdges)
            .ifEmpty { candidates.indices.map { listOf(it) } }

        val commonCandidates = mutableListOf<Pair<SummaryItem, Float>>()
        val uniqueCandidates = mutableListOf<Pair<SummaryItem, Float>>()

        for (cluster in clusters) {
            val initialRepresentativeIdx = selectRepresentativeCandidateIndex(cluster, graphEdges, bestScoreToOtherArticle)
            val tightenedCluster = tightenClusterAroundRepresentative(
                cluster = cluster,
                representativeIdx = initialRepresentativeIdx,
                graphEdges = graphEdges,
                minScore = threshold
            )
            val representativeIdx = selectRepresentativeCandidateIndex(
                cluster = tightenedCluster,
                graphEdges = graphEdges,
                bestScoreToOtherArticle = bestScoreToOtherArticle
            )
            val clusterSources = tightenedCluster.map { candidates[it].source }.distinctBy { it.url }
            val distinctArticlesInCluster = tightenedCluster.map { candidates[it].articleId }.distinct().size
            
            val item = SummaryItem(text = candidates[representativeIdx].text.trim(), sources = clusterSources)
            val clusterMaxScore = tightenedCluster.maxOf { bestScoreToOtherArticle[it] }

            if (distinctArticlesInCluster > 1) {
                commonCandidates.add(item to clusterMaxScore)
            } else {
                uniqueCandidates.add(item to clusterMaxScore)
            }
        }

        // Спільне: Топ за найбільшими балами схожості
        val commonItems = commonCandidates
            .sortedByDescending { it.second }
            .map { it.first }
            .take(SummaryLimits.Compare.maxCommon)

        // Унікальне: Топ за найменшими балами схожості (найбільш відмінні від інших статей речення)
        val differentItems = uniqueCandidates
            .sortedBy { it.second }
            .map { it.first }
            .take(SummaryLimits.Compare.maxUnique)

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

    private fun calculateCandidateSimilarity(
        leftIndex: Int,
        rightIndex: Int,
        embeddings: List<FloatArray>,
        featuresCache: List<com.andrewwin.sumup.domain.service.TextOptimizationFeatures>,
        bestScoreToOtherArticle: FloatArray
    ): Float {
        val score = similarityScorer.calculateSimilarity(
            articleA = Article(sourceId = 0, title = "", content = "", url = "", publishedAt = 0),
            embeddingA = embeddings[leftIndex],
            articleB = Article(sourceId = 0, title = "", content = "", url = "", publishedAt = 0),
            embeddingB = embeddings[rightIndex],
            strategy = DeduplicationStrategy.LOCAL,
            featuresA = featuresCache[leftIndex],
            featuresB = featuresCache[rightIndex]
        )

        bestScoreToOtherArticle[leftIndex] = maxOf(bestScoreToOtherArticle[leftIndex], score)
        bestScoreToOtherArticle[rightIndex] = maxOf(bestScoreToOtherArticle[rightIndex], score)
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

    private fun normalizeKey(value: String): String = value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

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
