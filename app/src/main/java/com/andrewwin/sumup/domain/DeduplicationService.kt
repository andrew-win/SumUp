package com.andrewwin.sumup.domain

import android.util.Log
import android.util.LruCache
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.ArticleSimilarity
import com.andrewwin.sumup.domain.repository.AiRepository
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
    private val articleRepository: ArticleRepository,
    private val aiRepository: AiRepository
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

    private suspend fun getEmbedding(article: Article): FloatArray {
        article.embedding?.let { return toFloatArray(it) }
        
        // Try cloud first if available
        val cloudEmbedding = aiRepository.embed(article.title)
        if (cloudEmbedding != null) {
            val normalized = normalize(cloudEmbedding)
            articleRepository.updateArticles(listOf(article.copy(embedding = toByteArray(normalized))))
            return normalized
        }

        // Fallback to local
        val session = ortSession ?: return FloatArray(EMBEDDING_DIM)
        val raw = computeLocalEmbedding(session, article.title)
        val normalized = normalize(resizeEmbedding(raw))
        articleRepository.updateArticles(listOf(article.copy(embedding = toByteArray(normalized))))
        return normalized
    }

    private fun computeLocalEmbedding(session: OrtSession, text: String): FloatArray {
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

    fun clusterArticlesIncremental(
        articles: List<Article>,
        threshold: Float,
        emitEvery: Int = 12,
        throttleMs: Long = 25L
    ): Flow<List<ArticleCluster>> =
        flow {
            if (articles.size < 2) {
                emit(articles.map { ArticleCluster(it, emptyList()) })
                return@flow
            }

            val clusters = mutableListOf<MutableCluster>()
            val pendingSimilarities = mutableListOf<ArticleSimilarity>()
            
            for ((index, article) in articles.withIndex()) {
                val embedding = embeddingMutex.withLock { getEmbedding(article) }
                if (embedding.all { it == 0f }) {
                    clusters.add(MutableCluster(article, embedding, mutableListOf()))
                    continue
                }

                var matchedCluster: MutableCluster? = null
                var matchedScore = 0f

                for (cluster in clusters) {
                    if (cluster.repEmbedding.size != embedding.size) continue
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

                if (pendingSimilarities.size >= DB_BATCH_SIZE) {
                    articleRepository.upsertSimilarities(pendingSimilarities.toList())
                    pendingSimilarities.clear()
                }

                if ((index + 1) % emitEvery == 0) {
                    emit(clusters.map { it.toCluster() })
                }

                if (throttleMs > 0) delay(throttleMs)
            }

            if (pendingSimilarities.isNotEmpty()) {
                articleRepository.upsertSimilarities(pendingSimilarities)
            }

            emit(clusters.map { it.toCluster() })
        }.flowOn(Dispatchers.Default.limitedParallelism(2))

    suspend fun attachNewArticlesIncremental(
        existingClusters: List<ArticleCluster>,
        newArticles: List<Article>,
        threshold: Float,
        emitEvery: Int = 12,
        throttleMs: Long = 25L
    ): Flow<List<ArticleCluster>> = flow {
        val clusters = existingClusters.map { cluster ->
            MutableCluster(
                representative = cluster.representative,
                repEmbedding = embeddingMutex.withLock { getEmbedding(cluster.representative) },
                duplicates = cluster.duplicates.toMutableList()
            )
        }.toMutableList()

        val pendingSimilarities = mutableListOf<ArticleSimilarity>()
        for ((index, article) in newArticles.withIndex()) {
            val embedding = embeddingMutex.withLock { getEmbedding(article) }
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

            if (pendingSimilarities.size >= DB_BATCH_SIZE) {
                articleRepository.upsertSimilarities(pendingSimilarities.toList())
                pendingSimilarities.clear()
            }

            if ((index + 1) % emitEvery == 0) {
                emit(clusters.map { it.toCluster() })
            }
            if (throttleMs > 0) delay(throttleMs)
        }
        
        if (pendingSimilarities.isNotEmpty()) {
            articleRepository.upsertSimilarities(pendingSimilarities)
        }
        emit(clusters.map { it.toCluster() })
    }.flowOn(Dispatchers.Default.limitedParallelism(2))

    suspend fun warmUpEmbeddings(
        articles: List<Article>,
        throttleMs: Long = 20L
    ) = withContext(Dispatchers.Default) {
        articles.forEach { article ->
            embeddingMutex.withLock { getEmbedding(article) }
            if (throttleMs > 0) delay(throttleMs)
        }
    }

    private fun normalize(vector: FloatArray): FloatArray {
        val mag = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        return if (mag > 0f) FloatArray(vector.size) { vector[it] / mag } else vector
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

    private fun resizeEmbedding(embedding: FloatArray): FloatArray {
        return when {
            embedding.size == EMBEDDING_DIM -> embedding
            embedding.size > EMBEDDING_DIM -> embedding.copyOfRange(0, EMBEDDING_DIM)
            else -> FloatArray(EMBEDDING_DIM).also { embedding.copyInto(it) }
        }
    }

    private data class MutableCluster(
        val representative: Article,
        val repEmbedding: FloatArray,
        val duplicates: MutableList<Pair<Article, Float>>
    ) {
        fun toCluster(): ArticleCluster = ArticleCluster(representative, duplicates.toList())
    }

    fun close() {
        ortSession?.close()
        ortSession = null
        representativeEmbeddingCache.evictAll()
    }

    companion object {
        private const val EMBEDDING_DIM = 768
        private const val DB_BATCH_SIZE = 32
        private const val REP_EMBED_CACHE_SIZE = 128
    }
}
