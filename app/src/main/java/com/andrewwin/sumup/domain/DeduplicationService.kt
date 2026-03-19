package com.andrewwin.sumup.domain

import android.util.LruCache
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.ArticleSimilarity
import com.andrewwin.sumup.domain.repository.ArticleRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class ArticleCluster(
    val representative: Article,
    val duplicates: List<Pair<Article, Float>>
)

class DeduplicationService(
    private val articleRepository: ArticleRepository
) {
    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    private val embeddingMutex = Mutex()
    private val representativeEmbeddingCache = LruCache<Long, FloatArray>(REP_EMBED_CACHE_SIZE)

    suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (ortSession != null) return@withContext true
            val modelFile = File(modelPath)
            if (!modelFile.exists()) return@withContext false
            val opts = OrtSession.SessionOptions()
            opts.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            opts.setIntraOpNumThreads(1)
            opts.setInterOpNumThreads(1)
            opts.setMemoryPatternOptimization(false)
            opts.registerCustomOpLibrary(OrtxPackage.getLibraryPath())
            ortSession = ortEnv.createSession(modelPath, opts)
            true
        }.getOrDefault(false)
    }

    suspend fun clusterArticles(articles: List<Article>, threshold: Float): List<ArticleCluster> =
        withContext(Dispatchers.Default) {
            val session = ortSession ?: return@withContext articles.map { ArticleCluster(it, emptyList()) }
            if (articles.size < 2) return@withContext articles.map { ArticleCluster(it, emptyList()) }
            val embeddingsById = articleRepository.getEmbeddingsByIds(articles.map { it.id })
            val embeddingResults = ArrayList<EmbeddingResult>(articles.size)
            for (chunk in articles.chunked(EMBEDDING_CHUNK_SIZE)) {
                for (article in chunk) {
                    embeddingResults += loadEmbeddingNormalized(session, article, embeddingsById)
                }
                yieldChunkBoundary()
            }

            val toUpdate = embeddingResults.mapNotNull { it.updatedArticle }
            if (toUpdate.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    articleRepository.updateArticles(toUpdate)
                }
            }

            val embeddings = embeddingResults.map { it.embedding }

            val n = articles.size
            val processed = BooleanArray(n) { false }
            val clusters = mutableListOf<ArticleCluster>()
            val similaritiesToPersist = mutableListOf<ArticleSimilarity>()

            for (i in 0 until n) {
                if (processed[i]) continue
                processed[i] = true
                val duplicates = mutableListOf<Pair<Article, Float>>()
                for (j in i + 1 until n) {
                    if (processed[j]) continue
                    val sim = dotProduct(embeddings[i], embeddings[j])
                    if (sim >= threshold) {
                        duplicates.add(articles[j] to sim)
                        similaritiesToPersist.add(
                            ArticleSimilarity(articles[i].id, articles[j].id, sim)
                        )
                        processed[j] = true
                    }
                }
                clusters.add(ArticleCluster(articles[i], duplicates))
            }

            if (similaritiesToPersist.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    articleRepository.upsertSimilarities(similaritiesToPersist)
                }
            }

            clusters
        }

    suspend fun warmUpEmbeddings(
        articles: List<Article>,
        throttleMs: Long = 20L
    ) = withContext(Dispatchers.Default.limitedParallelism(2)) {
        val session = ortSession ?: return@withContext
        val embeddingsById = articleRepository.getEmbeddingsByIds(articles.map { it.id })
        val pendingUpdates = mutableListOf<Article>()
        for (chunk in articles.chunked(EMBEDDING_CHUNK_SIZE)) {
            for (article in chunk) {
                embeddingMutex.withLock {
                    getOrComputeEmbedding(session, article, pendingUpdates, embeddingsById)
                }
                flushPendingUpdatesIfNeeded(pendingUpdates)
                if (throttleMs > 0) delay(throttleMs)
            }
            yieldChunkBoundary()
        }
        flushPendingUpdates(pendingUpdates)
    }

    fun clusterArticlesIncremental(
        articles: List<Article>,
        threshold: Float,
        emitEvery: Int = 12,
        throttleMs: Long = 25L
    ): Flow<List<ArticleCluster>> =
        flow {
            val session = ortSession ?: run {
                emit(articles.map { ArticleCluster(it, emptyList()) })
                return@flow
            }
            if (articles.size < 2) {
                emit(articles.map { ArticleCluster(it, emptyList()) })
                return@flow
            }

            val embeddingsById = articleRepository.getEmbeddingsByIds(articles.map { it.id })
            val clusters = mutableListOf<MutableCluster>()

            val pendingUpdates = mutableListOf<Article>()
            val pendingSimilarities = mutableListOf<ArticleSimilarity>()
            for ((index, article) in articles.withIndex()) {
                val embedding = embeddingMutex.withLock {
                    getOrComputeEmbedding(session, article, pendingUpdates, embeddingsById)
                }
                var matchedCluster: MutableCluster? = null
                var matchedScore = 0f

                for (cluster in clusters) {
                    val score = dotProduct(embedding, cluster.repEmbedding)
                    if (score >= threshold) {
                        matchedCluster = cluster
                        matchedScore = score
                        break
                    }
                }

                if (matchedCluster != null) {
                    matchedCluster.duplicates.add(article to matchedScore)
                    pendingSimilarities.add(
                        ArticleSimilarity(matchedCluster.representative.id, article.id, matchedScore)
                    )
                } else {
                    clusters.add(MutableCluster(article, embedding, mutableListOf()))
                }

                flushPendingUpdatesIfNeeded(pendingUpdates)
                flushPendingSimilaritiesIfNeeded(pendingSimilarities)

                if ((index + 1) % emitEvery == 0) {
                    emit(clusters.map { it.toCluster() })
                }

                if (throttleMs > 0) delay(throttleMs)
            }

            flushPendingUpdates(pendingUpdates)
            flushPendingSimilarities(pendingSimilarities)

            emit(clusters.map { it.toCluster() })
        }.flowOn(Dispatchers.Default.limitedParallelism(2))

    fun attachNewArticlesIncremental(
        existingClusters: List<ArticleCluster>,
        newArticles: List<Article>,
        threshold: Float,
        emitEvery: Int = 12,
        throttleMs: Long = 25L
    ): Flow<List<ArticleCluster>> =
        flow {
            if (newArticles.isEmpty()) {
                emit(existingClusters)
                return@flow
            }
            val session = ortSession ?: run {
                emit(existingClusters + newArticles.map { ArticleCluster(it, emptyList()) })
                return@flow
            }

            val clusters = existingClusters.map { cluster ->
                val repEmbedding = representativeEmbeddingCache.get(cluster.representative.id)
                    ?: getOrComputeEmbedding(session, cluster.representative).also {
                        representativeEmbeddingCache.put(cluster.representative.id, it)
                    }
                MutableCluster(
                    representative = cluster.representative,
                    repEmbedding = repEmbedding,
                    duplicates = cluster.duplicates.toMutableList()
                )
            }.toMutableList()

            val embeddingsById = articleRepository.getEmbeddingsByIds(newArticles.map { it.id })
            val pendingUpdates = mutableListOf<Article>()
            val pendingSimilarities = mutableListOf<ArticleSimilarity>()
            for ((index, article) in newArticles.withIndex()) {
                val embedding = embeddingMutex.withLock {
                    getOrComputeEmbedding(session, article, pendingUpdates, embeddingsById)
                }
                var matchedCluster: MutableCluster? = null
                var matchedScore = 0f

                for (cluster in clusters) {
                    val score = dotProduct(embedding, cluster.repEmbedding)
                    if (score >= threshold) {
                        matchedCluster = cluster
                        matchedScore = score
                        break
                    }
                }

                if (matchedCluster != null) {
                    matchedCluster.duplicates.add(article to matchedScore)
                    pendingSimilarities.add(
                        ArticleSimilarity(matchedCluster.representative.id, article.id, matchedScore)
                    )
                } else {
                    representativeEmbeddingCache.put(article.id, embedding)
                    clusters.add(MutableCluster(article, embedding, mutableListOf()))
                }

                flushPendingUpdatesIfNeeded(pendingUpdates)
                flushPendingSimilaritiesIfNeeded(pendingSimilarities)

                if ((index + 1) % emitEvery == 0) {
                    emit(clusters.map { it.toCluster() })
                }

                if (throttleMs > 0) delay(throttleMs)
            }

            flushPendingUpdates(pendingUpdates)
            flushPendingSimilarities(pendingSimilarities)

            emit(clusters.map { it.toCluster() })
        }.flowOn(Dispatchers.Default.limitedParallelism(2))

    private suspend fun getOrComputeEmbedding(
        session: OrtSession,
        article: Article,
        pendingUpdates: MutableList<Article>? = null,
        embeddingsById: Map<Long, ByteArray?>? = null
    ): FloatArray {
        val result = loadEmbeddingNormalized(session, article, embeddingsById)
        result.updatedArticle?.let { updated ->
            if (pendingUpdates != null) {
                pendingUpdates.add(updated)
            } else {
                withContext(Dispatchers.IO) {
                    articleRepository.updateArticles(listOf(updated))
                }
            }
        }
        return result.embedding
    }

    private fun getEmbedding(session: OrtSession, text: String): FloatArray {
        return runCatching {
            val inputName = session.inputNames.first()
            OnnxTensor.createTensor(ortEnv, arrayOf(text)).use { tensor ->
                session.run(mapOf(inputName to tensor)).use { results ->
                    var outTensor: OnnxTensor? = null
                    for (entry in results) {
                        if (entry.value is OnnxTensor) {
                            outTensor = entry.value as OnnxTensor
                            if (entry.key == "last_hidden_state") break
                        }
                    }
                    outTensor ?: return@runCatching FloatArray(EMBEDDING_DIM)

                    val buf = outTensor.floatBuffer
                    val dim = outTensor.info.shape.last().toInt()
                    if (dim == 0 || buf.capacity() == 0) return@runCatching FloatArray(EMBEDDING_DIM)

                    val tokens = (buf.capacity() / dim).coerceAtLeast(1)
                    val pooled = FloatArray(dim)
                    for (i in 0 until tokens) {
                        for (j in 0 until dim) {
                            pooled[j] += buf.get()
                        }
                    }
                    for (j in pooled.indices) pooled[j] /= tokens
                    pooled
                }
            }
        }.getOrDefault(FloatArray(EMBEDDING_DIM))
    }

    private fun normalize(vector: FloatArray): FloatArray {
        val mag = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        return if (mag > 0f) FloatArray(vector.size) { vector[it] / mag } else vector
    }

    private fun isNormalized(vector: FloatArray): Boolean {
        val mag = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        return kotlin.math.abs(1f - mag) <= 0.01f
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    private fun toByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        floats.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun toFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buffer.float }
    }

    private data class EmbeddingResult(
        val embedding: FloatArray,
        val updatedArticle: Article?
    )

    private suspend fun loadEmbeddingNormalized(
        session: OrtSession,
        article: Article,
        embeddingsById: Map<Long, ByteArray?>? = null
    ): EmbeddingResult {
        val storedBytes = article.embedding ?: embeddingsById?.get(article.id)
        val stored = storedBytes?.let { toFloatArray(it) }
        if (stored != null) {
            return if (isNormalized(stored) && stored.size == EMBEDDING_DIM) {
                representativeEmbeddingCache.put(article.id, stored)
                EmbeddingResult(stored, null)
            } else {
                val normalized = normalize(resizeEmbedding(stored))
                representativeEmbeddingCache.put(article.id, normalized)
                EmbeddingResult(normalized, article.copy(embedding = toByteArray(normalized)))
            }
        }

        val raw = getEmbedding(session, article.title)
        val normalized = normalize(resizeEmbedding(raw))
        representativeEmbeddingCache.put(article.id, normalized)
        return EmbeddingResult(normalized, article.copy(embedding = toByteArray(normalized)))
    }

    fun close() {
        ortSession?.close()
        ortSession = null
        representativeEmbeddingCache.evictAll()
    }

    private data class MutableCluster(
        val representative: Article,
        val repEmbedding: FloatArray,
        val duplicates: MutableList<Pair<Article, Float>>
    ) {
        fun toCluster(): ArticleCluster = ArticleCluster(representative, duplicates.toList())
    }

    private fun resizeEmbedding(embedding: FloatArray): FloatArray {
        return when {
            embedding.size == EMBEDDING_DIM -> embedding
            embedding.size > EMBEDDING_DIM -> embedding.copyOfRange(0, EMBEDDING_DIM)
            else -> FloatArray(EMBEDDING_DIM).also { embedding.copyInto(it) }
        }
    }

    private suspend fun persistSimilarities(representativeId: Long, articleId: Long, score: Float) {
        withContext(Dispatchers.IO) {
            articleRepository.upsertSimilarities(
                listOf(ArticleSimilarity(representativeId, articleId, score))
            )
        }
    }

    private suspend fun flushPendingUpdatesIfNeeded(pendingUpdates: MutableList<Article>) {
        if (pendingUpdates.size >= DB_BATCH_SIZE) {
            flushPendingUpdates(pendingUpdates)
        }
    }

    private suspend fun flushPendingUpdates(pendingUpdates: MutableList<Article>) {
        if (pendingUpdates.isEmpty()) return
        val batch = pendingUpdates.toList()
        pendingUpdates.clear()
        withContext(Dispatchers.IO) {
            articleRepository.updateArticles(batch)
        }
    }

    private suspend fun flushPendingSimilaritiesIfNeeded(pendingSimilarities: MutableList<ArticleSimilarity>) {
        if (pendingSimilarities.size >= DB_BATCH_SIZE) {
            flushPendingSimilarities(pendingSimilarities)
        }
    }

    private suspend fun flushPendingSimilarities(pendingSimilarities: MutableList<ArticleSimilarity>) {
        if (pendingSimilarities.isEmpty()) return
        val batch = pendingSimilarities.toList()
        pendingSimilarities.clear()
        withContext(Dispatchers.IO) {
            articleRepository.upsertSimilarities(batch)
        }
    }

    private suspend fun yieldChunkBoundary() {
        kotlinx.coroutines.yield()
    }

    companion object {
        private const val EMBEDDING_DIM = 768
        private const val DB_BATCH_SIZE = 32
        private const val REP_EMBED_CACHE_SIZE = 128
        private const val EMBEDDING_CHUNK_SIZE = 8
    }
}
