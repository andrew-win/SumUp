package com.andrewwin.sumup.domain.feed

import com.andrewwin.sumup.domain.repository.ArticleRefreshResult
import com.andrewwin.sumup.domain.repository.ArticleRepository
import javax.inject.Inject

interface UpdateArticlesFromSources {
    suspend operator fun invoke(): ArticleRefreshResult
}

class UpdateArticlesFromSourcesImpl @Inject constructor(
    private val articleRepository: ArticleRepository
) : UpdateArticlesFromSources {
    override suspend fun invoke(): ArticleRefreshResult = articleRepository.refreshArticles()
}

