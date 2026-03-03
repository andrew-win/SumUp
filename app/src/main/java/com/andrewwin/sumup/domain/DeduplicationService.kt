package com.andrewwin.sumup.domain

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import android.content.Context
import com.andrewwin.sumup.data.local.entities.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

data class ArticleCluster(
    val representative: Article,
    val duplicates: List<Pair<Article, Float>>
)

class DeduplicationService(private val context: Context) {
    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null

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
        if (articles.size < 2) return@withContext articles.map { ArticleCluster(it, emptyList()) }

        val embeddings = articles.map { article ->
            getEmbedding(session, "${article.title} ${article.content}")
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

    private suspend fun getEmbedding(session: OrtSession, text: String): FloatArray = withContext(Dispatchers.IO) {
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

                    if (outTensor == null) return@withContext FloatArray(768) { 0f }

                    val buf = outTensor.floatBuffer
                    val floats = FloatArray(buf.capacity()).also { buf.get(it) }
                    val dim = outTensor.info.shape.last().toInt()
                    if (dim == 0 || floats.isEmpty()) return@withContext FloatArray(768) { 0f }

                    val tokens = floats.size / dim
                    FloatArray(dim) { j ->
                        var sum = 0f
                        for (i in 0 until tokens) sum += floats[i * dim + j]
                        sum / tokens
                    }
                }
            }
        } catch (e: Exception) {
            FloatArray(768) { 0f }
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val mag = sqrt(na) * sqrt(nb)
        return if (mag > 0) (dot / mag).toFloat() else 0f
    }

    fun close() {
        ortSession?.close()
        ortSession = null
    }
}
