package com.andrewwin.sumup.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import com.andrewwin.sumup.data.local.dao.ArticleDao
import com.andrewwin.sumup.data.local.dao.ArticleSimilarityDao
import com.andrewwin.sumup.data.local.dao.SavedArticleDao
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.dao.UserPreferencesDao
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.ArticleSimilarity
import com.andrewwin.sumup.data.local.entities.SavedArticle
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.RemoteArticleDataSource
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.usecase.common.CleanArticleTextUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ArticleRepositoryImpl @Inject constructor(
    private val articleDao: ArticleDao,
    private val articleSimilarityDao: ArticleSimilarityDao,
    private val savedArticleDao: SavedArticleDao,
    private val sourceDao: SourceDao,
    private val userPreferencesDao: UserPreferencesDao,
    private val remoteArticleDataSource: RemoteArticleDataSource,
    private val cleanArticleTextUseCase: CleanArticleTextUseCase
) : ArticleRepository {

    override val enabledArticles: Flow<List<Article>> =
        articleDao.getEnabledArticles()
    override val allArticles: Flow<List<Article>> =
        articleDao.getAllArticles()
    override val favoriteArticles: Flow<List<Article>> =
        savedArticleDao.getSavedArticles().map { list -> list.map(::mapSavedToUiArticle) }

    private val _dataInvalidationSignal = MutableStateFlow(0L)
    override val dataInvalidationSignal: Flow<Long> = _dataInvalidationSignal.asStateFlow()

    override fun triggerDataInvalidation() {
        _dataInvalidationSignal.value = System.currentTimeMillis()
    }

    override suspend fun refreshArticles() = withContext(Dispatchers.IO) {
        val groups = sourceDao.getGroupsWithSources().first()
        for (groupWithSources in groups) {
            if (groupWithSources.group.isEnabled) {
                for (source in groupWithSources.sources) {
                    if (source.isEnabled) {
                        val fetchedArticles = remoteArticleDataSource.fetchArticles(source)

                        if (fetchedArticles.isNotEmpty()) {
                            val contentsForFooter = fetchedArticles.take(10).map { it.content }
                            val newFooterPattern = cleanArticleTextUseCase(contentsForFooter)

                            if (newFooterPattern != null && newFooterPattern != source.footerPattern) {
                                sourceDao.updateSource(source.copy(footerPattern = newFooterPattern))
                            }

                            val currentFooter = newFooterPattern ?: source.footerPattern
                            val cleanedArticles = mutableListOf<Article>()
                            for (article in fetchedArticles) {
                                val cleanedContent = cleanArticleTextUseCase(article.content, source.type, currentFooter)
                                cleanedArticles.add(article.copy(content = cleanedContent))
                            }
                            
                            articleDao.insertArticles(cleanedArticles)

                            for (article in cleanedArticles) {
                                articleDao.updateFetchedArticleByUrl(
                                    sourceId = article.sourceId,
                                    title = article.title,
                                    content = article.content,
                                    mediaUrl = article.mediaUrl,
                                    videoId = article.videoId,
                                    publishedAt = article.publishedAt,
                                    viewCount = article.viewCount,
                                    url = article.url
                                )
                            }

                            for (article in cleanedArticles) {
                                if (!article.mediaUrl.isNullOrBlank() || !article.videoId.isNullOrBlank()) {
                                    articleDao.updateMediaByUrl(
                                        url = article.url,
                                        mediaUrl = article.mediaUrl,
                                        videoId = article.videoId
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        val cleanupDays = userPreferencesDao.getUserPreferences().first()?.articleAutoCleanupDays ?: 3
        val cutoffTimestamp = System.currentTimeMillis() - (cleanupDays.toLong() * 24 * 60 * 60 * 1000L)
        val newerCount = articleDao.countArticlesNewerThan(cutoffTimestamp)
        if (newerCount > 0) {
            articleDao.deleteOldArticles(cutoffTimestamp)
        }
        Unit
    }

    override suspend fun updateArticle(article: Article) = articleDao.updateArticle(article)

    override suspend fun updateArticles(articles: List<Article>) {
        if (articles.isEmpty()) return
        articleDao.updateArticles(articles)
    }

    override suspend fun setFavoriteByIds(ids: List<Long>, isFavorite: Boolean): Int {
        if (ids.isEmpty()) return 0
        val distinctIds = ids.distinct()
        val savedIds = distinctIds.mapNotNull(::uiArticleIdToSavedId)
        val articleIds = distinctIds.filter { it > 0L }

        return if (isFavorite) {
            val rows = articleDao.getArticlesWithMetaByIds(articleIds)
            if (rows.isNotEmpty()) {
                val now = System.currentTimeMillis()
                savedArticleDao.upsert(
                    rows.map { row ->
                        SavedArticle(
                            url = row.url,
                            title = row.title,
                            content = row.content,
                            mediaUrl = row.mediaUrl,
                            videoId = row.videoId,
                            publishedAt = row.publishedAt,
                            viewCount = row.viewCount,
                            sourceName = row.sourceName,
                            groupName = row.groupName,
                            savedAt = now
                        )
                    }
                )
            }
            rows.size
        } else {
            var deleted = 0
            if (savedIds.isNotEmpty()) {
                deleted += savedArticleDao.deleteByIds(savedIds)
            }
            if (articleIds.isNotEmpty()) {
                val rows = articleDao.getArticlesWithMetaByIds(articleIds)
                if (rows.isNotEmpty()) {
                    deleted += savedArticleDao.deleteByUrls(rows.map { it.url })
                }
            }
            deleted
        }
    }

    override suspend fun getEmbeddingsByIds(ids: List<Long>): Map<Long, ByteArray?> {
        if (ids.isEmpty()) return emptyMap()
        return articleDao.getEmbeddingsByIds(ids).associate { it.id to it.embedding }
    }

    override suspend fun getArticleEmbeddingsByIds(ids: List<Long>): List<com.andrewwin.sumup.data.local.dao.ArticleEmbedding> {
        if (ids.isEmpty()) return emptyList()
        return articleDao.getEmbeddingsByIds(ids)
    }

    override suspend fun getEnabledArticlesOnce(): List<Article> = articleDao.getEnabledArticlesOnce()

    override suspend fun getEnabledArticlesSince(timestamp: Long): List<Article> =
        articleDao.getEnabledArticlesSince(timestamp)

    override suspend fun getSourceById(id: Long): com.andrewwin.sumup.data.local.entities.Source? =
        sourceDao.getSourceById(id)

    override suspend fun fetchFullContent(article: Article): String {
        val source = sourceDao.getSourceById(article.sourceId) ?: return article.content
        val fetchedRemote = remoteArticleDataSource.fetchFullContent(article.url, source.type)
        val remoteContent = fetchedRemote ?: article.content
        val mainContent = cleanArticleTextUseCase.extractMainContent(article.url, remoteContent, source.type)
        val cleaned = cleanArticleTextUseCase(mainContent, source.type, source.footerPattern)
        return cleaned
    }

    override suspend fun getSimilaritiesForArticles(articleIds: List<Long>): List<ArticleSimilarity> =
        articleSimilarityDao.getSimilaritiesForArticles(articleIds)

    override suspend fun upsertSimilarities(items: List<ArticleSimilarity>) {
        if (items.isEmpty()) return

        val relatedIds = items.asSequence()
            .flatMap { sequenceOf(it.representativeId, it.articleId) }
            .toSet()
        if (relatedIds.isEmpty()) return

        val existingIds = articleDao.getExistingArticleIds(relatedIds.toList()).toHashSet()
        if (existingIds.isEmpty()) return

        val validItems = items.filter { similarity ->
            similarity.representativeId in existingIds && similarity.articleId in existingIds
        }
        if (validItems.isEmpty()) return

        try {
            articleSimilarityDao.upsertSimilarities(validItems)
        } catch (_: SQLiteConstraintException) {
            // Race condition guard: one of related articles could be deleted between validation and insert.
        }
    }

    override suspend fun clearAllArticles() {
        articleSimilarityDao.deleteAll()
        articleDao.deleteAllArticles()
    }

    override suspend fun clearEmbeddings() {
        articleSimilarityDao.deleteAll()
        articleDao.clearEmbeddings()
    }

    override suspend fun clearSimilarities() {
        articleSimilarityDao.deleteAll()
    }

    override suspend fun clearOldArticlesByAge(days: Int) {
        val safeDays = days.coerceAtLeast(1)
        val cutoffTimestamp = System.currentTimeMillis() - (safeDays.toLong() * 24 * 60 * 60 * 1000L)
        val newerCount = articleDao.countArticlesNewerThan(cutoffTimestamp)
        if (newerCount > 0) {
            articleDao.deleteOldArticles(cutoffTimestamp)
        }
    }

    override suspend fun getFavoriteArticleUrls(): List<String> = savedArticleDao.getSavedUrls()

    override suspend fun replaceFavoriteArticlesByUrls(urls: List<String>) {
        savedArticleDao.deleteAll()
        upsertSavedByUrls(urls)
    }

    override suspend fun mergeFavoriteArticlesByUrls(urls: List<String>) {
        upsertSavedByUrls(urls)
    }

    override suspend fun getSavedArticlesSnapshot(): List<SavedArticle> =
        savedArticleDao.getSavedArticlesOnce()

    override suspend fun replaceSavedArticlesSnapshot(items: List<SavedArticle>) {
        savedArticleDao.deleteAll()
        if (items.isNotEmpty()) {
            savedArticleDao.upsert(
                items.map { item ->
                    item.copy(
                        id = 0,
                        url = item.url.trim()
                    )
                }.filter { it.url.isNotBlank() }
            )
        }
    }

    override suspend fun mergeSavedArticlesSnapshot(items: List<SavedArticle>) {
        if (items.isEmpty()) return
        savedArticleDao.upsert(
            items.map { item ->
                item.copy(
                    id = 0,
                    url = item.url.trim()
                )
            }.filter { it.url.isNotBlank() }
        )
    }

    override suspend fun saveFavoriteClusterMapping(articleIds: List<Long>, clusterKey: String?) {
        if (articleIds.isEmpty()) return
        val normalizedKey = clusterKey?.trim().orEmpty()
        val savedIds = articleIds.distinct().mapNotNull(::uiArticleIdToSavedId)
        if (savedIds.isNotEmpty()) {
            savedArticleDao.updateClusterKeyByIds(savedIds, normalizedKey.takeIf { it.isNotBlank() })
        } else {
            val rows = articleDao.getArticlesWithMetaByIds(articleIds.filter { it > 0L })
            if (rows.isNotEmpty()) {
                savedArticleDao.updateClusterKeyByUrls(
                    rows.map { it.url },
                    normalizedKey.takeIf { it.isNotBlank() }
                )
            }
        }
    }

    override suspend fun clearFavoriteClusterMapping(articleIds: List<Long>) {
        saveFavoriteClusterMapping(articleIds, null)
    }

    override suspend fun getFavoriteClusterMappings(articleIds: List<Long>): Map<Long, String> {
        if (articleIds.isEmpty()) return emptyMap()
        val savedById = savedArticleDao.getSavedArticlesOnce().associateBy { savedArticleUiId(it.id) }
        return buildMap {
            articleIds.distinct().forEach { uiId ->
                savedById[uiId]?.clusterKey?.takeIf { it.isNotBlank() }?.let { put(uiId, it) }
            }
        }
    }

    override suspend fun saveFavoriteSavedAt(articleIds: List<Long>, savedAtMillis: Long) {
        // savedAt is persisted in saved_articles at insert time, no separate action needed
    }

    override suspend fun clearFavoriteSavedAt(articleIds: List<Long>) {
        // savedAt is deleted together with saved rows
    }

    override suspend fun getFavoriteSavedAt(articleIds: List<Long>): Map<Long, Long> {
        if (articleIds.isEmpty()) return emptyMap()
        val savedById = savedArticleDao.getSavedArticlesOnce().associateBy { savedArticleUiId(it.id) }
        return buildMap {
            articleIds.distinct().forEach { uiId ->
                val savedAt = savedById[uiId]?.savedAt ?: 0L
                if (savedAt > 0L) put(uiId, savedAt)
            }
        }
    }

    override suspend fun saveFavoriteClusterScores(scoresByArticleId: Map<Long, Float>) {
        if (scoresByArticleId.isEmpty()) return
        val positiveIds = scoresByArticleId.keys.filter { it > 0L }
        val urlByArticleId = if (positiveIds.isEmpty()) {
            emptyMap()
        } else {
            articleDao.getArticlesWithMetaByIds(positiveIds).associate { it.id to it.url }
        }
        scoresByArticleId.forEach { (articleId, rawScore) ->
            val score = rawScore.coerceIn(0f, 1f)
            val savedId = uiArticleIdToSavedId(articleId)
            if (savedId != null) {
                savedArticleDao.updateClusterScoreById(savedId, score)
            } else {
                urlByArticleId[articleId]?.let { url ->
                    savedArticleDao.updateClusterScoreByUrl(url, score)
                }
            }
        }
    }

    override suspend fun getFavoriteClusterScores(articleIds: List<Long>): Map<Long, Float> {
        if (articleIds.isEmpty()) return emptyMap()
        val savedById = savedArticleDao.getSavedArticlesOnce().associateBy { savedArticleUiId(it.id) }
        return buildMap {
            articleIds.distinct().forEach { uiId ->
                savedById[uiId]?.clusterScore?.let { score ->
                    if (score > 0f) put(uiId, score)
                }
            }
        }
    }

    override suspend fun getFavoriteSimilarities(articleIds: List<Long>): List<ArticleSimilarity> {
        if (articleIds.isEmpty()) return emptyList()

        val requestedUiIds = articleIds.distinct()
        val savedByUiId = savedArticleDao.getSavedArticlesOnce().associateBy { savedArticleUiId(it.id) }
        val targetSaved = requestedUiIds.mapNotNull { uiId ->
            savedByUiId[uiId]?.let { uiId to it }
        }
        if (targetSaved.isEmpty()) return emptyList()

        val savedUrlByUiId = targetSaved.associate { (uiId, saved) -> uiId to saved.url }
        val allArticles = articleDao.getAllArticlesOnce()
        val articleIdsByCanonical = allArticles
            .mapNotNull { article ->
                canonicalizeUrl(article.url)?.let { canonical -> canonical to article.id }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })

        val resolvedArticleIdsByUiId = savedUrlByUiId.mapValues { (_, url) ->
            val canonical = canonicalizeUrl(url) ?: return@mapValues emptyList<Long>()
            articleIdsByCanonical[canonical].orEmpty().distinct()
        }.filterValues { it.isNotEmpty() }
        if (resolvedArticleIdsByUiId.isEmpty()) return emptyList()

        val allResolvedArticleIds = resolvedArticleIdsByUiId.values.flatten().distinct()
        val rawSimilarities = articleSimilarityDao.getSimilaritiesForArticles(allResolvedArticleIds)
        if (rawSimilarities.isEmpty()) return emptyList()

        val uiIdsByArticleId = mutableMapOf<Long, MutableSet<Long>>()
        resolvedArticleIdsByUiId.forEach { (uiId, resolvedIds) ->
            resolvedIds.forEach { articleId ->
                uiIdsByArticleId.getOrPut(articleId) { mutableSetOf() }.add(uiId)
            }
        }

        val mergedScores = mutableMapOf<Pair<Long, Long>, Float>()
        rawSimilarities.forEach { sim ->
            val leftUiIds = uiIdsByArticleId[sim.representativeId].orEmpty()
            val rightUiIds = uiIdsByArticleId[sim.articleId].orEmpty()
            if (leftUiIds.isEmpty() || rightUiIds.isEmpty()) return@forEach

            leftUiIds.forEach { leftUi ->
                rightUiIds.forEach { rightUi ->
                    if (leftUi == rightUi) return@forEach
                    val key = leftUi to rightUi
                    val prev = mergedScores[key]
                    if (prev == null || sim.score > prev) {
                        mergedScores[key] = sim.score
                    }
                }
            }
        }

        return mergedScores.map { (pair, score) ->
            ArticleSimilarity(
                representativeId = pair.first,
                articleId = pair.second,
                score = score
            )
        }
    }

    private fun canonicalizeUrl(rawUrl: String): String? {
        val value = rawUrl.trim()
        if (value.isBlank()) return null
        return runCatching {
            val uri = Uri.parse(value)
            val host = uri.host?.lowercase().orEmpty()
            val path = uri.path.orEmpty()

            if (host.contains("youtube.com") || host.contains("youtu.be")) {
                val videoId = when {
                    host.contains("youtu.be") -> path.trim('/').substringBefore('/')
                    path.startsWith("/shorts/") -> path.removePrefix("/shorts/").substringBefore('/')
                    else -> uri.getQueryParameter("v").orEmpty()
                }.trim()
                if (videoId.isNotBlank()) return "youtube:$videoId"
            }

            val filteredQuery = uri.queryParameterNames
                .asSequence()
                .filterNot { key ->
                    key.startsWith("utm_") ||
                        key == "fbclid" ||
                        key == "gclid" ||
                        key == "si" ||
                        key == "feature"
                }
                .sorted()
                .flatMap { key ->
                    uri.getQueryParameters(key)
                        .asSequence()
                        .map { valuePart -> key to valuePart.trim() }
                        .filter { it.second.isNotEmpty() }
                }
                .joinToString("&") { (key, valuePart) -> "$key=$valuePart" }

            val normalizedPath = path.trimEnd('/').ifBlank { "/" }
            buildString {
                append(host)
                append(normalizedPath)
                if (filteredQuery.isNotBlank()) {
                    append("?")
                    append(filteredQuery)
                }
            }
        }.getOrNull()
    }

    private suspend fun upsertSavedByUrls(urls: List<String>) {
        if (urls.isEmpty()) return
        val canonicalToInput = urls
            .mapNotNull { raw -> canonicalizeUrl(raw)?.let { it to raw.trim() } }
            .toMap()
        val rows = articleDao.getAllArticlesOnce()
        val rowsByCanonical = rows
            .mapNotNull { article ->
                canonicalizeUrl(article.url)?.let { it to article }
            }
            .toMap()
        val sourceById = sourceDao.getGroupsWithSourcesOnce()
            .flatMap { groupWithSources ->
                groupWithSources.sources.map { source ->
                    source.id to (source.name to groupWithSources.group.name)
                }
            }
            .toMap()
        val now = System.currentTimeMillis()
        val toUpsert = canonicalToInput.mapNotNull { (canonical, inputUrl) ->
            val matched = rowsByCanonical[canonical]
            if (matched != null) {
                val sourceMeta = sourceById[matched.sourceId]
                SavedArticle(
                    url = matched.url,
                    title = matched.title,
                    content = matched.content,
                    mediaUrl = matched.mediaUrl,
                    videoId = matched.videoId,
                    publishedAt = matched.publishedAt,
                    viewCount = matched.viewCount,
                    sourceName = sourceMeta?.first,
                    groupName = sourceMeta?.second,
                    savedAt = now
                )
            } else {
                SavedArticle(
                    url = inputUrl,
                    title = buildFallbackTitle(inputUrl),
                    content = inputUrl,
                    mediaUrl = null,
                    videoId = null,
                    publishedAt = now,
                    viewCount = 0,
                    sourceName = null,
                    groupName = null,
                    savedAt = now
                )
            }
        }
        if (toUpsert.isNotEmpty()) {
            savedArticleDao.upsert(toUpsert)
        }
    }

    private fun buildFallbackTitle(rawUrl: String): String =
        runCatching {
            val uri = Uri.parse(rawUrl)
            val host = uri.host?.removePrefix("www.")?.ifBlank { null }
            val path = uri.path?.trim('/')?.substringBefore('/')?.ifBlank { null }
            listOfNotNull(host, path).joinToString(" • ").ifBlank { rawUrl }
        }.getOrDefault(rawUrl)

    private fun mapSavedToUiArticle(item: SavedArticle): Article =
        Article(
            id = savedArticleUiId(item.id),
            sourceId = 0L,
            title = item.title,
            content = item.content,
            mediaUrl = item.mediaUrl,
            videoId = item.videoId,
            url = item.url,
            publishedAt = item.publishedAt,
            viewCount = item.viewCount,
            isRead = false,
            isFavorite = true,
            embedding = null
        )

    private fun savedArticleUiId(savedId: Long): Long = -(savedId + 1L)
    private fun uiArticleIdToSavedId(uiId: Long): Long? =
        if (uiId < 0L) (-uiId) - 1L else null
}






