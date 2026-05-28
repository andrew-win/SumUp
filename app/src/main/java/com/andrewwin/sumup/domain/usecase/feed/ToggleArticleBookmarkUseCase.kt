package com.andrewwin.sumup.domain.usecase.feed

import com.andrewwin.sumup.domain.article.Article
import com.andrewwin.sumup.domain.repository.ArticleRepository
import javax.inject.Inject

data class ArticleBookmarkToggleRequest(
    val articles: List<Article>,
    val scoreByArticleId: Map<Long, Float>,
    val clusterRepresentativeId: Long? = null
)

class ToggleArticleBookmarkUseCase @Inject constructor(
    private val articleRepository: ArticleRepository
) {
    suspend operator fun invoke(request: ArticleBookmarkToggleRequest): Boolean {
        val articles = request.articles.distinctBy(Article::id)
        if (articles.isEmpty()) return false

        val articleIds = articles.map(Article::id)
        val newFavorite = articles.any { !it.isFavorite }
        articleRepository.setFavoriteByIds(articleIds, newFavorite)

        if (newFavorite) {
            val clusterKey = request.clusterRepresentativeId
                ?.takeIf { articleIds.size > 1 }
                ?.let { representativeId -> "cluster:$representativeId" }
                .orEmpty()
            articleRepository.saveFavoriteClusterMapping(articleIds, clusterKey)
            articleRepository.saveFavoriteClusterScores(request.scoreByArticleId)
            articleRepository.saveFavoriteSavedAt(articleIds)
        } else {
            articleRepository.clearFavoriteClusterMapping(articleIds)
            articleRepository.clearFavoriteSavedAt(articleIds)
        }

        return newFavorite
    }
}
