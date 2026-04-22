package com.andrewwin.sumup.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.andrewwin.sumup.data.local.dao.ArticleDao
import com.andrewwin.sumup.data.local.dao.ArticleSimilarityDao
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.dao.UserPreferencesDao
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.ArticleSimilarity
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.RemoteArticleDataSource
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.support.DebugTrace
import com.andrewwin.sumup.domain.usecase.common.CleanArticleTextUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ArticleRepositoryImpl @Inject constructor(
    private val articleDao: ArticleDao,
    private val articleSimilarityDao: ArticleSimilarityDao,
    private val sourceDao: SourceDao,
    private val userPreferencesDao: UserPreferencesDao,
    private val remoteArticleDataSource: RemoteArticleDataSource,
    private val cleanArticleTextUseCase: CleanArticleTextUseCase
) : ArticleRepository {

    override val enabledArticles: Flow<List<Article>> =
        articleDao.getEnabledArticles()

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

    override suspend fun getEmbeddingsByIds(ids: List<Long>): Map<Long, ByteArray?> {
        if (ids.isEmpty()) return emptyMap()
        return articleDao.getEmbeddingsByIds(ids).associate { it.id to it.embedding }
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
        if (source.type == SourceType.YOUTUBE) {
            DebugTrace.d(
                "youtube_full_content",
                "repository fetchFullContent articleId=${article.id} videoId=${article.videoId} remoteFetched=${fetchedRemote != null} remoteChars=${fetchedRemote?.length ?: 0} articleContentChars=${article.content.length} finalChars=${cleaned.length} finalPreview=${DebugTrace.preview(cleaned, 260)}"
            )
        }
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
}






