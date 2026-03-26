package com.andrewwin.sumup.domain.usecase.common

import com.andrewwin.sumup.domain.repository.ArticleRepository
import javax.inject.Inject

interface RefreshArticlesUseCase {
    suspend operator fun invoke()
}

class RefreshArticlesUseCaseImpl @Inject constructor(
    private val articleRepository: ArticleRepository
) : RefreshArticlesUseCase {
    override suspend fun invoke() {
        articleRepository.refreshArticles()
    }
}










