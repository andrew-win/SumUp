package com.andrewwin.sumup.domain.feed.dedup

import com.andrewwin.sumup.domain.article.model.Article
import com.andrewwin.sumup.domain.article.model.ArticleSimilarityRecord
import com.andrewwin.sumup.domain.feed.model.ArticlePairKey
import com.andrewwin.sumup.domain.article.deduplication.ArticleDedupContentSignatureFactory
import com.andrewwin.sumup.domain.article.deduplication.SimilarityScorer
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
    suspend fun rebuildSimilarities(
        prefs: UserSettings,
        changedArticleIds: List<Long>
    ): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            if (!prefs.isDeduplicationEnabled) {
                return@runCatching
            }
            if (changedArticleIds.isEmpty()) {
                return@runCatching
            }

            val allArticles = articleRepository.getEnabledArticlesOnce().distinctBy { it.id }
            if (allArticles.size < MIN_DEDUP_ARTICLES) {
                return@runCatching
            }

            val changedArticleIdSet = changedArticleIds.toHashSet()
            val changedArticles = allArticles.filter { it.id in changedArticleIdSet }
            if (changedArticles.isEmpty()) {
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
                return@runCatching
            }

            val result = updateSimilarities(
                allArticles = allArticles,
                changedArticles = changedArticles,
                strategy = prefs.deduplicationStrategy,
                threshold = threshold
            )
            if (!result.shouldSaveSimilarities) {
                return@runCatching
            }
        }
    }

    private suspend fun updateSimilarities(
        allArticles: List<Article>,
        changedArticles: List<Article>,
        strategy: DeduplicationStrategy,
        threshold: Float
    ): DedupResult {
        val strategyKey = similarityScorer.similarityCacheKeyForStrategy(strategy)
        val activeArticleIds = allArticles.mapTo(mutableSetOf()) { it.id }
        val changedArticleIds = changedArticles.mapTo(mutableSetOf()) { it.id }
        val embeddingProgress = similarityScorer.getEmbeddingsProgress(allArticles, strategy).first()
        if (embeddingProgress.cloudMissingGenerationFailed) {
            return DedupResult(
                newSimilaritiesAboveThreshold = 0,
                newSimilaritiesTotal = 0,
                shouldSaveSimilarities = false
            )
        }

        val existingPairKeys = articleRepository
            .getSimilaritiesTouchingChangedArticles(
                changedArticleIds = changedArticleIds.toList(),
                activeArticleIds = activeArticleIds.toList(),
                strategyKey = strategyKey
            )
            .associateBy { similarity ->
                ArticlePairKey.of(similarity.leftArticleId, similarity.rightArticleId)
            }
        val contentSignaturesByArticleId = allArticles.associate { article ->
            article.id to ArticleDedupContentSignatureFactory.build(article)
        }
        val validExistingPairKeys = existingPairKeys
            .asSequence()
            .filter { (pairKey, similarity) ->
                pairKey.firstId in activeArticleIds &&
                    pairKey.secondId in activeArticleIds &&
                    similarity.leftContentSignature == contentSignaturesByArticleId[pairKey.firstId] &&
                    similarity.rightContentSignature == contentSignaturesByArticleId[pairKey.secondId]
            }
            .map { it.key }
            .toHashSet()

        val embeddingsById = embeddingProgress.embeddingsById
        val newSimilarities = mutableListOf<ArticleSimilarityRecord>()

        for (left in changedArticles.sortedBy { it.id }) {
            val leftEmbedding = embeddingsById[left.id] ?: continue
            for (right in allArticles) {
                if (left.id == right.id) continue
                val pairKey = ArticlePairKey.of(left.id, right.id)
                if (pairKey in validExistingPairKeys) continue

                val rightEmbedding = embeddingsById[right.id] ?: continue
                val score = similarityScorer.calculateSimilarity(
                    embeddingA = leftEmbedding,
                    embeddingB = rightEmbedding
                )
                val leftContentSignature = contentSignaturesByArticleId[pairKey.firstId].orEmpty()
                val rightContentSignature = contentSignaturesByArticleId[pairKey.secondId].orEmpty()
                newSimilarities += ArticleSimilarityRecord(
                    leftArticleId = pairKey.firstId,
                    rightArticleId = pairKey.secondId,
                    strategyKey = strategyKey,
                    score = score,
                    leftContentSignature = leftContentSignature,
                    rightContentSignature = rightContentSignature
                )
                validExistingPairKeys += pairKey
            }
        }

        articleRepository.upsertSimilarities(newSimilarities)
        return DedupResult(
            newSimilaritiesAboveThreshold = newSimilarities.count { it.score >= threshold },
            newSimilaritiesTotal = newSimilarities.size,
            shouldSaveSimilarities = true
        )
    }

    private data class DedupResult(
        val newSimilaritiesAboveThreshold: Int,
        val newSimilaritiesTotal: Int,
        val shouldSaveSimilarities: Boolean
    )

    private companion object {
        private const val MIN_DEDUP_ARTICLES = 2
    }
}
