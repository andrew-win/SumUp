package com.andrewwin.sumup.domain

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.ArticleSimilarity
import com.andrewwin.sumup.domain.repository.ArticleRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import java.util.concurrent.ConcurrentHashMap
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
    private val embeddingCache = ConcurrentHashMap<String, FloatArray>()
    private val embeddingMutex = Mutex()

    suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (ortSession != null) return@withContext true
            val modelFile = File(modelPath)
            if (!modelFile.exists()) return@withContext false
            val opts = OrtSession.SessionOptions()
            opts.registerCustomOpLibrary(OrtxPackage.getLibraryPath())
            ortSession = ortEnv.createSession(modelPath, opts)
            true
        }.getOrDefault(false)
    }

    suspend fun clusterArticles(articles: List<Article>, threshold: Float): List<ArticleCluster> =
        withContext(Dispatchers.Default) {
            val session = ortSession ?: return@withContext articles.map { ArticleCluster(it, emptyList()) }
            if (articles.size < 2) return@withContext articles.map { ArticleCluster(it, emptyList()) }

            val embeddingsWithUpdates = coroutineScope {
                articles.map { article ->
                    async {
                        val cached = embeddingCache[article.url]
                        if (cached != null) return@async article to null

                        val stored = article.embedding?.let { toFloatArray(it) }
                        if (stored != null) {
                            val normalized = normalize(stored)
                            embeddingCache[article.url] = normalized
                            return@async article to null
                        }

                        val raw = getEmbedding(session, article.title)
                        val normalized = normalize(raw)
                        embeddingCache[article.url] = normalized
                        article to article.copy(embedding = toByteArray(raw))
                    }
                }.awaitAll()
            }

            val toUpdate = embeddingsWithUpdates.mapNotNull { it.second }
            if (toUpdate.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    toUpdate.forEach { articleRepository.updateArticle(it) }
                }
            }

            val embeddings = articles.map { embeddingCache.getValue(it.url) }

            val n = articles.size
            val processed = BooleanArray(n) { false }
            val clusters = mutableListOf<ArticleCluster>()

            for (i in 0 until n) {
                if (processed[i]) continue
                processed[i] = true
                val duplicates = mutableListOf<Pair<Article, Float>>()
                for (j in i + 1 until n) {
                    if (processed[j]) continue
                    val sim = dotProduct(embeddings[i], embeddings[j])
                    if (sim >= threshold) {
                        duplicates.add(articles[j] to sim)
                        processed[j] = true
                    }
                }
                clusters.add(ArticleCluster(articles[i], duplicates))
            }

            clusters
        }

    suspend fun warmUpEmbeddings(
        articles: List<Article>,
        throttleMs: Long = 20L
    ) = withContext(Dispatchers.Default.limitedParallelism(2)) {
        val session = ortSession ?: return@withContext
        for (article in articles) {
            embeddingMutex.withLock {
                getOrComputeEmbedding(session, article)
            }
            if (throttleMs > 0) delay(throttleMs)
        }
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

            val clusters = mutableListOf<MutableCluster>()

            for ((index, article) in articles.withIndex()) {
                val embedding = embeddingMutex.withLock {
                    getOrComputeEmbedding(session, article)
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
                    persistSimilarities(matchedCluster.representative.id, article.id, matchedScore)
                } else {
                    clusters.add(MutableCluster(article, embedding, mutableListOf()))
                }

                if ((index + 1) % emitEvery == 0) {
                    emit(clusters.map { it.toCluster() })
                }

                if (throttleMs > 0) delay(throttleMs)
            }

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
                val repEmbedding = getOrComputeEmbedding(session, cluster.representative)
                MutableCluster(
                    representative = cluster.representative,
                    repEmbedding = repEmbedding,
                    duplicates = cluster.duplicates.toMutableList()
                )
            }.toMutableList()

            for ((index, article) in newArticles.withIndex()) {
                val embedding = embeddingMutex.withLock {
                    getOrComputeEmbedding(session, article)
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
                    persistSimilarities(matchedCluster.representative.id, article.id, matchedScore)
                } else {
                    clusters.add(MutableCluster(article, embedding, mutableListOf()))
                }

                if ((index + 1) % emitEvery == 0) {
                    emit(clusters.map { it.toCluster() })
                }

                if (throttleMs > 0) delay(throttleMs)
            }

            emit(clusters.map { it.toCluster() })
        }.flowOn(Dispatchers.Default.limitedParallelism(2))

    private suspend fun getOrComputeEmbedding(session: OrtSession, article: Article): FloatArray {
        val cached = embeddingCache[article.url]
        if (cached != null) return cached

        val stored = article.embedding?.let { toFloatArray(it) }
        if (stored != null) {
            val normalized = normalize(stored)
            embeddingCache[article.url] = normalized
            return normalized
        }

        val raw = getEmbedding(session, article.title)
        val normalized = normalize(raw)
        embeddingCache[article.url] = normalized
        withContext(Dispatchers.IO) {
            articleRepository.updateArticle(article.copy(embedding = toByteArray(raw)))
        }
        return normalized
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
                    val floats = FloatArray(buf.capacity()).also { buf.get(it) }
                    val dim = outTensor.info.shape.last().toInt()
                    if (dim == 0 || floats.isEmpty()) return@runCatching FloatArray(EMBEDDING_DIM)

                    val tokens = floats.size / dim
                    FloatArray(dim) { j ->
                        var sum = 0f
                        for (i in 0 until tokens) sum += floats[i * dim + j]
                        sum / tokens
                    }
                }
            }
        }.getOrDefault(FloatArray(EMBEDDING_DIM))
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

    fun close() {
        ortSession?.close()
        ortSession = null
        embeddingCache.clear()
    }

    private data class MutableCluster(
        val representative: Article,
        val repEmbedding: FloatArray,
        val duplicates: MutableList<Pair<Article, Float>>
    ) {
        fun toCluster(): ArticleCluster = ArticleCluster(representative, duplicates.toList())
    }

    private suspend fun persistSimilarities(representativeId: Long, articleId: Long, score: Float) {
        withContext(Dispatchers.IO) {
            articleRepository.upsertSimilarities(
                listOf(ArticleSimilarity(representativeId, articleId, score))
            )
        }
    }

    companion object {
        private const val EMBEDDING_DIM = 768
    }
}
