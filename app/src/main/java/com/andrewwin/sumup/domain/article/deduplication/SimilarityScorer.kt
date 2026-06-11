package com.andrewwin.sumup.domain.article.deduplication

import android.os.SystemClock
import android.util.Log
import com.andrewwin.sumup.domain.article.model.Article
import com.andrewwin.sumup.domain.article.model.ArticleEmbeddingRecord
import com.andrewwin.sumup.domain.settings.model.DeduplicationStrategy
import com.andrewwin.sumup.domain.ai.embedding.CloudEmbeddingProvider
import com.andrewwin.sumup.domain.ai.embedding.LocalEmbeddingProvider
import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicLong

class SimilarityScorer(
    private val articleRepository: ArticleRepository,
    private val localEmbeddingProvider: LocalEmbeddingProvider,
    private val cloudEmbeddingProvider: CloudEmbeddingProvider,
    private val dedupRuntimeCoordinator: DedupRuntimeCoordinator
) {
    private val cloudEmbeddingSemaphore = Semaphore(CLOUD_EMBEDDING_PARALLELISM)
    private val embeddingsRunMutex = Mutex()

    suspend fun initialize(): Boolean =
        localEmbeddingProvider.initialize()

    fun close() {
        localEmbeddingProvider.close()
    }

    fun calculateSimilarity(
        embeddingA: FloatArray,
        embeddingB: FloatArray
    ): Float {
        if (EmbeddingUtils.isZeroVector(embeddingA) || EmbeddingUtils.isZeroVector(embeddingB)) return 0f
        return EmbeddingUtils.dotProduct(embeddingA, embeddingB)
    }

    fun similarityCacheKeyForStrategy(strategy: DeduplicationStrategy): String =
        embeddingTypeForStrategy(strategy)

    fun thresholdSimilarityCacheKey(
        strategy: DeduplicationStrategy,
        threshold: Float
    ): String = "${similarityCacheKeyForStrategy(strategy)}|thr=${"%.4f".format(java.util.Locale.US, threshold)}"

    fun getEmbeddingsProgress(
        articles: List<Article>,
        strategy: DeduplicationStrategy
    ): Flow<EmbeddingProgress> = flow {
        embeddingsRunMutex.withLock {
            if (articles.isEmpty()) {
                emit(EmbeddingProgress(emptyMap(), 0, 0, isComplete = true))
                return@withLock
            }

            val runId = embeddingRunId.incrementAndGet()
            val runGeneration = dedupRuntimeCoordinator.currentEmbeddingsGeneration()
            val embeddingType = embeddingTypeForStrategy(strategy)
            val result = mutableMapOf<Long, FloatArray>()
            val storedEmbeddings = articleRepository.getArticleEmbeddingsByIds(articles.map { it.id })
                .associateBy { it.id }

            articles.forEach { article ->
                val articleEmbedding = article.embedding
                val articleEmbeddingVector = articleEmbedding?.let(EmbeddingUtils::toFloatArray)
                if (article.embeddingType == embeddingType && isUsableEmbedding(articleEmbeddingVector)) {
                    articleEmbeddingVector?.let { result[article.id] = it }
                    return@forEach
                }

                val stored = storedEmbeddings[article.id]
                val storedEmbeddingVector = stored?.embedding?.let(EmbeddingUtils::toFloatArray)
                if (stored?.embeddingType == embeddingType && isUsableEmbedding(storedEmbeddingVector)) {
                    storedEmbeddingVector?.let { result[article.id] = it }
                }
            }

            val missingArticles = articles.filterNot { result.containsKey(it.id) }

            if (missingArticles.isEmpty()) {
                emit(
                    EmbeddingProgress(
                        embeddingsById = result.toMap(),
                        processedArticlesCount = result.size,
                        totalArticlesCount = articles.size,
                        isComplete = true
                    )
                )
                return@withLock
            }

            val generatedEmbeddings = mutableListOf<GeneratedEmbedding>()
            var cloudMissingGenerationFailed = false

            when (strategy) {
                DeduplicationStrategy.CLOUD -> {
                    val chunks = missingArticles.chunked(CLOUD_EMBEDDING_BATCH_SIZE)
                    for ((chunkIndex, chunk) in chunks.withIndex()) {
                        val batchResult = getCloudEmbeddingBatch(
                            runId = runId,
                            chunk = chunk,
                            chunkIndex = chunkIndex,
                            totalChunkCount = chunks.size,
                            runGeneration = runGeneration
                        )
                        generatedEmbeddings.addAll(batchResult.generatedEmbeddings)
                        batchResult.generatedEmbeddings.forEach { generated ->
                            generated.embedding
                                ?.takeIf(::isUsableEmbedding)
                                ?.let { result[generated.article.id] = it }
                        }
                        val batchFailed = batchResult.shouldStop &&
                            batchResult.generatedEmbeddings.none { isUsableEmbedding(it.embedding) }
                        if (batchResult.shouldStop) {
                            cloudMissingGenerationFailed = batchFailed
                            break
                        }
                    }
                }

                DeduplicationStrategy.LOCAL -> {
                    val localGeneratedEmbeddings = generateLocalEmbeddings(
                        runId = runId,
                        articles = missingArticles
                    )
                    generatedEmbeddings.addAll(localGeneratedEmbeddings)
                    localGeneratedEmbeddings.forEach { generated ->
                        generated.embedding
                            ?.takeIf(::isUsableEmbedding)
                            ?.let { result[generated.article.id] = it }
                    }
                }
            }

            if (dedupRuntimeCoordinator.currentEmbeddingsGeneration() == runGeneration) {
                saveGeneratedEmbeddings(generatedEmbeddings, embeddingType)
            }
            emit(
                EmbeddingProgress(
                    embeddingsById = result.toMap(),
                    processedArticlesCount = result.size,
                    totalArticlesCount = articles.size,
                    isComplete = true,
                    cloudMissingGenerationFailed = cloudMissingGenerationFailed
                )
            )
        }
    }

    private suspend fun getCloudEmbeddingBatch(
        runId: Long,
        chunk: List<Article>,
        chunkIndex: Int,
        totalChunkCount: Int,
        runGeneration: Long
) : BatchResult {
        val cloudEmbeddings = cloudEmbeddingSemaphore.withPermit {
            cloudEmbeddingProvider.generateEmbeddings(
                chunk.map { it.title },
                runId
            ).map { embedding -> embedding?.let(EmbeddingUtils::normalize) }
        }

        val batchGeneratedEmbeddings = chunk.mapIndexed { index, article ->
            val embedding = cloudEmbeddings.getOrNull(index)?.let { cloud ->
                    EmbeddingUtils.normalize(EmbeddingUtils.resizeEmbedding(cloud))
                }
            GeneratedEmbedding(article, embedding)
        }

        if (dedupRuntimeCoordinator.currentEmbeddingsGeneration() != runGeneration) {
            return BatchResult(batchGeneratedEmbeddings, shouldStop = true)
        }

        val shouldStop = batchGeneratedEmbeddings.none { isUsableEmbedding(it.embedding) }
        if (!shouldStop && chunkIndex < totalChunkCount - 1) {
            delay(CLOUD_EMBEDDING_BATCH_DELAY_MS)
        }

        return BatchResult(batchGeneratedEmbeddings, shouldStop)
    }

    private suspend fun generateLocalEmbeddings(
        runId: Long,
        articles: List<Article>
    ) : List<GeneratedEmbedding> {
        val startMs = SystemClock.elapsedRealtime()
        val generatedEmbeddings = mutableListOf<GeneratedEmbedding>()
        var totalEmbeddingMs = 0L
        articles.forEach { article ->
            val titleForEmbedding = article.title
            val embeddingStartMs = SystemClock.elapsedRealtime()
            val embedding = localEmbeddingProvider.computeLocalEmbedding(titleForEmbedding)
            totalEmbeddingMs += SystemClock.elapsedRealtime() - embeddingStartMs
            generatedEmbeddings += GeneratedEmbedding(article, embedding)
        }
        Log.d(
            EMBEDDINGS_TEST_LOG_TAG,
            "local_embedding_avg_ms=${totalEmbeddingMs / articles.size.coerceAtLeast(1)} " +
                "count=${articles.size} total_ms=${SystemClock.elapsedRealtime() - startMs} " +
                "runId=$runId"
        )

        return generatedEmbeddings
    }

    private suspend fun saveGeneratedEmbeddings(generatedEmbeddings: List<GeneratedEmbedding>, embeddingType: String) {
        val updatedEmbeddings = generatedEmbeddings.mapNotNull { generated ->
            val embedding = generated.embedding ?: return@mapNotNull null
            if (!isUsableEmbedding(embedding)) return@mapNotNull null
            ArticleEmbeddingRecord(
                id = generated.article.id,
                embedding = EmbeddingUtils.toByteArray(embedding),
                embeddingType = embeddingType
            )
        }
        articleRepository.upsertArticleEmbeddings(updatedEmbeddings)
    }

    private fun isUsableEmbedding(embedding: FloatArray?): Boolean =
        embedding != null && !EmbeddingUtils.isZeroVector(embedding)

    private fun embeddingTypeForStrategy(strategy: DeduplicationStrategy): String =
        when (strategy) {
            DeduplicationStrategy.CLOUD -> "${strategy.name}-$DEDUP_EMBEDDING_CACHE_VERSION"
            DeduplicationStrategy.LOCAL -> "${localEmbeddingProvider.embeddingCacheType}-$DEDUP_EMBEDDING_CACHE_VERSION"
        }

    private fun emptyEmbeddingForStrategy(strategy: DeduplicationStrategy): FloatArray =
        when (strategy) {
            DeduplicationStrategy.CLOUD -> FloatArray(EmbeddingUtils.EMBEDDING_DIM)
            DeduplicationStrategy.LOCAL -> FloatArray(EmbeddingUtils.LOCAL_EMBEDDING_DIM)
        }

    private companion object {
        private const val CLOUD_EMBEDDING_PARALLELISM = 4
        private const val CLOUD_EMBEDDING_BATCH_SIZE = 32
        private const val CLOUD_EMBEDDING_BATCH_DELAY_MS = 1_000L
        private const val EMBEDDINGS_TEST_LOG_TAG = "EmbedingsTest"
        private const val DEDUP_EMBEDDING_CACHE_VERSION = "title-full-v4"
        private val embeddingRunId = AtomicLong(0)
    }

    private data class GeneratedEmbedding(
        val article: Article,
        val embedding: FloatArray?
    )

    data class EmbeddingProgress(
        val embeddingsById: Map<Long, FloatArray>,
        val processedArticlesCount: Int,
        val totalArticlesCount: Int,
        val isComplete: Boolean,
        val cloudMissingGenerationFailed: Boolean = false
    )

    private data class BatchResult(
        val generatedEmbeddings: List<GeneratedEmbedding>,
        val shouldStop: Boolean
    )
}
