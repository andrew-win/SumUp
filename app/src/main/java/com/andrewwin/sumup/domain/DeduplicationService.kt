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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    private val ortThreadCount: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

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
                setExecutionMode(OrtSession.SessionOptions.ExecutionMode.PARALLEL)
                setIntraOpNumThreads(ortThreadCount)
                setInterOpNumThreads(ortThreadCount)
                setMemoryPatternOptimization(true)
                registerCustomOpLibrary(OrtxPackage.getLibraryPath())
            }
            ortSession = ortEnv.createSession(modelPath, opts)
            Log.d(TAG, "initialize: success, modelPath=$modelPath")
            true
        }.onFailure { e ->
            Log.e(TAG, "initialize: failed, modelPath=$modelPath, error=${e.message}", e)
        }.getOrDefault(false)
    }

    private fun normalizeTitle(title: String): String {
        return title.lowercase().replace(WHITESPACE_REGEX, " ").trim()
    }

    private suspend fun resolveEmbedding(
        article: Article
    ): EmbeddingResult {
        article.embedding?.let { return EmbeddingResult(EmbeddingUtils.toFloatArray(it), null) }
        val cloudEmbedding = aiRepository.embed(article.title)
        if (cloudEmbedding != null) {
            val normalized = normalize(cloudEmbedding)
            return EmbeddingResult(
                embedding = normalized,
                updatedArticle = article.copy(embedding = EmbeddingUtils.toByteArray(normalized))
            )
        }

        val session = ortSession ?: return EmbeddingResult(FloatArray(EMBEDDING_DIM), null)
        val raw = computeLocalEmbedding(session, article.title)
        val normalized = normalize(resizeEmbedding(raw))
        return EmbeddingResult(
            embedding = normalized,
            updatedArticle = article.copy(embedding = EmbeddingUtils.toByteArray(normalized))
        )
    }

    private suspend fun flushPendingArticleUpdates(pendingArticleUpdates: MutableList<Article>) {
        if (pendingArticleUpdates.isEmpty()) return
        articleRepository.updateArticles(pendingArticleUpdates.toList())
        pendingArticleUpdates.clear()
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
        throttleMs: Long = 0L
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
        throttleMs: Long = 0L
    ): Flow<List<ArticleCluster>> = flow {
        val titleEmbeddingCache = mutableMapOf<String, FloatArray>()
        val existingArticles = existingClusters.flatMap { cluster ->
            buildList {
                add(cluster.representative)
                addAll(cluster.duplicates.map { it.first })
            }
        }
        val storedEmbeddingsById = articleRepository.getEmbeddingsByIds(existingArticles.map { it.id })
        val clusters = existingClusters.map { cluster ->
            val repEmbedding = resolveExistingEmbedding(
                article = cluster.representative,
                titleEmbeddingCache = titleEmbeddingCache,
                storedEmbeddingsById = storedEmbeddingsById
            )
            MutableCluster(
                representative = cluster.representative,
                repEmbedding = repEmbedding,
                duplicates = cluster.duplicates.toMutableList()
            )
        }.toMutableList()
        existingClusters.forEach { cluster ->
            cluster.duplicates.forEach { (article, _) ->
                val embeddingBytes = article.embedding ?: storedEmbeddingsById[article.id]
                embeddingBytes?.let { titleEmbeddingCache[normalizeTitle(article.title)] = EmbeddingUtils.toFloatArray(it) }
            }
        }
        processArticlesIntoClusters(newArticles, clusters, threshold, emitEvery, throttleMs)
        emit(clusters.map { it.toCluster() })
    }.flowOn(Dispatchers.Default)

    private fun resolveExistingEmbedding(
        article: Article,
        titleEmbeddingCache: MutableMap<String, FloatArray>,
        storedEmbeddingsById: Map<Long, ByteArray?>
    ): FloatArray {
        val embeddingBytes = article.embedding ?: storedEmbeddingsById[article.id]
        val embedding = embeddingBytes?.let(EmbeddingUtils::toFloatArray) ?: FloatArray(EMBEDDING_DIM)
        if (embeddingBytes != null) titleEmbeddingCache[normalizeTitle(article.title)] = embedding
        return embedding
    }

    private suspend fun precomputeEmbeddings(
        articles: List<Article>,
        titleEmbeddingCache: MutableMap<String, FloatArray>,
        maxParallel: Int = MAX_EMBEDDING_PARALLELISM
    ): Pair<Map<Long, FloatArray>, MutableList<Article>> = coroutineScope {
        val embeddingsById = mutableMapOf<Long, FloatArray>()
        val pendingArticleUpdates = mutableListOf<Article>()
        val unresolvedGroups = mutableListOf<Pair<String, List<Article>>>()
        val storedEmbeddingsById = articleRepository.getEmbeddingsByIds(articles.map { it.id })

        for ((normalizedTitle, group) in articles.groupBy { normalizeTitle(it.title) }) {
            val cached = titleEmbeddingCache[normalizedTitle]
                ?: group.firstNotNullOfOrNull { article ->
                    article.embedding?.let(EmbeddingUtils::toFloatArray)
                        ?: storedEmbeddingsById[article.id]?.let(EmbeddingUtils::toFloatArray)
                }
            if (cached != null) {
                titleEmbeddingCache[normalizedTitle] = cached
                group.forEach { article ->
                    embeddingsById[article.id] = cached
                    if (article.embedding == null && storedEmbeddingsById[article.id] == null) {
                        pendingArticleUpdates.add(article.copy(embedding = EmbeddingUtils.toByteArray(cached)))
                    }
                }
            } else {
                unresolvedGroups.add(normalizedTitle to group)
            }
        }

        if (unresolvedGroups.isNotEmpty()) {
            val semaphore = Semaphore(maxParallel.coerceAtLeast(1))
            val resolved = unresolvedGroups.map { (normalizedTitle, group) ->
                val representative = group.first()
                async {
                    semaphore.withPermit {
                        normalizedTitle to (group to resolveEmbedding(representative))
                    }
                }
            }.awaitAll()

            for ((normalizedTitle, value) in resolved) {
                val (group, result) = value
                titleEmbeddingCache[normalizedTitle] = result.embedding
                result.updatedArticle?.let(pendingArticleUpdates::add)
                group.forEachIndexed { index, article ->
                    embeddingsById[article.id] = result.embedding
                    if (index > 0 && article.embedding == null && storedEmbeddingsById[article.id] == null) {
                        pendingArticleUpdates.add(
                            article.copy(embedding = EmbeddingUtils.toByteArray(result.embedding))
                        )
                    }
                }
            }
        }

        embeddingsById to pendingArticleUpdates
    }

    private suspend fun FlowCollector<List<ArticleCluster>>.processArticlesIntoClusters(
        articles: List<Article>,
        clusters: MutableList<MutableCluster>,
        threshold: Float,
        emitEvery: Int,
        throttleMs: Long
    ) {
        val pendingSimilarities = mutableListOf<ArticleSimilarity>()
        val titleEmbeddingCache = mutableMapOf<String, FloatArray>()
        clusters.forEach { cluster ->
            titleEmbeddingCache[normalizeTitle(cluster.representative.title)] = cluster.repEmbedding
            cluster.duplicates.forEach { (article, _) ->
                article.embedding?.let {
                    titleEmbeddingCache[normalizeTitle(article.title)] = EmbeddingUtils.toFloatArray(it)
                }
            }
        }
        val (embeddingsByArticleId, pendingArticleUpdates) = precomputeEmbeddings(articles, titleEmbeddingCache)

        for ((index, article) in articles.withIndex()) {
            val embedding = embeddingsByArticleId[article.id] ?: FloatArray(EMBEDDING_DIM)

            var matchedCluster: MutableCluster? = null
            var matchedScore = Float.NEGATIVE_INFINITY
            if (!isZeroVector(embedding)) {
                for (cluster in clusters) {
                    if (cluster.repEmbedding.size != embedding.size) continue
                    val score = dotProduct(embedding, cluster.repEmbedding)
                    if (score >= threshold && score > matchedScore) {
                        matchedScore = score
                        matchedCluster = cluster
                    }
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
            if (pendingArticleUpdates.size >= DB_BATCH_SIZE) {
                flushPendingArticleUpdates(pendingArticleUpdates)
            }

            if ((index + 1) % emitEvery == 0) {
                emit(clusters.map { it.toCluster() })
            }

            if (throttleMs > 0) delay(throttleMs)
        }

        if (pendingSimilarities.isNotEmpty()) {
            articleRepository.upsertSimilarities(pendingSimilarities)
        }
        flushPendingArticleUpdates(pendingArticleUpdates)
    }

    suspend fun warmUpEmbeddings(
        articles: List<Article>,
        throttleMs: Long = 0L
    ) = withContext(Dispatchers.Default) {
        val titleEmbeddingCache = mutableMapOf<String, FloatArray>()
        val (_, pendingArticleUpdates) = precomputeEmbeddings(articles, titleEmbeddingCache)
        flushPendingArticleUpdates(pendingArticleUpdates)
        if (throttleMs > 0) delay(throttleMs)
    }

    private fun isZeroVector(vector: FloatArray): Boolean {
        for (value in vector) {
            if (value != 0f) return false
        }
        return true
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

    private data class EmbeddingResult(
        val embedding: FloatArray,
        val updatedArticle: Article?
    )

    companion object {
        private const val TAG = "DeduplicationService"
        private const val EMBEDDING_DIM = 768
        private const val DB_BATCH_SIZE = 32
        private const val MAX_EMBEDDING_PARALLELISM = 6
        private val WHITESPACE_REGEX = Regex("\\s+")
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
