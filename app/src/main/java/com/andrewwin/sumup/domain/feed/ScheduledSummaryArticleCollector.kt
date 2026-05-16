package com.andrewwin.sumup.domain.feed

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.ArticleSimilarity
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.news.ArticleCluster
import com.andrewwin.sumup.domain.news.ArticleImportanceScorer
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.common.RefreshArticlesUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ScheduledSummaryArticleCollector @Inject constructor(
    private val refreshArticlesUseCase: RefreshArticlesUseCase,
    private val articleRepository: ArticleRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val importanceScorer: ArticleImportanceScorer
) {
    suspend operator fun invoke(refresh: Boolean): List<FeedSummaryArticle> {
        if (refresh) {
            refreshArticlesUseCase()
        }

        val prefs = userPreferencesRepository.preferences.first()
        val articles = articleRepository.getEnabledArticlesOnce()
        if (articles.isEmpty()) return emptyList()

        val sourceTypesById = articles
            .map { it.sourceId }
            .distinct()
            .associateWith { sourceId ->
                articleRepository.getSourceById(sourceId)?.type ?: SourceType.RSS
            }
        val averageViews = articles
            .asSequence()
            .map { it.viewCount }
            .filter { it > 0L }
            .average()
            .takeIf { !it.isNaN() }
            ?.toLong()
            ?: 0L

        val scoredArticles = articles.map { article ->
            val sourceType = sourceTypesById[article.sourceId] ?: SourceType.RSS
            article.copy(importanceScore = importanceScorer.score(article, averageViews, sourceType))
        }
        val filteredArticles = if (prefs.isImportanceFilterEnabled) {
            scoredArticles.filter { it.importanceScore >= ArticleImportanceScorer.IMPORTANCE_THRESHOLD }
        } else {
            scoredArticles
        }
        if (filteredArticles.isEmpty()) return emptyList()

        val articlesById = filteredArticles.associateBy { it.id }
        val similarities = articleRepository
            .getSimilaritiesForArticles(filteredArticles.map { it.id })
            .filter { it.representativeId in articlesById && it.articleId in articlesById }
        val clusteredArticleIds = mutableSetOf<Long>()
        val clusters = mutableListOf<ArticleCluster>()

        similarities
            .groupBy { it.representativeId }
            .forEach { (representativeId, representativeSimilarities) ->
                val representative = articlesById[representativeId] ?: return@forEach
                if (representative.id in clusteredArticleIds) return@forEach

                val duplicates = representativeSimilarities
                    .mapNotNull { similarity ->
                        articlesById[similarity.articleId]
                            ?.takeIf { it.id != representative.id }
                            ?.let { article -> article to similarity.score }
                    }
                    .distinctBy { it.first.id }
                    .sortedByDescending { it.second }

                clusters += ArticleCluster(representative, duplicates)
                clusteredArticleIds += representative.id
                clusteredArticleIds += duplicates.map { it.first.id }
            }

        filteredArticles
            .filter { it.id !in clusteredArticleIds }
            .forEach { article -> clusters += ArticleCluster(article, emptyList()) }

        return clusters
            .filter { cluster -> !prefs.isHideSingleNewsEnabled || cluster.duplicates.size >= prefs.minMentions }
            .sortedByDescending { cluster ->
                cluster.representative.importanceScore + cluster.duplicates.size * SIMILAR_NEWS_BONUS_PER_MATCH
            }
            .map { cluster ->
                FeedSummaryArticle(
                    article = cluster.representative,
                    similarArticlesCount = cluster.duplicates.size,
                    baseImportanceScore = cluster.representative.importanceScore
                )
            }
    }

    private companion object {
        private const val SIMILAR_NEWS_BONUS_PER_MATCH = 0.25f
    }
}
