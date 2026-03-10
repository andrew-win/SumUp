package com.andrewwin.sumup.domain

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import com.andrewwin.sumup.data.local.dao.ArticleDao
import com.andrewwin.sumup.data.local.entities.Article
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

data class ArticleCluster(
    val representative: Article,
    val duplicates: List<Pair<Article, Float>>
)

class DeduplicationService(
    private val articleDao: ArticleDao
) {
    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    private val embeddingCache = ConcurrentHashMap<String, FloatArray>()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (ortSession != null) return@withContext true
            val modelFile = File(modelPath)
            if (!modelFile.exists()) return@withContext false
            
            val opts = OrtSession.SessionOptions()
            opts.registerCustomOpLibrary(OrtxPackage.getLibraryPath())
            ortSession = ortEnv.createSession(modelPath, opts)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clusterArticles(articles: List<Article>, threshold: Float): List<ArticleCluster> = withContext(Dispatchers.Default) {
        val session = ortSession ?: return@withContext articles.map { ArticleCluster(it, emptyList()) }
        if (articles.isEmpty()) return@withContext emptyList()
        if (articles.size < 2) return@withContext articles.map { ArticleCluster(it, emptyList()) }

        val toUpdate = mutableListOf<Article>()
        val embeddings = articles.map { article ->
            val cached = embeddingCache[article.url]
            if (cached != null) return@map cached

            val stored = article.embedding?.let { toFloatArray(it) }
            if (stored != null) {
                embeddingCache[article.url] = stored
                return@map stored
            }

            val newEmbedding = getEmbeddingSync(session, article.title)
            embeddingCache[article.url] = newEmbedding
            toUpdate.add(article.copy(embedding = toByteArray(newEmbedding)))
            newEmbedding
        }

        if (toUpdate.isNotEmpty()) {
            serviceScope.launch {
                toUpdate.forEach { articleDao.updateArticle(it) }
            }
        }

        val clusters = mutableListOf<ArticleCluster>()
        val n = articles.size
        val processed = BooleanArray(n) { false }
        
        for (i in 0 until n) {
            if (processed[i]) continue
            processed[i] = true
            
            val duplicates = mutableListOf<Pair<Article, Float>>()
            for (j in i + 1 until n) {
                if (processed[j]) continue
                val sim = cosineSimilarity(embeddings[i], embeddings[j])
                if (sim >= threshold) {
                    duplicates.add(articles[j] to sim)
                    processed[j] = true
                }
            }
            clusters.add(ArticleCluster(articles[i], duplicates))
        }

        clusters
    }

    private fun getEmbeddingSync(session: OrtSession, text: String): FloatArray {
        try {
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

                    if (outTensor == null) return FloatArray(768) { 0f }

                    val buf = outTensor.floatBuffer
                    val floats = FloatArray(buf.capacity()).also { buf.get(it) }
                    val dim = outTensor.info.shape.last().toInt()
                    if (dim == 0 || floats.isEmpty()) return FloatArray(768) { 0f }

                    val tokens = floats.size / dim
                    return FloatArray(dim) { j ->
                        var sum = 0f
                        for (i in 0 until tokens) sum += floats[i * dim + j]
                        sum / tokens
                    }
                }
            }
        } catch (e: Exception) {
            return FloatArray(768) { 0f }
        }
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

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i].toDouble()
            na += a[i].toDouble() * a[i].toDouble()
            nb += b[i].toDouble() * b[i].toDouble()
        }
        val mag = sqrt(na) * sqrt(nb)
        return if (mag > 0) (dot / mag).toFloat() else 0f
    }

    fun close() {
        ortSession?.close()
        ortSession = null
        embeddingCache.clear()
        serviceScope.cancel()
    }
}
