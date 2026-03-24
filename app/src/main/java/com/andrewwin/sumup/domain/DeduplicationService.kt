package com.andrewwin.sumup.domain

import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.ArticleSimilarity
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
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

    suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "initialize: start, modelPath=$modelPath, hasSession=${ortSession != null}")
            if (ortSession != null) return@withContext true
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Log.e(TAG, "initialize: model file not found, path=$modelPath")
                return@withContext false
            }
            val opts = OrtSession.SessionOptions().apply {
                setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
                setIntraOpNumThreads(1)
                setInterOpNumThreads(1)
                setMemoryPatternOptimization(false)
                registerCustomOpLibrary(OrtxPackage.getLibraryPath())
            }
            ortSession = ortEnv.createSession(modelPath, opts)
            Log.d(TAG, "initialize: success, modelPath=$modelPath")
            true
        }.onFailure { e ->
            Log.e(TAG, "initialize: failed, modelPath=$modelPath, error=${e.message}", e)
        }.getOrDefault(false)
    }

    private suspend fun resolveEmbedding(article: Article): FloatArray {
        article.embedding?.let { return EmbeddingUtils.toFloatArray(it) }

        val cloudEmbedding = aiRepository.embed(article.title)
        if (cloudEmbedding != null) {
            val normalized = normalize(cloudEmbedding)
            persistEmbedding(article, normalized)
            return normalized
        }

        val session = ortSession ?: return FloatArray(EMBEDDING_DIM)
        val raw = computeLocalEmbedding(session, article.title)
        val normalized = normalize(resizeEmbedding(raw))
        persistEmbedding(article, normalized)
        return normalized
    }

    private suspend fun persistEmbedding(article: Article, embedding: FloatArray) {
        articleRepository.updateArticles(listOf(article.copy(embedding = EmbeddingUtils.toByteArray(embedding))))
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
                        for (j in 0 until dim) pooled[j] += buf.get()
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
    ): Flow<List<ArticleCluster>> = flow {
        if (articles.size < 2) {
            emit(articles.map { ArticleCluster(it, emptyList()) })
            return@flow
        }
        val clusters = mutableListOf<MutableCluster>()
        processArticlesIntoClusters(articles, clusters, threshold, emitEvery, throttleMs)
        emit(clusters.map { it.toCluster() })
    }.flowOn(Dispatchers.Default)

    fun attachNewArticlesIncremental(
        existingClusters: List<ArticleCluster>,
        newArticles: List<Article>,
        threshold: Float,
        emitEvery: Int = 12,
        throttleMs: Long = 25L
    ): Flow<List<ArticleCluster>> = flow {
        val clusters = existingClusters.map { cluster ->
            MutableCluster(
                representative = cluster.representative,
                repEmbedding = resolveEmbedding(cluster.representative),
                duplicates = cluster.duplicates.toMutableList()
            )
        }.toMutableList()
        processArticlesIntoClusters(newArticles, clusters, threshold, emitEvery, throttleMs)
        emit(clusters.map { it.toCluster() })
    }.flowOn(Dispatchers.Default)

    private suspend fun FlowCollector<List<ArticleCluster>>.processArticlesIntoClusters(
        articles: List<Article>,
        clusters: MutableList<MutableCluster>,
        threshold: Float,
        emitEvery: Int,
        throttleMs: Long
    ) {
        val pendingSimilarities = mutableListOf<ArticleSimilarity>()

        for ((index, article) in articles.withIndex()) {
            val embedding = resolveEmbedding(article)

            val bestMatch = if (embedding.all { it == 0f }) null
            else clusters
                .filter { it.repEmbedding.size == embedding.size }
                .mapNotNull { cluster ->
                    val score = dotProduct(embedding, cluster.repEmbedding)
                    if (score >= threshold) cluster to score else null
                }
                .maxByOrNull { (_, score) -> score }

            if (bestMatch != null) {
                val (matchedCluster, matchedScore) = bestMatch
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
    }

    suspend fun warmUpEmbeddings(
        articles: List<Article>,
        throttleMs: Long = 20L
    ) = withContext(Dispatchers.Default) {
        articles.forEach { article ->
            resolveEmbedding(article)
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

    private fun resizeEmbedding(embedding: FloatArray): FloatArray {
        return when {
            embedding.size == EMBEDDING_DIM -> embedding
            embedding.size > EMBEDDING_DIM -> embedding.copyOfRange(0, EMBEDDING_DIM)
            else -> FloatArray(EMBEDDING_DIM).also { embedding.copyInto(it) }
        }
    }

    fun close() {
        ortSession?.close()
        ortSession = null
    }

    private data class MutableCluster(
        val representative: Article,
        val repEmbedding: FloatArray,
        val duplicates: MutableList<Pair<Article, Float>>
    ) {
        fun toCluster(): ArticleCluster = ArticleCluster(representative, duplicates.toList())
    }

    companion object {
        private const val TAG = "DeduplicationService"
        private const val EMBEDDING_DIM = 768
        private const val DB_BATCH_SIZE = 32
    }
}

object EmbeddingUtils {
    fun toByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        floats.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun toFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buffer.float }
    }
}
