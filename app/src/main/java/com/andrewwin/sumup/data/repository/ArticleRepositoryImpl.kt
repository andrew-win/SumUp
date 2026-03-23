package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.local.dao.ArticleDao
import com.andrewwin.sumup.data.local.dao.ArticleSimilarityDao
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.ArticleSimilarity
import com.andrewwin.sumup.data.remote.datasource.RemoteArticleDataSource
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.usecase.CleanArticleTextUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ArticleRepositoryImpl @Inject constructor(
    private val articleDao: ArticleDao,
    private val articleSimilarityDao: ArticleSimilarityDao,
    private val sourceDao: SourceDao,
    private val remoteArticleDataSource: RemoteArticleDataSource,
    private val cleanArticleTextUseCase: CleanArticleTextUseCase
) : ArticleRepository {

    override val enabledArticles: Flow<List<Article>> =
        articleDao.getEnabledArticles()

    override suspend fun refreshArticles() = withContext(Dispatchers.IO) {
        val groups = sourceDao.getGroupsWithSources().first()
        groups.forEach { groupWithSources ->
            if (groupWithSources.group.isEnabled) {
                groupWithSources.sources.forEach { source ->
                    if (source.isEnabled) {
                        val fetchedArticles = remoteArticleDataSource.fetchArticles(source)

                        if (fetchedArticles.isNotEmpty()) {
                            val cleanedContents = fetchedArticles
                                .take(10)
                                .map { it.content }
                            val newFooterPattern = cleanArticleTextUseCase(cleanedContents)

                            if (newFooterPattern != null && newFooterPattern != source.footerPattern) {
                                sourceDao.updateSource(source.copy(footerPattern = newFooterPattern))
                            }

                            val currentFooter = newFooterPattern ?: source.footerPattern
                            val cleanedArticles = fetchedArticles.map { article ->
                                article.copy(
                                    content = cleanArticleTextUseCase(article.content, source.type, currentFooter)
                                )
                            }
                            val insertResults = articleDao.insertArticles(cleanedArticles)
                            val insertedCount = insertResults.count { it != -1L }
                            cleanedArticles.forEach { article ->
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
        val threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
        val newerCount = articleDao.countArticlesNewerThan(threeDaysAgo)
        if (newerCount > 0) {
            articleDao.deleteOldArticles(threeDaysAgo)
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
        val remoteContent = remoteArticleDataSource.fetchFullContent(article.url, source.type) ?: article.content
        val mainContent = cleanArticleTextUseCase.extractMainContent(article.url, remoteContent, source.type)
        return cleanArticleTextUseCase(mainContent, source.type, source.footerPattern)
    }

    override suspend fun getSimilaritiesForArticles(articleIds: List<Long>): List<ArticleSimilarity> =
        articleSimilarityDao.getSimilaritiesForArticles(articleIds)

    override suspend fun upsertSimilarities(items: List<ArticleSimilarity>) {
        if (items.isEmpty()) return
        articleSimilarityDao.upsertSimilarities(items)
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
}
