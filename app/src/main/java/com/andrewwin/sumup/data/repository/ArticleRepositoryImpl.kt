package com.andrewwin.sumup.data.repository

import android.util.Log
import com.andrewwin.sumup.data.local.dao.ArticleDao
import com.andrewwin.sumup.data.local.dao.ArticleSimilarityDao
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.ArticleSimilarity
import com.andrewwin.sumup.data.remote.datasource.RemoteArticleDataSource
import com.andrewwin.sumup.domain.FooterCleaner
import com.andrewwin.sumup.domain.TextCleaner
import com.andrewwin.sumup.domain.usecase.CleanArticleTextUseCase
import com.andrewwin.sumup.domain.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
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
            .onEach { Log.d("ArticleRepo", "enabledArticles flow size=${it.size}") }

    override suspend fun refreshArticles() = withContext(Dispatchers.IO) {
        Log.d("ArticleRepo", "refreshArticles started")
        val groups = sourceDao.getGroupsWithSources().first()
        Log.d("ArticleRepo", "Found ${groups.size} groups")
        groups.forEach { groupWithSources ->
            if (groupWithSources.group.isEnabled) {
                Log.d("ArticleRepo", "Processing group: ${groupWithSources.group.name}")
                groupWithSources.sources.forEach { source ->
                    if (source.isEnabled) {
                        Log.d("ArticleRepo", "Fetching from source: ${source.name} (${source.url})")
                        val fetchedArticles = remoteArticleDataSource.fetchArticles(source.id, source.url, source.type)
                        Log.d("ArticleRepo", "Fetched ${fetchedArticles.size} articles from ${source.name}")
                        
                        if (fetchedArticles.isNotEmpty()) {
                            // 1. Адаптивне оновлення патерна футера
                            // Якщо у нас є свіжі статті, спробуємо знайти актуальний футер
                            val cleanedContents = fetchedArticles.take(10).map { TextCleaner.clean(it.content) }
                            val newFooterPattern = FooterCleaner.findCommonFooter(cleanedContents)
                            
                            // Оновлюємо патерн у БД, якщо він змінився і він не порожній
                            if (newFooterPattern != null && newFooterPattern != source.footerPattern) {
                                sourceDao.updateSource(source.copy(footerPattern = newFooterPattern))
                            }

                            // 2. Очищення статей перед збереженням
                            val currentFooter = newFooterPattern ?: source.footerPattern
                            val cleanedArticles = fetchedArticles.map { article ->
                                article.copy(
                                    content = cleanArticleTextUseCase(article.content, source.type, currentFooter)
                                )
                            }
                            val insertResults = articleDao.insertArticles(cleanedArticles)
                            val insertedCount = insertResults.count { it != -1L }
                            Log.d("ArticleRepo", "Inserted $insertedCount/${cleanedArticles.size} articles")
                            cleanedArticles.forEach { article ->
                                if (!article.mediaUrl.isNullOrBlank() || !article.videoId.isNullOrBlank()) {
                                    articleDao.updateMediaByUrl(
                                        url = article.url,
                                        mediaUrl = article.mediaUrl,
                                        videoId = article.videoId
                                    )
                                }
                            }
                            val enabledNow = articleDao.getEnabledArticlesOnce()
                            Log.d("ArticleRepo", "Enabled articles after insert: ${enabledNow.size}")
                        }
                    }
                }
            }
        }
        val threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
        val newerCount = articleDao.countArticlesNewerThan(threeDaysAgo)
        val olderCount = articleDao.countArticlesOlderThan(threeDaysAgo)
        if (newerCount > 0) {
            val deleted = articleDao.deleteOldArticles(threeDaysAgo)
            Log.d("ArticleRepo", "Deleted old articles: $deleted (older=$olderCount newer=$newerCount)")
        } else {
            Log.w("ArticleRepo", "Skip deleteOldArticles: newerCount=0 olderCount=$olderCount (timestamps likely invalid)")
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
        val fullContent = remoteArticleDataSource.fetchFullContent(article.url, source.type) ?: article.content
        
        // Централізоване очищення повного тексту
        return cleanArticleTextUseCase(fullContent, source.type, source.footerPattern)
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
