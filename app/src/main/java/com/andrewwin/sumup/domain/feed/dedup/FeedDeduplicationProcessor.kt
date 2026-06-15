package com.andrewwin.sumup.domain.feed.dedup

import com.andrewwin.sumup.domain.article.deduplication.ArticleDedupContentSignatureFactory
import com.andrewwin.sumup.domain.article.deduplication.SimilarityScorer
import com.andrewwin.sumup.domain.article.model.Article
import com.andrewwin.sumup.domain.article.model.ArticleSimilarityRecord
import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import com.andrewwin.sumup.domain.settings.model.DeduplicationStrategy
import com.andrewwin.sumup.domain.settings.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FeedDeduplicationProcessor @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val similarityScorer: SimilarityScorer
) {
    suspend fun ensureSimilaritiesForArticles(
        prefs: UserSettings,
        articleIds: List<Long>
    ): Result<DeduplicationRebuildResult> = withContext(Dispatchers.Default) {
        runCatching {
            if (!prefs.isDeduplicationEnabled || articleIds.isEmpty()) {
                return@runCatching DeduplicationRebuildResult()
            }

            val allArticles = articleRepository.getEnabledArticlesOnce().distinctBy { it.id }
            if (allArticles.size < MIN_DEDUP_ARTICLES) {
                return@runCatching DeduplicationRebuildResult()
            }

            val targetArticleIds = articleIds
                .distinct()
                .filter { articleId -> allArticles.any { it.id == articleId } }
                .toSet()
            if (targetArticleIds.isEmpty()) {
                return@runCatching DeduplicationRebuildResult()
            }

            val threshold = when (prefs.deduplicationStrategy) {
                DeduplicationStrategy.LOCAL -> prefs.localDeduplicationThreshold
                DeduplicationStrategy.CLOUD -> prefs.cloudDeduplicationThreshold
            }
            val isModelInitialized = when (prefs.deduplicationStrategy) {
                DeduplicationStrategy.LOCAL -> similarityScorer.initialize()
                DeduplicationStrategy.CLOUD -> true
            }
            if (!isModelInitialized) {
                return@runCatching DeduplicationRebuildResult()
            }

            val result = updateSimilarities(
                allArticles = allArticles,
                targetArticleIds = targetArticleIds,
                strategy = prefs.deduplicationStrategy,
                threshold = threshold
            )
            DeduplicationRebuildResult(
                cloudEmbeddingsIncomplete = result.cloudEmbeddingsIncomplete
            )
        }
    }

    suspend fun rebuildSimilarities(prefs: UserSettings): Result<DeduplicationRebuildResult> = withContext(Dispatchers.Default) {
        runCatching {
            if (!prefs.isDeduplicationEnabled) {
                return@runCatching DeduplicationRebuildResult()
            }

            val allArticles = articleRepository.getEnabledArticlesOnce().distinctBy { it.id }
            if (allArticles.size < MIN_DEDUP_ARTICLES) {
                return@runCatching DeduplicationRebuildResult()
            }

            val threshold = when (prefs.deduplicationStrategy) {
                DeduplicationStrategy.LOCAL -> prefs.localDeduplicationThreshold
                DeduplicationStrategy.CLOUD -> prefs.cloudDeduplicationThreshold
            }
            val isModelInitialized = when (prefs.deduplicationStrategy) {
                DeduplicationStrategy.LOCAL -> similarityScorer.initialize()
                DeduplicationStrategy.CLOUD -> true
            }
            if (!isModelInitialized) {
                return@runCatching DeduplicationRebuildResult()
            }

            val result = updateSimilarities(
                allArticles = allArticles,
                targetArticleIds = null,
                strategy = prefs.deduplicationStrategy,
                threshold = threshold
            )
            DeduplicationRebuildResult(
                cloudEmbeddingsIncomplete = result.cloudEmbeddingsIncomplete
            )
        }
    }

    private suspend fun updateSimilarities(
        allArticles: List<Article>,
        targetArticleIds: Set<Long>?,
        strategy: DeduplicationStrategy,
        threshold: Float
    ): DedupResult {
        val strategyKey = similarityScorer.thresholdSimilarityCacheKey(strategy, threshold)
        val embeddingProgress = similarityScorer.getEmbeddingsProgress(allArticles, strategy).first()

        val contentSignaturesByArticleId = allArticles.associate { article ->
            article.id to ArticleDedupContentSignatureFactory.build(article)
        }

        val embeddingsById = embeddingProgress.embeddingsById
        val newSimilarities = mutableListOf<ArticleSimilarityRecord>()

        val orderedArticles = allArticles.sortedBy { it.id }
        for (leftIndex in orderedArticles.indices) {
            val left = orderedArticles[leftIndex]
            val leftEmbedding = embeddingsById[left.id] ?: continue
            for (rightIndex in leftIndex + 1 until orderedArticles.size) {
                val right = orderedArticles[rightIndex]
                if (targetArticleIds != null && left.id !in targetArticleIds && right.id !in targetArticleIds) continue
                val rightEmbedding = embeddingsById[right.id] ?: continue
                val score = similarityScorer.calculateSimilarity(
                    embeddingA = leftEmbedding,
                    embeddingB = rightEmbedding
                )
                if (score < threshold) continue
                val leftContentSignature = contentSignaturesByArticleId[left.id].orEmpty()
                val rightContentSignature = contentSignaturesByArticleId[right.id].orEmpty()
                newSimilarities += ArticleSimilarityRecord(
                    leftArticleId = left.id,
                    rightArticleId = right.id,
                    strategyKey = strategyKey,
                    score = score,
                    leftContentSignature = leftContentSignature,
                    rightContentSignature = rightContentSignature
                )
            }
        }

        articleRepository.upsertSimilarities(newSimilarities)
        return DedupResult(
            newSimilaritiesAboveThreshold = newSimilarities.count { it.score >= threshold },
            newSimilaritiesTotal = newSimilarities.size,
            cloudEmbeddingsIncomplete = embeddingProgress.cloudMissingGenerationFailed
        )
    }

    private data class DedupResult(
        val newSimilaritiesAboveThreshold: Int,
        val newSimilaritiesTotal: Int,
        val cloudEmbeddingsIncomplete: Boolean = false
    )

    private companion object {
        private const val MIN_DEDUP_ARTICLES = 2
    }
}
