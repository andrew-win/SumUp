package com.andrewwin.sumup.domain.news

import android.os.SystemClock
import android.util.Log
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.DeduplicationStrategy
import com.andrewwin.sumup.domain.ai.CloudEmbeddingProvider
import com.andrewwin.sumup.domain.ai.LocalEmbeddingProvider
import com.andrewwin.sumup.domain.repository.ArticleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
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
    private val localEmbeddingSemaphore = Semaphore(LOCAL_EMBEDDING_PARALLELISM)
    private val cloudEmbeddingSemaphore = Semaphore(CLOUD_EMBEDDING_PARALLELISM)
    private val embeddingsRunMutex = Mutex()

    suspend fun initialize(): Boolean =
        localEmbeddingProvider.initialize()

    fun close() {
        localEmbeddingProvider.close()
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
        val deduplicationEmbeddingTitle = article.title.removeEmojiForDeduplicationEmbeddingInput()
        val embedding = when (strategy) {
            DeduplicationStrategy.CLOUD -> {
                val cloud = cloudEmbeddingProvider.generateEmbedding(deduplicationEmbeddingTitle)
                if (cloud != null) {
                    EmbeddingUtils.normalize(EmbeddingUtils.resizeEmbedding(cloud))
                } else null
            }
            DeduplicationStrategy.LOCAL -> {
                val raw = localEmbeddingProvider.computeLocalEmbedding(deduplicationEmbeddingTitle)
                EmbeddingUtils.normalize(raw)
            }
        }

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
                if (article.embeddingType == embeddingType && articleEmbeddingVector != null) {
                    return@forEach
                }

                val stored = storedEmbeddings[article.id]
                val storedEmbeddingVector = stored?.embedding?.let(EmbeddingUtils::toFloatArray)
                if (stored?.embeddingType == embeddingType && isUsableEmbedding(storedEmbeddingVector)) {
                    storedEmbeddingVector?.let { result[article.id] = it }
                } else if (stored?.embeddingType == embeddingType && storedEmbeddingVector != null) {
                    return@forEach
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
                    val chunks = missingArticles.chunked(LOCAL_EMBEDDING_BATCH_SIZE)
                    for ((chunkIndex, chunk) in chunks.withIndex()) {
                        val batchGeneratedEmbeddings = getLocalEmbeddingBatch(
                            runId = runId,
                            chunk = chunk,
                            chunkIndex = chunkIndex,
                            totalChunkCount = chunks.size
                        )
                        generatedEmbeddings.addAll(batchGeneratedEmbeddings)
                        batchGeneratedEmbeddings.forEach { generated ->
                            generated.embedding
                                ?.takeIf(::isUsableEmbedding)
                                ?.let { result[generated.article.id] = it }
                        }
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
                chunk.map { it.title.removeEmojiForDeduplicationEmbeddingInput() },
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

    private suspend fun getLocalEmbeddingBatch(
        runId: Long,
        chunk: List<Article>,
        chunkIndex: Int,
        totalChunkCount: Int
) : List<GeneratedEmbedding> {
        val batchStartMs = SystemClock.elapsedRealtime()
        val batchGeneratedEmbeddings = mutableListOf<GeneratedEmbedding>()
        var totalEmbeddingMs = 0L
        chunk.forEach { article ->
            localEmbeddingSemaphore.withPermit {
                val titleForEmbedding = article.title.removeEmojiForDeduplicationEmbeddingInput()
                val embeddingStartMs = SystemClock.elapsedRealtime()
                val embedding = EmbeddingUtils.normalize(localEmbeddingProvider.computeLocalEmbedding(titleForEmbedding))
                totalEmbeddingMs += SystemClock.elapsedRealtime() - embeddingStartMs
                batchGeneratedEmbeddings.add(GeneratedEmbedding(article, embedding))
            }
        }
        Log.d(
            EMBEDDINGS_TEST_LOG_TAG,
            "local_embedding_avg_ms=${totalEmbeddingMs / chunk.size.coerceAtLeast(1)} " +
                "count=${chunk.size} total_ms=${SystemClock.elapsedRealtime() - batchStartMs} " +
                "runId=$runId batch=${chunkIndex + 1}/$totalChunkCount"
        )

        return batchGeneratedEmbeddings
    }

    private suspend fun saveGeneratedEmbeddings(generatedEmbeddings: List<GeneratedEmbedding>, embeddingType: String) {
        val updatedArticles = generatedEmbeddings.mapNotNull { generated ->
            val embedding = generated.embedding ?: return@mapNotNull null
            if (!isUsableEmbedding(embedding)) return@mapNotNull null
            generated.article.copy(
                embedding = EmbeddingUtils.toByteArray(embedding),
                embeddingType = embeddingType
            )
        }
        articleRepository.updateArticles(updatedArticles)
    }

    private fun isUsableEmbedding(embedding: FloatArray?): Boolean =
        embedding != null && !EmbeddingUtils.isZeroVector(embedding)

    private fun embeddingSemaphoreForStrategy(strategy: DeduplicationStrategy): Semaphore =
        when (strategy) {
            DeduplicationStrategy.CLOUD -> cloudEmbeddingSemaphore
            DeduplicationStrategy.LOCAL -> localEmbeddingSemaphore
        }

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

    private fun String.removeEmojiForDeduplicationEmbeddingInput(): String {
        return replace(DEDUPE_EMOJI_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private companion object {
        private const val LOCAL_EMBEDDING_PARALLELISM = 1
        private const val LOCAL_EMBEDDING_BATCH_SIZE = 32
        private const val CLOUD_EMBEDDING_PARALLELISM = 4
        private const val CLOUD_EMBEDDING_BATCH_SIZE = 32
        private const val CLOUD_EMBEDDING_BATCH_DELAY_MS = 1_000L
        private const val EMBEDDINGS_TEST_LOG_TAG = "EmbedingsTest"
        private const val DEDUP_EMBEDDING_CACHE_VERSION = "emoji-title-v2"
        private val DEDUPE_EMOJI_REGEX = Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\uFE0E\\uFE0F\\u20E3]+")
        private val WHITESPACE_REGEX = Regex("\\s+")
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
