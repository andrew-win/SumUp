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
    private val cloudEmbeddingService: CloudEmbeddingService,
    private val optimizer: LocalEmbeddingOptimizer
) {
    private val semaphore = Semaphore(6)

    suspend fun initialize(modelPath: String): Boolean =
        localEmbeddingService.initialize(modelPath)

    fun close() {
        localEmbeddingService.close()
    }

    suspend fun getEmbedding(article: Article, strategy: DeduplicationStrategy): FloatArray {
        Log.d("SimilarityScorer", "getEmbedding: articleId=${article.id}, strategy=$strategy, currentType=${article.embeddingType}")
        
        // 1. Check if the provided article object already has the correct embedding
        if (article.embeddingType == strategy.name && article.embedding != null) {
            return EmbeddingUtils.toFloatArray(article.embedding)
        }

        // 2. Check Database Cache if the article object didn't have it (might be stale)
        val stored = articleRepository.getArticleEmbeddingsByIds(listOf(article.id)).firstOrNull()
        if (stored?.embedding != null && stored.embeddingType == strategy.name) {
            return EmbeddingUtils.toFloatArray(stored.embedding)
        }

        // 3. Generate based on strategy
        Log.d("SimilarityScorer", "Generating embedding for ${article.id} using $strategy")
        val embedding = when (strategy) {
            DeduplicationStrategy.CLOUD -> {
                val cloud = cloudEmbeddingService.getCloudEmbedding(article.title)
                if (cloud != null) {
                    EmbeddingUtils.normalize(EmbeddingUtils.resizeEmbedding(cloud))
                } else null
            }
            DeduplicationStrategy.LOCAL -> {
                val raw = localEmbeddingService.computeLocalEmbedding(article.title)
                EmbeddingUtils.normalize(EmbeddingUtils.resizeEmbedding(raw))
            }
        }

        return if (embedding != null) {
            Log.d("SimilarityScorer", "Generated embedding for ${article.id}, saving...")
            saveEmbedding(article, embedding, strategy)
            embedding
        } else {
            Log.w("SimilarityScorer", "Failed to generate embedding for ${article.id}")
            FloatArray(EmbeddingUtils.EMBEDDING_DIM)
        }
    }

    private suspend fun saveEmbedding(article: Article, embedding: FloatArray, strategy: DeduplicationStrategy) {
        Log.d("SimilarityScorer", "saveEmbedding: updating article ${article.id}")
        val updated = article.copy(
            embedding = EmbeddingUtils.toByteArray(embedding),
            embeddingType = strategy.name
        )
        articleRepository.updateArticles(listOf(updated))
    }

    fun calculateSimilarity(
        articleA: Article, 
        embeddingA: FloatArray, 
        articleB: Article, 
        embeddingB: FloatArray,
        strategy: DeduplicationStrategy,
        featuresA: TextOptimizationFeatures?,
        featuresB: TextOptimizationFeatures?
    ): Float {
        if (EmbeddingUtils.isZeroVector(embeddingA) || EmbeddingUtils.isZeroVector(embeddingB)) return 0f
        val rawScore = EmbeddingUtils.dotProduct(embeddingA, embeddingB)
        
        return if (strategy == DeduplicationStrategy.LOCAL && featuresA != null && featuresB != null) {
            optimizer.calculateAdjustedScore(rawScore, featuresA, featuresB)
        } else {
            rawScore
        }
    }

    suspend fun getEmbeddingsParallel(articles: List<Article>, strategy: DeduplicationStrategy): Map<Long, FloatArray> = coroutineScope {
        articles.map { article ->
            async {
                semaphore.withPermit {
                    article.id to getEmbedding(article, strategy)
                }
            }
        }.awaitAll().toMap()
    }
}
