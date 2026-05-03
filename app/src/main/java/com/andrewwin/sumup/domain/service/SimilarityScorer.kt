package com.andrewwin.sumup.domain.service

import android.util.Log
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.DeduplicationStrategy
import com.andrewwin.sumup.domain.repository.ArticleRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class SimilarityScorer(
    private val articleRepository: ArticleRepository,
    private val localEmbeddingService: LocalEmbeddingService,
    private val cloudEmbeddingService: CloudEmbeddingService
) {
    private val localEmbeddingSemaphore = Semaphore(LOCAL_EMBEDDING_PARALLELISM)
    private val cloudEmbeddingSemaphore = Semaphore(CLOUD_EMBEDDING_PARALLELISM)

    suspend fun initialize(): Boolean =
        localEmbeddingService.initialize()

    fun close() {
        localEmbeddingService.close()
    }

    suspend fun getEmbedding(article: Article, strategy: DeduplicationStrategy): FloatArray {
        val embeddingType = embeddingTypeForStrategy(strategy)
        // 1. Check if the provided article object already has the correct embedding
        if (article.embeddingType == embeddingType && article.embedding != null) {
            return EmbeddingUtils.toFloatArray(article.embedding)
        }

        // 2. Check Database Cache if the article object didn't have it (might be stale)
        val stored = articleRepository.getArticleEmbeddingsByIds(listOf(article.id)).firstOrNull()
        if (stored?.embedding != null && stored.embeddingType == embeddingType) {
            return EmbeddingUtils.toFloatArray(stored.embedding)
        }

        // 3. Generate based on strategy
        val startMs = System.currentTimeMillis()
        val embedding = when (strategy) {
            DeduplicationStrategy.CLOUD -> {
                val cloud = cloudEmbeddingService.getCloudEmbedding(article.title)
                if (cloud != null) {
                    EmbeddingUtils.normalize(EmbeddingUtils.resizeEmbedding(cloud))
                } else null
            }
            DeduplicationStrategy.LOCAL -> {
                val raw = localEmbeddingService.computeLocalEmbedding(article.title)
                EmbeddingUtils.normalize(raw)
            }
        }
        Log.d(
            EMBEDDINGS_TEST_LOG_TAG,
            "embedding_created strategy=${strategy.name} articleId=${article.id} " +
                "ms=${System.currentTimeMillis() - startMs} title=${article.title}"
        )

        return if (embedding != null) {
            saveEmbedding(article, embedding, embeddingType)
            embedding
        } else {
            emptyEmbeddingForStrategy(strategy)
        }
    }

    private suspend fun saveEmbedding(article: Article, embedding: FloatArray, embeddingType: String) {
        val updated = article.copy(
            embedding = EmbeddingUtils.toByteArray(embedding),
            embeddingType = embeddingType
        )
        articleRepository.updateArticles(listOf(updated))
    }

    fun calculateSimilarity(
        embeddingA: FloatArray,
        embeddingB: FloatArray
    ): Float {
        if (EmbeddingUtils.isZeroVector(embeddingA) || EmbeddingUtils.isZeroVector(embeddingB)) return 0f
        return EmbeddingUtils.dotProduct(embeddingA, embeddingB)
    }

    suspend fun getEmbeddingsParallel(articles: List<Article>, strategy: DeduplicationStrategy): Map<Long, FloatArray> = coroutineScope {
        val embeddingSemaphore = embeddingSemaphoreForStrategy(strategy)
        articles.map { article ->
            async {
                embeddingSemaphore.withPermit {
                    article.id to getEmbedding(article, strategy)
                }
            }
        }.awaitAll().toMap()
    }

    private fun embeddingSemaphoreForStrategy(strategy: DeduplicationStrategy): Semaphore =
        when (strategy) {
            DeduplicationStrategy.CLOUD -> cloudEmbeddingSemaphore
            DeduplicationStrategy.LOCAL -> localEmbeddingSemaphore
        }

    private fun embeddingTypeForStrategy(strategy: DeduplicationStrategy): String =
        when (strategy) {
            DeduplicationStrategy.CLOUD -> strategy.name
            DeduplicationStrategy.LOCAL -> LocalEmbeddingService.EMBEDDING_CACHE_TYPE
        }

    private fun emptyEmbeddingForStrategy(strategy: DeduplicationStrategy): FloatArray =
        when (strategy) {
            DeduplicationStrategy.CLOUD -> FloatArray(EmbeddingUtils.EMBEDDING_DIM)
            DeduplicationStrategy.LOCAL -> FloatArray(EmbeddingUtils.LOCAL_EMBEDDING_DIM)
        }

    private companion object {
        private const val LOCAL_EMBEDDING_PARALLELISM = 1
        private const val CLOUD_EMBEDDING_PARALLELISM = 4
        private const val EMBEDDINGS_TEST_LOG_TAG = "EmbedingsTest"
    }
}
