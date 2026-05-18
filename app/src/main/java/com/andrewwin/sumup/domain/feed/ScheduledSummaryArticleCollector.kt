package com.andrewwin.sumup.domain.feed

import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.news.ArticleImportanceScorer
import com.andrewwin.sumup.domain.news.SimilarityScorer
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.ai.RefreshArticlesUseCase
import com.andrewwin.sumup.domain.usecase.feed.ArticlePairKey
import com.andrewwin.sumup.domain.usecase.feed.FeedClusterCalculator
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ScheduledSummaryArticleCollector @Inject constructor(
    private val refreshArticlesUseCase: RefreshArticlesUseCase,
    private val articleRepository: ArticleRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val importanceScorer: ArticleImportanceScorer,
    private val similarityScorer: SimilarityScorer
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

        val currentArticleIds = filteredArticles.mapTo(mutableSetOf()) { it.id }
        val strategyKey = similarityScorer.similarityCacheKeyForStrategy(prefs.deduplicationStrategy)
        val threshold = when (prefs.deduplicationStrategy) {
            com.andrewwin.sumup.data.local.entities.DeduplicationStrategy.LOCAL -> prefs.localDeduplicationThreshold
            com.andrewwin.sumup.data.local.entities.DeduplicationStrategy.CLOUD -> prefs.cloudDeduplicationThreshold
        }
        val pairScores = articleRepository
            .getSimilaritiesForArticles(filteredArticles.map { it.id }, strategyKey)
            .asSequence()
            .filter { it.leftArticleId in currentArticleIds && it.rightArticleId in currentArticleIds }
            .filter { it.score >= threshold }
            .associate { similarity ->
                ArticlePairKey.of(similarity.leftArticleId, similarity.rightArticleId) to similarity.score
            }
        val clusters = FeedClusterCalculator.buildFinalClusters(filteredArticles, pairScores)

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
