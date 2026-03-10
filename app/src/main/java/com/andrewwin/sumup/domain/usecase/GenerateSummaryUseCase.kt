package com.andrewwin.sumup.domain.usecase

import android.content.Context
import com.andrewwin.sumup.R
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import javax.inject.Inject

interface GenerateSummaryUseCase {
    /**
     * @return localized summary text or system notice/error message.
     */
    suspend operator fun invoke(): String
}

class GenerateSummaryUseCaseImpl @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val aiRepository: AiRepository,
    private val appContext: Context
) : GenerateSummaryUseCase {

    override suspend fun invoke(): String {
        articleRepository.refreshArticles()
        val articles = articleRepository.getEnabledArticlesOnce()

        if (articles.isEmpty()) {
            return appContext.getString(R.string.summary_no_articles_today)
        }

        val content = articles.take(10)
            .joinToString(separator = "\n\n") { "${it.title}: ${it.content}" }

        return aiRepository.summarize(content)
    }
}

