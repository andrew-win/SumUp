package com.andrewwin.sumup.domain.feed.dedup

import android.util.Log
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
            val totalStartedAt = System.currentTimeMillis()
            if (!prefs.isDeduplicationEnabled || articleIds.isEmpty()) {
                return@runCatching DeduplicationRebuildResult()
            }

            val allArticles = articleRepository.getEnabledArticlesOnce().distinctBy { it.id }
            if (allArticles.size < MIN_DEDUP_ARTICLES) {
                logDedupProfile(
                    "ensure_similarities_skipped reason=not_enough_articles " +
                        "durationMs=${System.currentTimeMillis() - totalStartedAt} articles=${allArticles.size}"
                )
                return@runCatching DeduplicationRebuildResult()
            }

            val targetArticleIds = articleIds
                .distinct()
                .filter { articleId -> allArticles.any { it.id == articleId } }
                .toSet()
            if (targetArticleIds.isEmpty()) {
                logDedupProfile(
                    "ensure_similarities_skipped reason=no_enabled_target_articles " +
                        "durationMs=${System.currentTimeMillis() - totalStartedAt} requested=${articleIds.size}"
                )
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
                logDedupProfile(
                    "ensure_similarities_skipped reason=model_not_initialized " +
                        "durationMs=${System.currentTimeMillis() - totalStartedAt}"
                )
                return@runCatching DeduplicationRebuildResult()
            }

            val result = updateSimilaritiesForArticles(
                allArticles = allArticles,
                targetArticleIds = targetArticleIds,
                strategy = prefs.deduplicationStrategy,
                threshold = threshold
            )
            logDedupProfile(
                "ensure_similarities_complete durationMs=${System.currentTimeMillis() - totalStartedAt} " +
                    "allArticles=${allArticles.size} targetArticles=${targetArticleIds.size} " +
                    "strategy=${prefs.deduplicationStrategy} threshold=$threshold " +
                    "newSimilarities=${result.newSimilaritiesTotal} aboveThreshold=${result.newSimilaritiesAboveThreshold}"
            )
            DeduplicationRebuildResult(
                cloudEmbeddingsIncomplete = result.cloudEmbeddingsIncomplete
            )
        }
    }

    suspend fun rebuildSimilarities(prefs: UserSettings): Result<DeduplicationRebuildResult> = withContext(Dispatchers.Default) {
        runCatching {
            val totalStartedAt = System.currentTimeMillis()
            if (!prefs.isDeduplicationEnabled) {
                return@runCatching DeduplicationRebuildResult()
            }

            val loadArticlesStartedAt = System.currentTimeMillis()
            val allArticles = articleRepository.getEnabledArticlesOnce().distinctBy { it.id }
            val loadArticlesDurationMs = System.currentTimeMillis() - loadArticlesStartedAt
            if (allArticles.size < MIN_DEDUP_ARTICLES) {
                logDedupProfile(
                    "rebuild_skipped reason=not_enough_articles durationMs=${System.currentTimeMillis() - totalStartedAt} articles=${allArticles.size}"
                )
                return@runCatching DeduplicationRebuildResult()
            }

            val threshold = when (prefs.deduplicationStrategy) {
                DeduplicationStrategy.LOCAL -> prefs.localDeduplicationThreshold
                DeduplicationStrategy.CLOUD -> prefs.cloudDeduplicationThreshold
            }
            val initializeStartedAt = System.currentTimeMillis()
            val isModelInitialized = when (prefs.deduplicationStrategy) {
                DeduplicationStrategy.LOCAL -> similarityScorer.initialize()
                DeduplicationStrategy.CLOUD -> true
            }
            val initializeDurationMs = System.currentTimeMillis() - initializeStartedAt
            if (!isModelInitialized) {
                logDedupProfile(
                    "rebuild_skipped reason=model_not_initialized durationMs=${System.currentTimeMillis() - totalStartedAt}"
                )
                return@runCatching DeduplicationRebuildResult()
            }

            val updateStartedAt = System.currentTimeMillis()
            val result = updateSimilarities(
                allArticles = allArticles,
                strategy = prefs.deduplicationStrategy,
                threshold = threshold
            )
            val updateDurationMs = System.currentTimeMillis() - updateStartedAt
            logDedupProfile(
                "rebuild_complete durationMs=${System.currentTimeMillis() - totalStartedAt} " +
                    "loadArticlesMs=$loadArticlesDurationMs initializeMs=$initializeDurationMs " +
                    "updateMs=$updateDurationMs allArticles=${allArticles.size} " +
                    "strategy=${prefs.deduplicationStrategy} threshold=$threshold " +
                    "newSimilarities=${result.newSimilaritiesTotal} aboveThreshold=${result.newSimilaritiesAboveThreshold}"
            )
            DeduplicationRebuildResult(
                cloudEmbeddingsIncomplete = result.cloudEmbeddingsIncomplete
            )
        }
    }

    private suspend fun updateSimilaritiesForArticles(
        allArticles: List<Article>,
        targetArticleIds: Set<Long>,
        strategy: DeduplicationStrategy,
        threshold: Float
    ): DedupResult {
        val strategyKey = similarityScorer.thresholdSimilarityCacheKey(strategy, threshold)
        val embeddingsStartedAt = System.currentTimeMillis()
        val embeddingProgress = similarityScorer.getEmbeddingsProgress(allArticles, strategy).first()
        val embeddingsDurationMs = System.currentTimeMillis() - embeddingsStartedAt
        if (embeddingProgress.cloudMissingGenerationFailed) {
            logDedupProfile(
                "ensure_similarities_partial stage=embeddings durationMs=$embeddingsDurationMs " +
                    "allArticles=${allArticles.size} targetArticles=${targetArticleIds.size}"
            )
        }

        val contentSignaturesStartedAt = System.currentTimeMillis()
        val contentSignaturesByArticleId = allArticles.associate { article ->
            article.id to ArticleDedupContentSignatureFactory.build(article)
        }
        val contentSignaturesDurationMs = System.currentTimeMillis() - contentSignaturesStartedAt

        val embeddingsById = embeddingProgress.embeddingsById
        val newSimilarities = mutableListOf<ArticleSimilarityRecord>()

        val pairLoopStartedAt = System.currentTimeMillis()
        val orderedArticles = allArticles.sortedBy { it.id }
        for (leftIndex in orderedArticles.indices) {
            val left = orderedArticles[leftIndex]
            val leftEmbedding = embeddingsById[left.id] ?: continue
            for (rightIndex in leftIndex + 1 until orderedArticles.size) {
                val right = orderedArticles[rightIndex]
                if (left.id !in targetArticleIds && right.id !in targetArticleIds) continue
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
        val pairLoopDurationMs = System.currentTimeMillis() - pairLoopStartedAt

        val upsertStartedAt = System.currentTimeMillis()
        articleRepository.upsertSimilarities(newSimilarities)
        val upsertDurationMs = System.currentTimeMillis() - upsertStartedAt
        logDedupProfile(
            "ensure_similarities_steps embeddingsMs=$embeddingsDurationMs " +
                "contentSignaturesMs=$contentSignaturesDurationMs pairLoopMs=$pairLoopDurationMs " +
                "upsertMs=$upsertDurationMs activeArticles=${allArticles.size} " +
                "targetArticles=${targetArticleIds.size} generatedPairs=${newSimilarities.size} " +
                "threshold=$threshold strategyKey=$strategyKey"
        )
        return DedupResult(
            newSimilaritiesAboveThreshold = newSimilarities.count { it.score >= threshold },
            newSimilaritiesTotal = newSimilarities.size,
            cloudEmbeddingsIncomplete = embeddingProgress.cloudMissingGenerationFailed
        )
    }

    private suspend fun updateSimilarities(
        allArticles: List<Article>,
        strategy: DeduplicationStrategy,
        threshold: Float
    ): DedupResult {
        val strategyKey = similarityScorer.thresholdSimilarityCacheKey(strategy, threshold)
        val embeddingsStartedAt = System.currentTimeMillis()
        val embeddingProgress = similarityScorer.getEmbeddingsProgress(allArticles, strategy).first()
        val embeddingsDurationMs = System.currentTimeMillis() - embeddingsStartedAt
        if (embeddingProgress.cloudMissingGenerationFailed) {
            logDedupProfile(
                "rebuild_partial stage=embeddings durationMs=$embeddingsDurationMs allArticles=${allArticles.size}"
            )
        }

        val contentSignaturesStartedAt = System.currentTimeMillis()
        val contentSignaturesByArticleId = allArticles.associate { article ->
            article.id to ArticleDedupContentSignatureFactory.build(article)
        }
        val contentSignaturesDurationMs = System.currentTimeMillis() - contentSignaturesStartedAt

        val embeddingsById = embeddingProgress.embeddingsById
        val newSimilarities = mutableListOf<ArticleSimilarityRecord>()

        val pairLoopStartedAt = System.currentTimeMillis()
        val orderedArticles = allArticles.sortedBy { it.id }
        for (leftIndex in orderedArticles.indices) {
            val left = orderedArticles[leftIndex]
            val leftEmbedding = embeddingsById[left.id] ?: continue
            for (rightIndex in leftIndex + 1 until orderedArticles.size) {
                val right = orderedArticles[rightIndex]
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
        val pairLoopDurationMs = System.currentTimeMillis() - pairLoopStartedAt

        val upsertStartedAt = System.currentTimeMillis()
        articleRepository.upsertSimilarities(newSimilarities)
        val upsertDurationMs = System.currentTimeMillis() - upsertStartedAt
        logDedupProfile(
            "rebuild_steps embeddingsMs=$embeddingsDurationMs " +
                "contentSignaturesMs=$contentSignaturesDurationMs pairLoopMs=$pairLoopDurationMs " +
                "upsertMs=$upsertDurationMs activeArticles=${allArticles.size} " +
                "generatedPairs=${newSimilarities.size} threshold=$threshold strategyKey=$strategyKey"
        )
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

    private fun logDedupProfile(message: String) {
        Log.d(FEED_DEDUP_PROFILE_LOG_TAG, message)
    }

    private companion object {
        private const val MIN_DEDUP_ARTICLES = 2
        private const val FEED_DEDUP_PROFILE_LOG_TAG = "FeedDedupProfile"
    }
}
