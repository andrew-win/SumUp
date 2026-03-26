package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.support.DispatcherProvider
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.ui.screen.feed.model.ArticleUiModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AskFeedUseCase @Inject constructor(
    private val askQuestionUseCase: AskQuestionUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(articles: List<ArticleUiModel>, question: String): Result<String> {
        val prefs = userPreferencesRepository.preferences.first()
        val perArticleLimit = prefs.aiMaxCharsPerFeedArticle.coerceAtLeast(200)
        val maxTotalChars = prefs.aiMaxCharsTotal.coerceAtLeast(1000)
        
        val content = withContext(dispatcherProvider.default) {
            val builder = StringBuilder()
            for (uiModel in articles) {
                if (builder.isNotEmpty()) builder.append("\n\n")
                builder.append(uiModel.displayTitle)
                    .append(": ")
                    .append(uiModel.article.content.take(perArticleLimit))
                if (builder.length >= maxTotalChars) break
            }
            builder.toString().take(maxTotalChars)
        }
        if (content.isBlank()) return Result.success("")
        return withContext(dispatcherProvider.io) {
            askQuestionUseCase(content, question)
        }
    }
}









