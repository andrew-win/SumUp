package com.andrewwin.sumup.domain.feed.pipeline

import com.andrewwin.sumup.domain.article.repository.ArticleRefreshResult
import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import javax.inject.Inject

interface UpdateArticlesFromSources {
    suspend operator fun invoke(): ArticleRefreshResult
}

class UpdateArticlesFromSourcesImpl @Inject constructor(
    private val articleRepository: ArticleRepository
) : UpdateArticlesFromSources {
    override suspend fun invoke(): ArticleRefreshResult = articleRepository.refreshArticles()
}

