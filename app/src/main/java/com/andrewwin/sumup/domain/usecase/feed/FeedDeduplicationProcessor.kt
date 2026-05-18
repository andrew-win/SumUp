package com.andrewwin.sumup.domain.usecase.feed

import android.util.Log
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.ArticleSimilarity
import com.andrewwin.sumup.data.local.entities.DeduplicationStrategy
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.news.ArticleCluster
import com.andrewwin.sumup.domain.news.SimilarityScorer
import com.andrewwin.sumup.domain.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FeedDeduplicationProcessor @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val similarityScorer: SimilarityScorer
) {
    suspend fun rebuildSimilarities(prefs: UserPreferences): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            if (!prefs.isDeduplicationEnabled) {
                Log.d(FEED_STATES_LOG_TAG, "dedup_skipped_disabled")
                return@runCatching
            }

            val articles = articleRepository.getEnabledArticlesOnce()
            if (articles.size < MIN_DEDUP_ARTICLES) {
                Log.d(FEED_STATES_LOG_TAG, "dedup_skipped_not_enough_articles articles=${articles.size}")
                return@runCatching
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
                Log.d(FEED_STATES_LOG_TAG, "dedup_skipped_model_not_initialized articles=${articles.size}")
                return@runCatching
            }

            Log.d(
                FEED_STATES_LOG_TAG,
                "dedup_start articles=${articles.size} strategy=${prefs.deduplicationStrategy.name} threshold=$threshold"
            )
            val result = buildClusters(
                articles = articles,
                strategy = prefs.deduplicationStrategy,
                threshold = threshold
            )
            if (!result.shouldSaveSimilarities) {
                Log.d(FEED_STATES_LOG_TAG, "dedup_result_not_saved reason=cloud_missing_generation_failed")
                return@runCatching
            }

            Log.d(
                FEED_STATES_LOG_TAG,
                "dedup_complete articles=${articles.size} clusters=${result.clusters.size} " +
                    "newSimilarities=${result.newSimilaritiesCount}"
            )
        }
    }

    private suspend fun buildClusters(
        articles: List<Article>,
        strategy: DeduplicationStrategy,
        threshold: Float
    ): DedupResult {
        val allArticles = articles.distinctBy { it.id }
        val embeddingProgress = similarityScorer.getEmbeddingsProgress(allArticles, strategy).first()
        val embeddingsById = embeddingProgress.embeddingsById
        val strategyKey = similarityScorer.similarityCacheKeyForStrategy(strategy)
        val currentArticleIds = allArticles.mapTo(mutableSetOf()) { it.id }
        val pairScores = articleRepository
            .getSimilaritiesForArticles(allArticles.map { it.id }, strategyKey)
            .asSequence()
            .filter { it.leftArticleId in currentArticleIds && it.rightArticleId in currentArticleIds }
            .associate { similarity ->
                ArticlePairKey.of(similarity.leftArticleId, similarity.rightArticleId) to similarity.score
            }
            .toMutableMap()
        val newSimilarities = mutableListOf<ArticleSimilarity>()

        if (embeddingProgress.cloudMissingGenerationFailed) {
            return DedupResult(
                clusters = FeedClusterCalculator.buildFinalClusters(
                    articles = allArticles,
                    pairScores = pairScores.filterValues { it >= threshold }
                ),
                newSimilaritiesCount = 0,
                shouldSaveSimilarities = false
            )
        }

        val expectedPairCount = allArticles.size.toLong() * (allArticles.size - 1L) / 2L
        if (pairScores.size.toLong() >= expectedPairCount) {
            return DedupResult(
                clusters = FeedClusterCalculator.buildFinalClusters(
                    articles = allArticles,
                    pairScores = pairScores.filterValues { it >= threshold }
                ),
                newSimilaritiesCount = 0,
                shouldSaveSimilarities = true
            )
        }

        for (i in 0 until allArticles.lastIndex) {
            val left = allArticles[i]
            val leftEmbedding = embeddingsById[left.id] ?: continue

            for (j in i + 1 until allArticles.size) {
                val right = allArticles[j]
                val rightEmbedding = embeddingsById[right.id] ?: continue
                val pairKey = ArticlePairKey.of(left.id, right.id)
                if (pairScores.containsKey(pairKey)) continue

                val score = similarityScorer.calculateSimilarity(
                    embeddingA = leftEmbedding,
                    embeddingB = rightEmbedding
                )
                pairScores[pairKey] = score
                newSimilarities += ArticleSimilarity(
                    leftArticleId = pairKey.firstId,
                    rightArticleId = pairKey.secondId,
                    strategyKey = strategyKey,
                    score = score
                )
            }
        }
        articleRepository.upsertSimilarities(newSimilarities)

        return DedupResult(
            clusters = FeedClusterCalculator.buildFinalClusters(
                articles = allArticles,
                pairScores = pairScores.filterValues { it >= threshold }
            ),
            newSimilaritiesCount = newSimilarities.size,
            shouldSaveSimilarities = true
        )
    }

    private data class DedupResult(
        val clusters: List<ArticleCluster>,
        val newSimilaritiesCount: Int,
        val shouldSaveSimilarities: Boolean
    )

    private companion object {
        private const val FEED_STATES_LOG_TAG = "FeedStatesDebug"
        private const val MIN_DEDUP_ARTICLES = 2
    }
}
