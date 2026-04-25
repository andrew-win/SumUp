package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.support.DispatcherProvider
import kotlinx.coroutines.withContext
import com.andrewwin.sumup.ui.screen.feed.model.ArticleUiModel
import javax.inject.Inject

class AskFeedUseCase @Inject constructor(
    private val askQuestionUseCase: AskQuestionUseCase,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(articles: List<ArticleUiModel>, question: String): Result<String> {
        return withContext(dispatcherProvider.io) {
            askQuestionUseCase(articles.map { it.article }, question)
        }
    }
}









