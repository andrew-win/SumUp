package com.andrewwin.sumup.domain.service

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.ArticleSimilarity
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.usecase.ai.GenerateCloudEmbeddingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    private val generateCloudEmbeddingUseCase: GenerateCloudEmbeddingUseCase
) {
    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    private val ortThreadCount: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

    suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (ortSession != null) return@withContext true

            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
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
            true
        }.onFailure { e ->
            android.util.Log.e(TAG, "Failed to initialize ONNX session", e)
        }.getOrDefault(false)
    }

    private fun normalizeTitle(title: String): String {
        return title.lowercase().replace(WHITESPACE_REGEX, " ").trim()
    }

    private suspend fun resolveEmbedding(article: Article): EmbeddingResult {
        article.embedding?.let {
            return EmbeddingResult(EmbeddingUtils.toFloatArray(it), null)
        }

        val cloudEmbedding = generateCloudEmbeddingUseCase(article.title)
        if (cloudEmbedding != null) {
            val normalized = normalize(cloudEmbedding)
            return EmbeddingResult(
                embedding = normalized,
                updatedArticle = article.copy(
                    embedding = EmbeddingUtils.toByteArray(normalized)
                )
            )
        }

        val session = ortSession ?: return EmbeddingResult(FloatArray(EMBEDDING_DIM), null)
        val raw = computeLocalEmbedding(session, article.title)
        val normalized = normalize(resizeEmbedding(raw))

        return EmbeddingResult(
            embedding = normalized,
            updatedArticle = article.copy(
                embedding = EmbeddingUtils.toByteArray(normalized)
            )
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

                    if (dim == 0 || buf.capacity() == 0) {
                        return@runCatching FloatArray(EMBEDDING_DIM)
                    }

                    val tokens = (buf.capacity() / dim).coerceAtLeast(1)
                    val pooled = FloatArray(dim)

                    for (i in 0 until tokens) {
                        for (j in 0 until dim) {
                            pooled[j] += buf.get()
                        }
                    }

                    for (j in pooled.indices) {
                        pooled[j] /= tokens
                    }

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

        val storedEmbeddingsById = articleRepository.getEmbeddingsByIds(
            existingArticles.map { it.id }
        )

        val clusters = existingClusters.map { cluster ->
            val repEmbedding = resolveExistingEmbedding(
                article = cluster.representative,
                titleEmbeddingCache = titleEmbeddingCache,
                storedEmbeddingsById = storedEmbeddingsById
            )

            val memberEmbeddings = mutableMapOf<Long, FloatArray>(
                cluster.representative.id to repEmbedding
            )

            val memberArticles = mutableMapOf<Long, Article>(
                cluster.representative.id to cluster.representative
            )

            cluster.duplicates.forEach { (article, _) ->
                val duplicateEmbedding = resolveExistingEmbedding(
                    article = article,
                    titleEmbeddingCache = titleEmbeddingCache,
                    storedEmbeddingsById = storedEmbeddingsById
                )

                memberEmbeddings[article.id] = duplicateEmbedding
                memberArticles[article.id] = article
            }

            MutableCluster(
                representative = cluster.representative,
                repEmbedding = repEmbedding,
                duplicates = cluster.duplicates.toMutableList(),
                memberEmbeddings = memberEmbeddings,
                memberArticles = memberArticles
            )
        }.toMutableList()

        processArticlesIntoClusters(newArticles, clusters, threshold, emitEvery, throttleMs)
        emit(clusters.map { it.toCluster() })
    }.flowOn(Dispatchers.Default)

    private fun resolveExistingEmbedding(
        article: Article,
        titleEmbeddingCache: MutableMap<String, FloatArray>,
        storedEmbeddingsById: Map<Long, ByteArray?>
    ): FloatArray {
        val embeddingBytes = article.embedding ?: storedEmbeddingsById[article.id]
        val embedding = embeddingBytes?.let(EmbeddingUtils::toFloatArray)
            ?: FloatArray(EMBEDDING_DIM)

        if (embeddingBytes != null) {
            titleEmbeddingCache[normalizeTitle(article.title)] = embedding
        }

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

        val storedEmbeddingsById = articleRepository.getEmbeddingsByIds(
            articles.map { it.id }
        )

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
                        pendingArticleUpdates.add(
                            article.copy(
                                embedding = EmbeddingUtils.toByteArray(cached)
                            )
                        )
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

                    if (
                        index > 0 &&
                        article.embedding == null &&
                        storedEmbeddingsById[article.id] == null
                    ) {
                        pendingArticleUpdates.add(
                            article.copy(
                                embedding = EmbeddingUtils.toByteArray(result.embedding)
                            )
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
        val titleEmbeddingCache = mutableMapOf<String, FloatArray>()
        val articlesById = linkedMapOf<Long, Article>()
        val embeddingsById = mutableMapOf<Long, FloatArray>()

        clusters.forEach { cluster ->
            articlesById[cluster.representative.id] = cluster.representative
            embeddingsById[cluster.representative.id] = cluster.repEmbedding
            titleEmbeddingCache[normalizeTitle(cluster.representative.title)] = cluster.repEmbedding

            cluster.duplicates.forEach { (article, _) ->
                articlesById[article.id] = article
            }

            cluster.memberArticles.forEach { (articleId, article) ->
                articlesById[articleId] = article
            }

            cluster.memberEmbeddings.forEach { (articleId, embedding) ->
                embeddingsById[articleId] = embedding

                articlesById[articleId]?.let { article ->
                    titleEmbeddingCache[normalizeTitle(article.title)] = embedding
                }
            }
        }

        articles.forEach { article ->
            articlesById[article.id] = article
        }

        val incomingArticlesToResolve = articles.distinctBy { it.id }

        val (resolvedIncomingEmbeddings, pendingArticleUpdates) = precomputeEmbeddings(
            articles = incomingArticlesToResolve,
            titleEmbeddingCache = titleEmbeddingCache
        )

        embeddingsById.putAll(resolvedIncomingEmbeddings)

        val allArticles = articlesById.values.toList()
        val pairScores = mutableMapOf<ArticlePairKey, Float>()

        var totalComparisons = 0L
        var thresholdMatches = 0L
        var highestAdjustedScore = Float.NEGATIVE_INFINITY
        var highestRawScore = Float.NEGATIVE_INFINITY
        var highestJaccard = 0f
        var highestJaccardBonus = 0f
        var highestEntityBonus = 0f
        var highestEntityMismatchPenalty = 0f
        var highestPair: Pair<Article, Article>? = null

        for (i in 0 until allArticles.lastIndex) {
            val left = allArticles[i]
            val leftEmbedding = embeddingsById[left.id] ?: FloatArray(EMBEDDING_DIM)

            if (isZeroVector(leftEmbedding)) {
                if (throttleMs > 0) delay(throttleMs)
                continue
            }

            for (j in i + 1 until allArticles.size) {
                val right = allArticles[j]
                val rightEmbedding = embeddingsById[right.id] ?: FloatArray(EMBEDDING_DIM)

                if (rightEmbedding.size != leftEmbedding.size) continue
                if (isZeroVector(rightEmbedding)) continue

                totalComparisons++

                val rawScore = dotProduct(leftEmbedding, rightEmbedding)
                val score = calculateSimilarityScore(
                    leftTitle = left.title,
                    rightTitle = right.title,
                    rawScore = rawScore
                )

                if (score.adjustedScore > highestAdjustedScore) {
                    highestAdjustedScore = score.adjustedScore
                    highestRawScore = score.rawScore
                    highestJaccard = score.jaccard
                    highestJaccardBonus = score.jaccardBonus
                    highestEntityBonus = score.entityBonus
                    highestEntityMismatchPenalty = score.entityMismatchPenalty
                    highestPair = left to right
                }

                if (score.adjustedScore >= threshold) {
                    thresholdMatches++
                    pairScores[ArticlePairKey.of(left.id, right.id)] = score.adjustedScore
                }
            }

            if (emitEvery > 0 && (i + 1) % emitEvery == 0) {
                val partialClusters = buildRepresentativeGatedClusters(
                    articles = allArticles,
                    embeddingsById = embeddingsById,
                    pairScores = pairScores
                )
                emit(partialClusters.map { it.toCluster() })
            }

            if (pendingArticleUpdates.size >= DB_BATCH_SIZE) {
                flushPendingArticleUpdates(pendingArticleUpdates)
            }

            if (throttleMs > 0) {
                delay(throttleMs)
            }
        }

        val rebuiltClusters = buildRepresentativeGatedClusters(
            articles = allArticles,
            embeddingsById = embeddingsById,
            pairScores = pairScores
        )

        clusters.clear()
        clusters.addAll(rebuiltClusters)

        val similarities = buildSimilaritiesFromClusters(clusters)

        similarities.chunked(DB_BATCH_SIZE).forEach { batch ->
            articleRepository.upsertSimilarities(batch)
        }

        flushPendingArticleUpdates(pendingArticleUpdates)
    }

    private fun calculateSimilarityScore(
        leftTitle: String,
        rightTitle: String,
        rawScore: Float
    ): SimilarityScore {
        val jaccard = titleJaccard(leftTitle, rightTitle)
        val jaccardBonus = calculateJaccardBonus(jaccard)

        val leftEntities = titleEntityPrefixes(leftTitle)
        val rightEntities = titleEntityPrefixes(rightTitle)
        val commonEntities = leftEntities.intersect(rightEntities)

        val entityBonus = if (commonEntities.isNotEmpty()) {
            ENTITY_MATCH_BONUS
        } else {
            0f
        }

        val entityMismatchPenalty = if (
            leftEntities.isNotEmpty() &&
            rightEntities.isNotEmpty() &&
            commonEntities.isEmpty()
        ) {
            ENTITY_MISMATCH_PENALTY
        } else {
            0f
        }

        val adjustedScore = (
                rawScore +
                        jaccardBonus +
                        entityBonus -
                        entityMismatchPenalty
                ).coerceIn(0f, 1f)

        return SimilarityScore(
            rawScore = rawScore,
            jaccard = jaccard,
            jaccardBonus = jaccardBonus,
            entityBonus = entityBonus,
            entityMismatchPenalty = entityMismatchPenalty,
            adjustedScore = adjustedScore
        )
    }

    private fun calculateJaccardBonus(jaccard: Float): Float {
        if (jaccard < MIN_JACCARD_TO_COUNT) {
            return 0f
        }

        return (jaccard - JACCARD_OFFSET)
            .coerceAtLeast(0f)
            .coerceAtMost(JACCARD_BONUS_CAP)
    }

    private fun allTitleWords(title: String): List<String> {
        return ENTITY_WORD_REGEX.findAll(title)
            .map { it.value }
            .toList()
    }

    private fun cleanEntityWord(word: String): String {
        return word
            .trim()
            .replace("ʼ", "")
            .replace("’", "")
            .replace("'", "")
            .replace("-", "")
    }

    private fun isCapitalizedWord(word: String): Boolean {
        return word.firstOrNull()?.isUpperCase() == true
    }

    private fun isAbbreviation(word: String): Boolean {
        val cleaned = cleanEntityWord(word)

        if (cleaned.length < MIN_ABBREVIATION_LENGTH) {
            return false
        }

        var hasLetter = false

        for (char in cleaned) {
            if (char.isLetter()) {
                hasLetter = true

                if (!char.isUpperCase()) {
                    return false
                }
            }
        }

        return hasLetter
    }

    private fun titleEntityPrefixes(title: String): Set<String> {
        val words = allTitleWords(title)

        if (words.isEmpty()) {
            return emptySet()
        }

        val result = mutableSetOf<String>()
        var index = 0

        while (index < words.size) {
            val word = words[index]

            // Перше слово заголовка пропускаємо тільки якщо це НЕ абревіатура.
            // Якщо заголовок починається з "США", "ЄС", "НАТО" — це сутність.
            if (index == 0 && !isAbbreviation(word)) {
                index++
                continue
            }

            // Абревіатури — окремі сутності.
            // Не склеюємо "НАТО ЄС" в одну сутність.
            if (isAbbreviation(word)) {
                val entityKey = normalizeEntityWords(listOf(word))

                if (entityKey != null) {
                    result.add(entityKey)
                }

                index++
                continue
            }

            if (!isCapitalizedWord(word)) {
                index++
                continue
            }

            val entityWords = mutableListOf(word)
            var nextIndex = index + 1

            // Кілька звичайних слів з великої літери підряд — одна сутність:
            // "Сполучені Штати" -> "спо_штат".
            // Якщо наступне слово абревіатура, не приклеюємо його.
            while (nextIndex < words.size) {
                val nextWord = words[nextIndex]

                if (isAbbreviation(nextWord)) {
                    break
                }

                if (!isCapitalizedWord(nextWord)) {
                    break
                }

                entityWords.add(nextWord)
                nextIndex++
            }

            val entityKey = normalizeEntityWords(entityWords)

            if (entityKey != null) {
                result.add(entityKey)
            }

            index = nextIndex
        }

        return result
    }

    private fun normalizeEntityWords(words: List<String>): String? {
        val normalizedParts = words.mapIndexedNotNull { index, word ->
            val cleaned = cleanEntityWord(word)
            val normalized = cleaned.lowercase()

            // Абревіатури не відсікаємо по довжині і не обрізаємо до 3-4 символів:
            // ЄС -> єс
            // США -> сша
            // РФ -> рф
            // НАТО -> нато
            // ДТЕК -> дтек
            if (isAbbreviation(cleaned)) {
                return@mapIndexedNotNull normalized
            }

            if (normalized.length < MIN_ENTITY_WORD_LENGTH) {
                return@mapIndexedNotNull null
            }

            val prefixLength = if (words.size > 1 && index == 0) {
                ENTITY_FIRST_PREFIX_LENGTH
            } else {
                ENTITY_PREFIX_LENGTH
            }

            normalized.take(prefixLength)
        }

        if (normalizedParts.isEmpty()) {
            return null
        }

        return normalizedParts.joinToString(separator = "_")
    }

    private fun buildRepresentativeGatedClusters(
        articles: List<Article>,
        embeddingsById: Map<Long, FloatArray>,
        pairScores: Map<ArticlePairKey, Float>
    ): List<MutableCluster> {
        val assignedArticleIds = mutableSetOf<Long>()
        val result = mutableListOf<MutableCluster>()

        for (representative in articles) {
            if (representative.id in assignedArticleIds) continue

            assignedArticleIds.add(representative.id)

            val repEmbedding = embeddingsById[representative.id] ?: FloatArray(EMBEDDING_DIM)
            val duplicates = mutableListOf<Pair<Article, Float>>()
            val memberArticles = mutableMapOf<Long, Article>(
                representative.id to representative
            )
            val memberEmbeddings = mutableMapOf<Long, FloatArray>(
                representative.id to repEmbedding
            )

            for (candidate in articles) {
                if (candidate.id == representative.id) continue
                if (candidate.id in assignedArticleIds) continue

                val scoreToRepresentative = pairScores[
                    ArticlePairKey.of(representative.id, candidate.id)
                ] ?: continue

                duplicates.add(candidate to scoreToRepresentative)
                assignedArticleIds.add(candidate.id)

                memberArticles[candidate.id] = candidate
                memberEmbeddings[candidate.id] = embeddingsById[candidate.id]
                    ?: FloatArray(EMBEDDING_DIM)
            }

            result.add(
                MutableCluster(
                    representative = representative,
                    repEmbedding = repEmbedding,
                    duplicates = duplicates,
                    memberEmbeddings = memberEmbeddings,
                    memberArticles = memberArticles
                )
            )
        }

        return result
    }

    private fun buildSimilaritiesFromClusters(
        clusters: List<MutableCluster>
    ): List<ArticleSimilarity> {
        val similarities = mutableListOf<ArticleSimilarity>()

        clusters.forEach { cluster ->
            cluster.duplicates.forEach { (article, score) ->
                if (score.isFinite()) {
                    similarities.add(
                        ArticleSimilarity(
                            cluster.representative.id,
                            article.id,
                            score
                        )
                    )
                }
            }
        }

        return similarities
    }

    private fun titleJaccard(leftTitle: String, rightTitle: String): Float {
        val leftTokens = importantTitleTokens(leftTitle)
        val rightTokens = importantTitleTokens(rightTitle)

        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0f
        }

        val intersectionSize = leftTokens.intersect(rightTokens).size
        val unionSize = leftTokens.union(rightTokens).size

        if (unionSize == 0) {
            return 0f
        }

        return intersectionSize.toFloat() / unionSize.toFloat()
    }

    private fun importantTitleTokens(title: String): Set<String> {
        val words = allTitleWords(title)

        if (words.isEmpty()) {
            return emptySet()
        }

        return words
            .asSequence()
            .mapIndexedNotNull { index, word ->
                // Абревіатури не включаємо у Jaccard,
                // бо вони є сутностями.
                if (isAbbreviation(word)) {
                    null
                } else if (index > 0 && isCapitalizedWord(word)) {
                    // Не включаємо слова з великої літери у Jaccard,
                    // щоб сутності не давали подвійний бонус.
                    // Перше слово залишаємо, бо воно часто з великої тільки через початок речення.
                    null
                } else {
                    normalizeJaccardToken(word.lowercase())
                }
            }
            .toSet()
    }

    private fun normalizeJaccardToken(token: String): String? {
        if (token.length < MIN_TOKEN_LENGTH) {
            return null
        }

        if (token in TITLE_STOPWORDS) {
            return null
        }

        val normalized = token.dropLast(1)

        if (normalized.isBlank()) {
            return null
        }

        if (normalized in TITLE_STOPWORDS) {
            return null
        }

        return normalized
    }

    suspend fun warmUpEmbeddings(
        articles: List<Article>,
        throttleMs: Long = 0L
    ) = withContext(Dispatchers.Default) {
        val titleEmbeddingCache = mutableMapOf<String, FloatArray>()
        val (_, pendingArticleUpdates) = precomputeEmbeddings(articles, titleEmbeddingCache)
        flushPendingArticleUpdates(pendingArticleUpdates)

        if (throttleMs > 0) {
            delay(throttleMs)
        }
    }

    private fun isZeroVector(vector: FloatArray): Boolean {
        for (value in vector) {
            if (value != 0f) return false
        }
        return true
    }

    private fun normalize(vector: FloatArray): FloatArray {
        val mag = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()

        return if (mag > 0f) {
            FloatArray(vector.size) { vector[it] / mag }
        } else {
            vector
        }
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var sum = 0f

        for (i in a.indices) {
            sum += a[i] * b[i]
        }

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
        val duplicates: MutableList<Pair<Article, Float>>,
        val memberEmbeddings: MutableMap<Long, FloatArray> = mutableMapOf(
            representative.id to repEmbedding
        ),
        val memberArticles: MutableMap<Long, Article> = mutableMapOf(
            representative.id to representative
        )
    ) {
        fun toCluster(): ArticleCluster {
            return ArticleCluster(representative, duplicates.toList())
        }
    }

    private data class SimilarityScore(
        val rawScore: Float,
        val jaccard: Float,
        val jaccardBonus: Float,
        val entityBonus: Float,
        val entityMismatchPenalty: Float,
        val adjustedScore: Float
    )

    private data class ArticlePairKey(
        val firstId: Long,
        val secondId: Long
    ) {
        companion object {
            fun of(leftId: Long, rightId: Long): ArticlePairKey {
                return if (leftId <= rightId) {
                    ArticlePairKey(leftId, rightId)
                } else {
                    ArticlePairKey(rightId, leftId)
                }
            }
        }
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

        private const val MIN_TOKEN_LENGTH = 5

        private const val MIN_JACCARD_TO_COUNT = 0.05f
        private const val JACCARD_OFFSET = 0.05f
        private const val JACCARD_BONUS_CAP = 0.15f

        private const val ENTITY_MATCH_BONUS = 0.05f
        private const val ENTITY_MISMATCH_PENALTY = 0.10f
        private const val ENTITY_PREFIX_LENGTH = 4
        private const val ENTITY_FIRST_PREFIX_LENGTH = 3
        private const val MIN_ENTITY_WORD_LENGTH = 3
        private const val MIN_ABBREVIATION_LENGTH = 2

        private val WHITESPACE_REGEX = Regex("\\s+")
        private val ENTITY_WORD_REGEX = Regex("\\p{L}[\\p{L}\\p{N}ʼ'’\\-]*")

        private val TITLE_STOPWORDS = setOf("")
    }
}

object EmbeddingUtils {
    fun toByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)

        floats.forEach { buffer.putFloat(it) }

        return buffer.array()
    }

    fun toFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)

        return FloatArray(bytes.size / 4) {
            buffer.float
        }
    }
}