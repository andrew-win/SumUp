package com.andrewwin.sumup.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.util.Log
import com.andrewwin.sumup.data.mappers.toDomainModel
import com.andrewwin.sumup.data.mappers.toDomainRecord
import com.andrewwin.sumup.data.mappers.toDomainSnapshot
import com.andrewwin.sumup.data.mappers.toRoomEntity
import com.andrewwin.sumup.data.local.dao.ArticleDao
import com.andrewwin.sumup.data.local.dao.ArticleEmbeddingDao
import com.andrewwin.sumup.data.local.dao.ArticleSimilarityDao
import com.andrewwin.sumup.data.local.dao.SavedArticleDao
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.dao.UserPreferencesDao
import com.andrewwin.sumup.data.local.entities.ArticleSimilarity
import com.andrewwin.sumup.data.local.entities.SavedArticle
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.data.remote.sources.ArticleStableKeyFactory
import com.andrewwin.sumup.data.remote.sources.RemoteArticleDataSource
import com.andrewwin.sumup.data.remote.sources.SourceRefreshBoundary
import com.andrewwin.sumup.domain.ai.model.RemoteContentFetchStatus
import com.andrewwin.sumup.domain.article.model.Article
import com.andrewwin.sumup.domain.article.model.ArticleEmbeddingRecord
import com.andrewwin.sumup.domain.article.model.ArticleSimilarityRecord
import com.andrewwin.sumup.domain.article.model.SavedArticleSnapshot
import com.andrewwin.sumup.domain.article.processing.ArticleContentCleaner
import com.andrewwin.sumup.domain.article.processing.ArticleImportanceScorer
import com.andrewwin.sumup.domain.article.processing.ArticleTitleFormatter
import com.andrewwin.sumup.domain.article.repository.ArticleRefreshResult
import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import com.andrewwin.sumup.domain.article.repository.FullArticleContent
import com.andrewwin.sumup.domain.feed.model.ArticlePairScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ArticleRepositoryImpl @Inject constructor(
    private val articleDao: ArticleDao,
    private val articleEmbeddingDao: ArticleEmbeddingDao,
    private val articleSimilarityDao: ArticleSimilarityDao,
    private val savedArticleDao: SavedArticleDao,
    private val sourceDao: SourceDao,
    private val userPreferencesDao: UserPreferencesDao,
    private val remoteArticleDataSource: RemoteArticleDataSource,
    private val cleanArticleTextUseCase: ArticleContentCleaner,
    private val articleTitleFormatter: ArticleTitleFormatter,
    private val articleImportanceScorer: ArticleImportanceScorer
) : ArticleRepository {

    override val enabledArticles: Flow<List<Article>> =
        articleDao.getEnabledArticles().map { articles -> articles.map { it.toDomainModel() } }
    override val allArticles: Flow<List<Article>> =
        articleDao.getAllArticles().map { articles -> articles.map { it.toDomainModel() } }
    override val favoriteArticles: Flow<List<Article>> =
        savedArticleDao.getSavedArticles().map { list -> list.map(::mapSavedToUiArticle) }

    private val _feedRefreshRequests = MutableStateFlow(0L)
    override val feedRefreshRequests: Flow<Long> = _feedRefreshRequests.asStateFlow()

    override fun requestFeedRefresh(timestamp: Long) {
        _feedRefreshRequests.value = timestamp
    }

    override suspend fun refreshArticles(): ArticleRefreshResult = withContext(Dispatchers.IO) {
        val refreshStartedAt = System.currentTimeMillis()
        val groups = sourceDao.getGroupsWithSources().first()
        val cleanupHours = userPreferencesDao.getUserPreferences().first()?.articleAutoCleanupHours
            ?: UserPreferences.DEFAULT_ARTICLE_AUTO_CLEANUP_HOURS
        val cutoffTimestamp = System.currentTimeMillis() - (cleanupHours.toLong() * 60 * 60 * 1000L)
        val fetchedArticlesToInsert = mutableListOf<com.andrewwin.sumup.data.local.entities.Article>()
        val fetchedArticlesToUpdate = mutableListOf<com.andrewwin.sumup.data.local.entities.Article>()
        val existingArticlesToRefreshMetrics = mutableListOf<com.andrewwin.sumup.data.local.entities.Article>()
        val changedStableArticleKeys = linkedSetOf<String>()
        val sourceFooterUpdates = mutableListOf<Source>()
        val cleaningRequests = mutableListOf<ArticleCleaningRequest>()
        val enabledSources = groups
            .filter { it.group.isEnabled }
            .flatMap { groupWithSources -> groupWithSources.sources.filter { it.isEnabled } }
        val refreshBoundaries = buildRefreshBoundaries(cutoffTimestamp)

        val fetchAllStartedAt = System.currentTimeMillis()
        val fetchedArticlesBySource = fetchEnabledSources(enabledSources, cutoffTimestamp, refreshBoundaries)
        Log.d(
            ARTICLE_REFRESH_TIMING_LOG_TAG,
            "fetch_all_sources durationMs=${System.currentTimeMillis() - fetchAllStartedAt} " +
                "enabledSources=${enabledSources.size} fetchedArticles=${fetchedArticlesBySource.sumOf { it.second.size }} " +
                "parallelism=$NEWS_REFRESH_PARALLELISM"
        )
        val sourceTypeById = enabledSources.associateBy({ it.id }, { it.type })
        val existingLookupStartedAt = System.currentTimeMillis()
        val existingArticlesByStableKey = articleDao.getArticlesByStableArticleKeys(
            fetchedArticlesBySource
                .asSequence()
                .flatMap { (_, fetchedArticles) -> fetchedArticles.asSequence() }
                .map { it.stableArticleKey }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
        ).associateBy { it.stableArticleKey }
        Log.d(
            ARTICLE_REFRESH_TIMING_LOG_TAG,
            "existing_lookup durationMs=${System.currentTimeMillis() - existingLookupStartedAt} " +
                "existing=${existingArticlesByStableKey.size}"
        )
        val allFreshFetchedArticles = fetchedArticlesBySource
            .flatMap { (_, fetchedArticles) -> fetchedArticles }
            .filter { it.publishedAt >= cutoffTimestamp }
        val averageViewsStartedAt = System.currentTimeMillis()
        val averageViews = articleImportanceScorer.averageViews(
            allFreshFetchedArticles.map { it.toDomainModel() }
        )
        Log.d(
            IMPORTANCE_SCORE_LOG_TAG,
            "refresh averageViews durationMs=${System.currentTimeMillis() - averageViewsStartedAt} articles=${allFreshFetchedArticles.size}"
        )
        var oldFetchedArticlesSkipped = 0
        var existingFetchedArticlesSkipped = 0
        val planningStartedAt = System.currentTimeMillis()
        for ((source, fetchedArticles) in fetchedArticlesBySource) {
            val freshArticles = fetchedArticles.filter { it.publishedAt >= cutoffTimestamp }
            oldFetchedArticlesSkipped += fetchedArticles.size - freshArticles.size
            val processingPlan = filterArticlesForProcessing(
                source = source,
                freshArticles = freshArticles,
                existingArticlesByStableKey = existingArticlesByStableKey
            )
            Log.d(
                ARTICLE_REFRESH_PROFILE_LOG_TAG,
                "source_refresh sourceId=${source.id} type=${source.type} " +
                    "fetched=${fetchedArticles.size} fresh=${freshArticles.size} " +
                    "new=${processingPlan.newArticles.size} " +
                    "existing=${processingPlan.existingArticlesForMetricsRefresh.size} " +
                    "skippedExisting=${processingPlan.skippedExistingArticlesCount} " +
                    "skippedOld=${fetchedArticles.size - freshArticles.size}"
            )
            val articlesToClean = processingPlan.newArticles
            existingArticlesToRefreshMetrics.addAll(processingPlan.existingArticlesForMetricsRefresh)
            existingFetchedArticlesSkipped += processingPlan.skippedExistingArticlesCount
            if (articlesToClean.isNotEmpty()) {
                val footerDetection = detectFooterPatternIfNeeded(source, articlesToClean)
                val newFooterPattern = footerDetection.footerPattern

                if (footerDetection.didRun) {
                    sourceFooterUpdates.add(
                        source.copy(
                            footerPattern = newFooterPattern ?: source.footerPattern,
                            footerPatternCheckedAt = footerDetection.checkedAt
                        )
                    )
                }

                val currentFooter = newFooterPattern ?: source.footerPattern
                cleaningRequests.addAll(
                    articlesToClean.map { article ->
                        ArticleCleaningRequest(
                            article = article,
                            sourceType = source.type,
                            footerPattern = currentFooter
                        )
                    }
                )
            }
        }
        Log.d(
            ARTICLE_REFRESH_TIMING_LOG_TAG,
            "processing_plan durationMs=${System.currentTimeMillis() - planningStartedAt} " +
                "freshArticles=${allFreshFetchedArticles.size} newArticles=${cleaningRequests.size} " +
                "existingMetrics=${existingArticlesToRefreshMetrics.size} skippedOld=$oldFetchedArticlesSkipped " +
                "skippedExisting=$existingFetchedArticlesSkipped footerUpdates=${sourceFooterUpdates.size}"
        )
        if (cleaningRequests.isNotEmpty()) {
            val newArticlesImportanceStartedAt = System.currentTimeMillis()
            val cleanedArticles = cleanArticlesInParallel(
                requests = cleaningRequests,
                averageViews = averageViews
            )
            Log.d(
                IMPORTANCE_SCORE_LOG_TAG,
                "refresh newArticles durationMs=${System.currentTimeMillis() - newArticlesImportanceStartedAt} count=${cleaningRequests.size}"
            )
            Log.d(
                ARTICLE_REFRESH_TIMING_LOG_TAG,
                "clean_new_articles durationMs=${System.currentTimeMillis() - newArticlesImportanceStartedAt} " +
                    "articles=${cleaningRequests.size} parallelism=$ARTICLE_CLEANING_PARALLELISM"
            )

            cleanedArticles.forEach { article ->
                val existingArticle = existingArticlesByStableKey[article.stableArticleKey]
                if (existingArticle == null) {
                    fetchedArticlesToInsert.add(article)
                } else {
                    fetchedArticlesToUpdate.add(
                        article.copy(
                            id = existingArticle.id,
                            isRead = existingArticle.isRead,
                            isFavorite = existingArticle.isFavorite
                        )
                    )
                }
            }
            changedStableArticleKeys.addAll(
                cleanedArticles.mapNotNull { article ->
                    article.stableArticleKey.takeIf { it.isNotBlank() }
                }
            )
        }
        val dbWriteStartedAt = System.currentTimeMillis()
        for (sourceUpdate in sourceFooterUpdates) {
            sourceDao.updateSource(sourceUpdate)
        }

        if (fetchedArticlesToInsert.isNotEmpty()) {
            articleDao.insertArticles(fetchedArticlesToInsert)
        }
        if (fetchedArticlesToUpdate.isNotEmpty()) {
            articleDao.updateArticles(fetchedArticlesToUpdate)
        }
        Log.d(
            ARTICLE_REFRESH_TIMING_LOG_TAG,
            "db_write_new_articles durationMs=${System.currentTimeMillis() - dbWriteStartedAt} " +
                "insert=${fetchedArticlesToInsert.size} update=${fetchedArticlesToUpdate.size} " +
                "sourceFooterUpdates=${sourceFooterUpdates.size}"
        )
        if (existingArticlesToRefreshMetrics.isNotEmpty()) {
            val existingArticlesImportanceStartedAt = System.currentTimeMillis()
            val articlesWithRefreshedMetrics = existingArticlesToRefreshMetrics.mapNotNull { article ->
                val existingArticle = existingArticlesByStableKey[article.stableArticleKey] ?: return@mapNotNull null
                val sourceType = sourceTypeById[article.sourceId] ?: SourceType.RSS
                existingArticle.copy(
                    viewCount = article.viewCount,
                    importanceScore = articleImportanceScorer.score(
                        article = article.toDomainModel(),
                        averageViews = averageViews,
                        sourceType = sourceType.toDomainModel()
                    ),
                    mediaUrl = article.mediaUrl,
                    videoId = article.videoId
                )
            }
            if (articlesWithRefreshedMetrics.isNotEmpty()) {
                articleDao.updateArticles(articlesWithRefreshedMetrics)
            }
            Log.d(
                IMPORTANCE_SCORE_LOG_TAG,
                "refresh existingArticles durationMs=${System.currentTimeMillis() - existingArticlesImportanceStartedAt} count=${existingArticlesToRefreshMetrics.size}"
            )
        }
        val changedArticleIds = if (changedStableArticleKeys.isEmpty()) {
            emptyList()
        } else {
            articleDao.getArticleIdsByStableArticleKeys(changedStableArticleKeys.toList())
        }

        val cleanupStartedAt = System.currentTimeMillis()
        val newerCount = articleDao.countArticlesNewerThan(cutoffTimestamp)
        var deletedOldArticles = 0
        if (newerCount > 0) {
            deletedOldArticles = articleDao.deleteOldArticles(cutoffTimestamp)
        }
        Log.d(
            ARTICLE_REFRESH_TIMING_LOG_TAG,
            "cleanup_old_articles durationMs=${System.currentTimeMillis() - cleanupStartedAt} " +
                "newerCount=$newerCount deletedOld=$deletedOldArticles"
        )
        val result = ArticleRefreshResult(
            changedArticleIds = changedArticleIds,
            deletedOldArticlesCount = deletedOldArticles
        )
        Log.d(
            ARTICLE_REFRESH_TIMING_LOG_TAG,
            "refresh_total durationMs=${System.currentTimeMillis() - refreshStartedAt} " +
                "enabledSources=${enabledSources.size} fetchedArticles=${fetchedArticlesBySource.sumOf { it.second.size }} " +
                "freshArticles=${allFreshFetchedArticles.size} newArticles=${cleaningRequests.size} " +
                "insert=${fetchedArticlesToInsert.size} update=${fetchedArticlesToUpdate.size} " +
                "existingMetrics=${existingArticlesToRefreshMetrics.size} changedArticleIds=${changedArticleIds.size} " +
                "deletedOld=$deletedOldArticles"
        )
        result
    }

    private suspend fun filterArticlesForProcessing(
        source: Source,
        freshArticles: List<com.andrewwin.sumup.data.local.entities.Article>,
        existingArticlesByStableKey: Map<String, com.andrewwin.sumup.data.local.entities.Article>
    ): ArticleProcessingPlan {
        if (freshArticles.isEmpty()) return ArticleProcessingPlan()
        val stableArticleKeys = freshArticles
            .map { it.stableArticleKey }
            .filter { it.isNotBlank() }
            .distinct()
        if (stableArticleKeys.isEmpty()) {
            return ArticleProcessingPlan(
                newArticles = freshArticles,
                skippedExistingArticlesCount = 0
            )
        }
        val existingKeys = stableArticleKeys
            .filter { existingArticlesByStableKey.containsKey(it) }
            .toHashSet()
        if (existingKeys.isEmpty()) {
            return ArticleProcessingPlan(
                newArticles = freshArticles,
                skippedExistingArticlesCount = 0
            )
        }
        val newArticles = freshArticles.filterNot { it.stableArticleKey in existingKeys }
        val existingArticlesForMetricsRefresh = if (source.type == SourceType.TELEGRAM || source.type == SourceType.YOUTUBE) {
            freshArticles.filter { it.stableArticleKey in existingKeys }
        } else {
            emptyList()
        }
        return ArticleProcessingPlan(
            newArticles = newArticles,
            existingArticlesForMetricsRefresh = existingArticlesForMetricsRefresh,
            skippedExistingArticlesCount = freshArticles.size - newArticles.size
        )
    }

    private suspend fun detectFooterPatternIfNeeded(
        source: Source,
        articlesToClean: List<com.andrewwin.sumup.data.local.entities.Article>
    ): FooterDetectionResult {
        val now = System.currentTimeMillis()
        val reason = footerDetectionReason(source, now)
            ?: return FooterDetectionResult(
                didRun = false,
                reason = FOOTER_DETECTION_REASON_RECENT,
                footerPattern = null,
                checkedAt = source.footerPatternCheckedAt
            )
        val contentsForFooter = articlesToClean.take(FOOTER_PATTERN_SAMPLE_ARTICLES).map { it.content }
        val footerPattern = cleanArticleTextUseCase.detectFooterPattern(contentsForFooter)
        return FooterDetectionResult(
            didRun = true,
            reason = reason,
            footerPattern = footerPattern,
            checkedAt = now
        )
    }

    private fun footerDetectionReason(source: Source, now: Long): String? {
        if (source.footerPattern.isNullOrBlank()) return FOOTER_DETECTION_REASON_MISSING_PATTERN
        if (source.footerPatternCheckedAt <= 0L) return FOOTER_DETECTION_REASON_NEVER_CHECKED
        val ageMs = now - source.footerPatternCheckedAt
        return if (ageMs >= FOOTER_PATTERN_REFRESH_INTERVAL_MS) {
            FOOTER_DETECTION_REASON_EXPIRED
        } else {
            null
        }
    }

    private suspend fun fetchEnabledSources(
        sources: List<Source>,
        cutoffTimestamp: Long,
        refreshBoundaries: Map<Long, SourceRefreshBoundary>
    ): List<Pair<Source, List<com.andrewwin.sumup.data.local.entities.Article>>> = coroutineScope {
        val semaphore = Semaphore(NEWS_REFRESH_PARALLELISM)
        sources.map { source ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val sourceStartedAt = System.currentTimeMillis()
                    val articles = remoteArticleDataSource.fetchArticles(
                        source = source,
                        oldestAllowedPublishedAt = cutoffTimestamp,
                        refreshBoundary = refreshBoundaries[source.id] ?: SourceRefreshBoundary.Empty
                    )
                    Log.d(
                        ARTICLE_REFRESH_TIMING_LOG_TAG,
                        "source_fetch sourceId=${source.id} type=${source.type} " +
                            "durationMs=${System.currentTimeMillis() - sourceStartedAt} articles=${articles.size}"
                    )
                    source to articles
                }
            }
        }.awaitAll()
    }

    private suspend fun buildRefreshBoundaries(cutoffTimestamp: Long): Map<Long, SourceRefreshBoundary> {
        return articleDao.getEnabledArticlesSince(cutoffTimestamp)
            .groupBy { it.sourceId }
            .mapValues { (_, articles) ->
                SourceRefreshBoundary(
                    knownStableArticleKeys = articles
                        .map { it.stableArticleKey }
                        .filter { it.isNotBlank() }
                        .toSet(),
                    knownUrls = articles
                        .map { it.url }
                        .filter { it.isNotBlank() }
                        .toSet(),
                    knownVideoIds = articles
                        .mapNotNull { it.videoId?.takeIf(String::isNotBlank) }
                        .toSet()
                )
            }
    }

    private suspend fun cleanArticlesInParallel(
        requests: List<ArticleCleaningRequest>,
        averageViews: Long
    ): List<com.andrewwin.sumup.data.local.entities.Article> = coroutineScope {
        val semaphore = Semaphore(ARTICLE_CLEANING_PARALLELISM)
        requests.map { request ->
            async(Dispatchers.Default) {
                semaphore.withPermit {
                    val sourceType = request.sourceType
                    val cleanedContent = cleanArticleTextUseCase.clean(
                        request.article.content,
                        sourceType.toDomainModel(),
                        request.footerPattern
                    )
                    val articleWithCleanContent = request.article.toDomainModel().copy(content = cleanedContent)
                    val formattedArticle = articleTitleFormatter.format(
                        articleWithCleanContent,
                        sourceType.toDomainModel()
                    )
                    formattedArticle.copy(
                        importanceScore = articleImportanceScorer.score(
                            article = formattedArticle,
                            averageViews = averageViews,
                            sourceType = sourceType.toDomainModel()
                        )
                    ).toRoomEntity()
                }
            }
        }.awaitAll()
    }

    private data class ArticleProcessingPlan(
        val newArticles: List<com.andrewwin.sumup.data.local.entities.Article> = emptyList(),
        val existingArticlesForMetricsRefresh: List<com.andrewwin.sumup.data.local.entities.Article> = emptyList(),
        val skippedExistingArticlesCount: Int = 0
    )

    private data class ArticleCleaningRequest(
        val article: com.andrewwin.sumup.data.local.entities.Article,
        val sourceType: SourceType,
        val footerPattern: String?
    )

    override suspend fun updateArticle(article: Article) = articleDao.updateArticle(article.toRoomEntity())

    override suspend fun updateArticles(articles: List<Article>) {
        if (articles.isEmpty()) return
        articleDao.updateArticles(articles.map(Article::toRoomEntity))
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
                articleDao.setFavoriteByIds(rows.map { it.id }, true)
            }
            rows.size
        } else {
            var deleted = 0
            if (savedIds.isNotEmpty()) {
                val savedRows = savedArticleDao.getSavedArticlesByIds(savedIds)
                deleted += savedArticleDao.deleteByIds(savedIds)
                val savedUrls = savedRows.map { it.url }.filter { it.isNotBlank() }
                if (savedUrls.isNotEmpty()) {
                    articleDao.setFavoriteByUrls(savedUrls, false)
                }
            }
            if (articleIds.isNotEmpty()) {
                val rows = articleDao.getArticlesWithMetaByIds(articleIds)
                if (rows.isNotEmpty()) {
                    deleted += savedArticleDao.deleteByUrls(rows.map { it.url })
                    articleDao.setFavoriteByIds(rows.map { it.id }, false)
                }
            }
            deleted
        }
    }

    override suspend fun getEmbeddingsByIds(ids: List<Long>): Map<Long, ByteArray?> {
        if (ids.isEmpty()) return emptyMap()
        return articleEmbeddingDao.getEmbeddingsByIds(ids).associate { it.id to it.embedding }
    }

    override suspend fun getArticleEmbeddingsByIds(ids: List<Long>): List<ArticleEmbeddingRecord> {
        if (ids.isEmpty()) return emptyList()
        return articleEmbeddingDao.getEmbeddingsByIds(ids).map { it.toDomainRecord() }
    }

    override suspend fun upsertArticleEmbeddings(items: List<ArticleEmbeddingRecord>) {
        if (items.isEmpty()) return
        articleEmbeddingDao.upsertEmbeddings(items.mapNotNull(ArticleEmbeddingRecord::toRoomEntity))
    }

    override suspend fun getEnabledArticlesOnce(): List<Article> =
        articleDao.getEnabledArticlesOnce().map { it.toDomainModel() }

    override suspend fun getEnabledArticlesSince(timestamp: Long): List<Article> =
        articleDao.getEnabledArticlesSince(timestamp).map { it.toDomainModel() }

    override suspend fun getSourceById(id: Long): com.andrewwin.sumup.domain.source.model.Source? =
        sourceDao.getSourceById(id)?.toDomainModel()

    override suspend fun fetchFullContent(article: Article): FullArticleContent {
        val source = sourceDao.getSourceById(article.sourceId) ?: return FullArticleContent(
            text = article.content,
            status = RemoteContentFetchStatus.FETCH_FAILED
        )
        val fetchedRemote = remoteArticleDataSource.fetchFullContent(article.url, source.type)
        val remoteContent = fetchedRemote?.text ?: article.content
        val sourceType = source.type.toDomainModel()
        val mainContent = cleanArticleTextUseCase.extractMainContent(article.url, remoteContent, sourceType)
        val cleaned = cleanArticleTextUseCase.clean(mainContent, sourceType, source.footerPattern)
        return FullArticleContent(
            text = cleaned,
            status = fetchedRemote?.status ?: RemoteContentFetchStatus.FETCH_FAILED
        )
    }

    override suspend fun getSimilaritiesForArticles(
        articleIds: List<Long>,
        strategyKey: String
    ): List<ArticleSimilarityRecord> {
        if (articleIds.isEmpty() || strategyKey.isBlank()) return emptyList()
        return articleSimilarityDao.getSimilaritiesForArticles(articleIds, strategyKey).map(ArticleSimilarity::toDomainRecord)
    }

    override suspend fun getSimilaritiesInsideArticleSet(
        articleIds: List<Long>,
        strategyKey: String
    ): List<ArticleSimilarityRecord> {
        if (articleIds.isEmpty() || strategyKey.isBlank()) return emptyList()
        val startedAt = System.currentTimeMillis()
        return articleSimilarityDao.getSimilaritiesInsideArticleSet(articleIds, strategyKey).map(ArticleSimilarity::toDomainRecord).also { similarities ->
            Log.d(
                FEED_BUILD_PROFILE_LOG_TAG,
                "similarities_inside_query durationMs=${System.currentTimeMillis() - startedAt} " +
                    "articleIds=${articleIds.size} similarities=${similarities.size} strategyKey=$strategyKey"
            )
        }
    }

    override suspend fun getSimilaritiesInsideArticleSetAboveThreshold(
        articleIds: List<Long>,
        strategyKey: String,
        threshold: Float
    ): List<ArticleSimilarityRecord> {
        if (articleIds.isEmpty() || strategyKey.isBlank()) return emptyList()
        val startedAt = System.currentTimeMillis()
        return articleSimilarityDao
            .getSimilaritiesInsideArticleSetAboveThreshold(articleIds, strategyKey, threshold)
            .map(ArticleSimilarity::toDomainRecord)
            .also { similarities ->
                Log.d(
                    FEED_BUILD_PROFILE_LOG_TAG,
                    "similarities_inside_threshold_query durationMs=${System.currentTimeMillis() - startedAt} " +
                        "articleIds=${articleIds.size} similarities=${similarities.size} " +
                        "strategyKey=$strategyKey threshold=$threshold"
                )
            }
    }

    override suspend fun getSimilarityScoresInsideArticleSetAboveThreshold(
        articleIds: List<Long>,
        strategyKey: String,
        threshold: Float
    ): List<ArticlePairScore> {
        if (articleIds.isEmpty() || strategyKey.isBlank()) return emptyList()
        val startedAt = System.currentTimeMillis()
        return articleSimilarityDao
            .getSimilarityScoresInsideArticleSetAboveThreshold(articleIds, strategyKey, threshold)
            .map { row ->
                ArticlePairScore(
                    leftArticleId = row.leftArticleId,
                    rightArticleId = row.rightArticleId,
                    score = row.score
                )
            }
            .also { similarities ->
                Log.d(
                    FEED_BUILD_PROFILE_LOG_TAG,
                    "similarity_scores_inside_threshold_query durationMs=${System.currentTimeMillis() - startedAt} " +
                        "articleIds=${articleIds.size} similarities=${similarities.size} " +
                        "strategyKey=$strategyKey threshold=$threshold"
                )
            }
    }

    override suspend fun deleteSimilaritiesByStrategyKey(strategyKey: String) {
        if (strategyKey.isBlank()) return
        articleSimilarityDao.deleteSimilaritiesByStrategyKey(strategyKey)
    }

    override suspend fun deleteSimilaritiesForArticles(articleIds: List<Long>, strategyKey: String) {
        if (articleIds.isEmpty() || strategyKey.isBlank()) return
        articleSimilarityDao.deleteSimilaritiesForArticles(articleIds.distinct(), strategyKey)
    }

    override suspend fun upsertSimilarities(items: List<ArticleSimilarityRecord>) {
        if (items.isEmpty()) return

        val relatedIds = items.asSequence()
            .flatMap { sequenceOf(it.leftArticleId, it.rightArticleId) }
            .toSet()
        if (relatedIds.isEmpty()) return

        val existingIds = articleDao.getExistingArticleIds(relatedIds.toList()).toHashSet()
        if (existingIds.isEmpty()) return

        val validItems = items.filter { similarity ->
            similarity.leftArticleId in existingIds && similarity.rightArticleId in existingIds
        }
        if (validItems.isEmpty()) return

        try {
            articleSimilarityDao.upsertSimilarities(validItems.map(ArticleSimilarityRecord::toRoomEntity))
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
        articleEmbeddingDao.clearEmbeddings()
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
        articleDao.clearAllFavorites()
        savedArticleDao.deleteAll()
        val savedUrls = upsertSavedByUrls(urls)
        if (savedUrls.isNotEmpty()) {
            articleDao.markFavoritesByUrls(savedUrls)
        }
    }

    override suspend fun mergeFavoriteArticlesByUrls(urls: List<String>) {
        val savedUrls = upsertSavedByUrls(urls)
        if (savedUrls.isNotEmpty()) {
            articleDao.markFavoritesByUrls(savedUrls)
        }
    }

    override suspend fun getSavedArticlesSnapshot(): List<SavedArticleSnapshot> =
        savedArticleDao.getSavedArticlesOnce().map(SavedArticle::toDomainSnapshot)

    override suspend fun replaceSavedArticlesSnapshot(items: List<SavedArticleSnapshot>) {
        articleDao.clearAllFavorites()
        savedArticleDao.deleteAll()
        val savedItems = items.map { item ->
            item.toRoomEntity().copy(
                id = 0,
                url = item.url.trim()
            )
        }.filter { it.url.isNotBlank() }
        if (savedItems.isNotEmpty()) {
            savedArticleDao.upsert(savedItems)
            articleDao.markFavoritesByUrls(savedItems.map { it.url })
        }
    }

    override suspend fun mergeSavedArticlesSnapshot(items: List<SavedArticleSnapshot>) {
        if (items.isEmpty()) return
        val savedItems = items.map { item ->
            item.toRoomEntity().copy(
                id = 0,
                url = item.url.trim()
            )
        }.filter { it.url.isNotBlank() }
        if (savedItems.isNotEmpty()) {
            savedArticleDao.upsert(savedItems)
            articleDao.markFavoritesByUrls(savedItems.map { it.url })
        }
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
        val savedById = loadSavedArticlesByUiIds(articleIds).associateBy { savedArticleUiId(it.id) }
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
        val startedAt = System.currentTimeMillis()
        val distinctIds = articleIds.distinct()
        val negativeIds = distinctIds.filter { it < 0L }
        val positiveIds = distinctIds.filter { it > 0L }
        val negativeLookupStartedAt = System.currentTimeMillis()
        val directSavedAtByUiId = loadSavedAtByUiIds(negativeIds)
        val negativeLookupDurationMs = System.currentTimeMillis() - negativeLookupStartedAt
        val positiveLookupStartedAt = System.currentTimeMillis()
        val directSavedAtByArticleId = loadSavedAtByPositiveArticleIds(positiveIds)
        val positiveLookupDurationMs = System.currentTimeMillis() - positiveLookupStartedAt

        return buildMap {
            distinctIds.forEach { uiId ->
                val savedAt = when {
                    uiId < 0L -> directSavedAtByUiId[uiId] ?: 0L
                    else -> directSavedAtByArticleId[uiId] ?: 0L
                }
                if (savedAt > 0L) put(uiId, savedAt)
            }
        }.also { result ->
            Log.d(
                FEED_BUILD_PROFILE_LOG_TAG,
                "favorite_saved_at durationMs=${System.currentTimeMillis() - startedAt} " +
                    "negativeLookupMs=$negativeLookupDurationMs positiveLookupMs=$positiveLookupDurationMs " +
                    "articleIds=${articleIds.size} negativeIds=${negativeIds.size} positiveIds=${positiveIds.size} " +
                    "savedRows=${directSavedAtByUiId.size + directSavedAtByArticleId.size} result=${result.size}"
            )
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
        val savedById = loadSavedArticlesByUiIds(articleIds).associateBy { savedArticleUiId(it.id) }
        return buildMap {
            articleIds.distinct().forEach { uiId ->
                savedById[uiId]?.clusterScore?.let { score ->
                    if (score > 0f) put(uiId, score)
                }
            }
        }
    }

    override suspend fun getFavoriteSimilarities(
        articleIds: List<Long>,
        strategyKey: String
    ): List<ArticleSimilarityRecord> {
        if (articleIds.isEmpty() || strategyKey.isBlank()) return emptyList()

        val requestedUiIds = articleIds.distinct()
        val savedByUiId = loadSavedArticlesByUiIds(requestedUiIds).associateBy { savedArticleUiId(it.id) }
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
        val rawSimilarities = articleSimilarityDao.getSimilaritiesForArticles(allResolvedArticleIds, strategyKey)
        if (rawSimilarities.isEmpty()) return emptyList()

        val uiIdsByArticleId = mutableMapOf<Long, MutableSet<Long>>()
        resolvedArticleIdsByUiId.forEach { (uiId, resolvedIds) ->
            resolvedIds.forEach { articleId ->
                uiIdsByArticleId.getOrPut(articleId) { mutableSetOf() }.add(uiId)
            }
        }

        val mergedScores = mutableMapOf<Pair<Long, Long>, Float>()
        rawSimilarities.forEach { sim ->
            val leftUiIds = uiIdsByArticleId[sim.leftArticleId].orEmpty()
            val rightUiIds = uiIdsByArticleId[sim.rightArticleId].orEmpty()
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
            ArticleSimilarityRecord(
                leftArticleId = pair.first,
                rightArticleId = pair.second,
                strategyKey = strategyKey,
                score = score,
                leftContentSignature = "",
                rightContentSignature = ""
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

    private suspend fun upsertSavedByUrls(urls: List<String>): List<String> {
        if (urls.isEmpty()) return emptyList()
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
        return toUpsert.map { it.url }
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
            stableArticleKey = ArticleStableKeyFactory.buildSavedKey(item.url),
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

    private suspend fun loadSavedArticlesByUiIds(uiIds: List<Long>): List<SavedArticle> {
        val savedIds = uiIds.distinct().mapNotNull(::uiArticleIdToSavedId)
        if (savedIds.isEmpty()) return emptyList()
        return savedArticleDao.getSavedArticlesByIds(savedIds)
    }

    private suspend fun loadSavedAtByUiIds(uiIds: List<Long>): Map<Long, Long> {
        val savedIds = uiIds.distinct().mapNotNull(::uiArticleIdToSavedId)
        if (savedIds.isEmpty()) return emptyMap()
        return savedArticleDao.getSavedArticleSavedAtByIds(savedIds)
            .associate { savedArticleUiId(it.id) to it.savedAt }
    }

    private suspend fun loadSavedAtByPositiveArticleIds(articleIds: List<Long>): Map<Long, Long> {
        if (articleIds.isEmpty()) return emptyMap()
        return articleDao.getFavoriteSavedAtByArticleIds(articleIds.distinct())
            .associate { it.articleId to it.savedAt }
    }

    private data class FooterDetectionResult(
        val didRun: Boolean,
        val reason: String,
        val footerPattern: String?,
        val checkedAt: Long
    )

    private companion object {
        private const val FEED_BUILD_PROFILE_LOG_TAG = "FeedBuildProfile"
        private const val IMPORTANCE_SCORE_LOG_TAG = "ArticleImportanceTiming"
        private const val ARTICLE_REFRESH_PROFILE_LOG_TAG = "ArticleRefreshProfile"
        private const val ARTICLE_REFRESH_TIMING_LOG_TAG = "ArticleRefreshTiming"
        private val NEWS_REFRESH_PARALLELISM = Runtime.getRuntime().availableProcessors()
        private val ARTICLE_CLEANING_PARALLELISM = Runtime.getRuntime().availableProcessors()
        private const val FOOTER_PATTERN_SAMPLE_ARTICLES = 10
        private const val FOOTER_PATTERN_REFRESH_INTERVAL_HOURS = 48L
        private const val FOOTER_PATTERN_REFRESH_INTERVAL_MS =
            FOOTER_PATTERN_REFRESH_INTERVAL_HOURS * 60L * 60L * 1000L
        private const val FOOTER_DETECTION_REASON_MISSING_PATTERN = "missing_pattern"
        private const val FOOTER_DETECTION_REASON_NEVER_CHECKED = "never_checked"
        private const val FOOTER_DETECTION_REASON_EXPIRED = "expired"
        private const val FOOTER_DETECTION_REASON_RECENT = "recent"
    }
}
