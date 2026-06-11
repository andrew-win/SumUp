package com.andrewwin.sumup.domain.feed.dedup

import android.util.Log
import com.andrewwin.sumup.domain.article.deduplication.ArticleDedupContentSignatureFactory
import com.andrewwin.sumup.domain.article.deduplication.SimilarityScorer
import com.andrewwin.sumup.domain.article.model.Article
import com.andrewwin.sumup.domain.article.model.ArticleSimilarityRecord
import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import com.andrewwin.sumup.domain.feed.model.ArticlePairKey
import com.andrewwin.sumup.domain.feed.model.ArticlePairScore
import com.andrewwin.sumup.domain.feed.model.toPairScoreMap
import com.andrewwin.sumup.domain.settings.model.DeduplicationStrategy
import com.andrewwin.sumup.domain.settings.model.UserSettings
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ThresholdSimilarityResolver @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val similarityScorer: SimilarityScorer
) {
    suspend fun resolvePairScores(
        articles: List<Article>,
        prefs: UserSettings,
        persistComputed: Boolean,
        allowOnDemandComputation: Boolean
    ): Map<ArticlePairKey, Float> {
        return resolveOrderedPairScores(
            articles = articles,
            prefs = prefs,
            persistComputed = persistComputed,
            allowOnDemandComputation = allowOnDemandComputation
        ).toPairScoreMap()
    }

    suspend fun resolveOrderedPairScores(
        articles: List<Article>,
        prefs: UserSettings,
        persistComputed: Boolean,
        allowOnDemandComputation: Boolean
    ): List<ArticlePairScore> {
        if (!prefs.isDeduplicationEnabled || articles.size < MIN_ARTICLES_FOR_SIMILARITY) {
            return emptyList()
        }
        val threshold = prefs.deduplicationThreshold()
        val strategy = prefs.deduplicationStrategy
        val strategyKey = similarityScorer.thresholdSimilarityCacheKey(strategy, threshold)
        val articleIds = articles.map { it.id }.distinct()
        val cached = articleRepository.getSimilarityScoresInsideArticleSetAboveThreshold(
            articleIds = articleIds,
            strategyKey = strategyKey,
            threshold = threshold
        )
        if (cached.isNotEmpty()) {
            return cached
        }
        if (!allowOnDemandComputation) {
            Log.d(
                THRESHOLD_SIMILARITY_LOG_TAG,
                "cache_miss_skip_compute strategyKey=$strategyKey articles=${articleIds.size}"
            )
            return emptyList()
        }

        val computed = computeSimilaritiesAboveThreshold(
            articles = articles,
            strategy = strategy,
            threshold = threshold,
            strategyKey = strategyKey
        )
        if (persistComputed && computed.isNotEmpty()) {
            articleRepository.upsertSimilarities(computed)
        }
        return computed
            .asSequence()
            .sortedByDescending { it.score }
            .map { similarity ->
                ArticlePairScore(
                    leftArticleId = similarity.leftArticleId,
                    rightArticleId = similarity.rightArticleId,
                    score = similarity.score
                )
            }
            .toList()
    }

    suspend fun resolveSimilarityCounts(
        articles: List<Article>,
        prefs: UserSettings,
        persistComputed: Boolean,
        allowOnDemandComputation: Boolean
    ): Map<Long, Int> {
        val pairScores = resolvePairScores(
            articles = articles,
            prefs = prefs,
            persistComputed = persistComputed,
            allowOnDemandComputation = allowOnDemandComputation
        )
        if (pairScores.isEmpty()) return emptyMap()
        val relatedArticleIds = mutableMapOf<Long, MutableSet<Long>>()
        pairScores
            .filterValues { it >= prefs.deduplicationThreshold() }
            .keys
            .forEach { pair ->
            relatedArticleIds.getOrPut(pair.firstId) { mutableSetOf() }.add(pair.secondId)
            relatedArticleIds.getOrPut(pair.secondId) { mutableSetOf() }.add(pair.firstId)
        }
        return relatedArticleIds.mapValues { it.value.size }
    }

    private suspend fun computeSimilaritiesAboveThreshold(
        articles: List<Article>,
        strategy: DeduplicationStrategy,
        threshold: Float,
        strategyKey: String
    ): List<ArticleSimilarityRecord> {
        if (strategy == DeduplicationStrategy.LOCAL && !similarityScorer.initialize()) {
            return emptyList()
        }
        val embeddingsProgress = similarityScorer.getEmbeddingsProgress(articles, strategy).first()
        if (embeddingsProgress.cloudMissingGenerationFailed) {
            return emptyList()
        }
        val embeddingsById = embeddingsProgress.embeddingsById
        val contentSignaturesByArticleId = articles.associate { article ->
            article.id to ArticleDedupContentSignatureFactory.build(article)
        }
        val result = mutableListOf<ArticleSimilarityRecord>()
        val orderedArticles = articles.sortedBy { it.id }
        for (leftIndex in orderedArticles.indices) {
            val leftArticle = orderedArticles[leftIndex]
            val leftEmbedding = embeddingsById[leftArticle.id] ?: continue
            for (rightIndex in leftIndex + 1 until orderedArticles.size) {
                val rightArticle = orderedArticles[rightIndex]
                val rightEmbedding = embeddingsById[rightArticle.id] ?: continue
                val score = similarityScorer.calculateSimilarity(leftEmbedding, rightEmbedding)
                if (score < threshold) continue
                result += ArticleSimilarityRecord(
                    leftArticleId = leftArticle.id,
                    rightArticleId = rightArticle.id,
                    strategyKey = strategyKey,
                    score = score,
                    leftContentSignature = contentSignaturesByArticleId[leftArticle.id].orEmpty(),
                    rightContentSignature = contentSignaturesByArticleId[rightArticle.id].orEmpty()
                )
            }
        }
        return result
    }

    private companion object {
        private const val MIN_ARTICLES_FOR_SIMILARITY = 2
        private const val THRESHOLD_SIMILARITY_LOG_TAG = "ThresholdSimilarity"
    }
}

private fun UserSettings.deduplicationThreshold(): Float =
    when (deduplicationStrategy) {
        DeduplicationStrategy.LOCAL -> localDeduplicationThreshold
        DeduplicationStrategy.CLOUD -> cloudDeduplicationThreshold
    }
