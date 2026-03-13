package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.local.dao.ArticleDao
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.remote.datasource.RemoteArticleDataSource
import com.andrewwin.sumup.domain.FooterCleaner
import com.andrewwin.sumup.domain.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ArticleRepositoryImpl @Inject constructor(
    private val articleDao: ArticleDao,
    private val sourceDao: SourceDao,
    private val remoteArticleDataSource: RemoteArticleDataSource
) : ArticleRepository {

    override val enabledArticles: Flow<List<Article>> = articleDao.getEnabledArticles()

    override suspend fun refreshArticles() = withContext(Dispatchers.IO) {
        val groups = sourceDao.getGroupsWithSources().first()
        groups.forEach { groupWithSources ->
            if (groupWithSources.group.isEnabled) {
                groupWithSources.sources.forEach { source ->
                    if (source.isEnabled) {
                        val fetchedArticles = remoteArticleDataSource.fetchArticles(source.id, source.url, source.type)
                        
                        if (fetchedArticles.isNotEmpty()) {
                            val cleanedArticles = fetchedArticles.map { article ->
                                article.copy(
                                    content = FooterCleaner.removeFooter(article.content, source.footerPattern)
                                )
                            }
                            articleDao.insertArticles(cleanedArticles)
                        }
                    }
                }
            }
        }
        val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        articleDao.deleteOldArticles(oneWeekAgo)
    }

    override suspend fun updateArticle(article: Article) {
        articleDao.updateArticle(article)
    }

    override suspend fun getEnabledArticlesOnce(): List<Article> =
        articleDao.getEnabledArticlesOnce()

    override suspend fun fetchFullContent(article: Article): String {
        val source = sourceDao.getSourceById(article.sourceId) ?: return article.content
        return remoteArticleDataSource.fetchFullContent(article.url, source.type) ?: article.content
    }
}
