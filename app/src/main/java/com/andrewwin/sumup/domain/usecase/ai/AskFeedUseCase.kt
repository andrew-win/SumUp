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
                builder.append("source_id: ").append(uiModel.article.id).append('\n')
                uiModel.sourceName
                    ?.takeIf { it.isNotBlank() }
                    ?.let { builder.append("source_name: ").append(it.trim()).append('\n') }
                builder.append("source_url: ").append(uiModel.article.url).append('\n')
                builder.append("title: ").append(uiModel.displayTitle.trim()).append('\n')
                val contentPreview = uiModel.displayContent
                    .takeIf { it.isNotBlank() }
                    ?: uiModel.article.content
                builder.append("content: ").append(contentPreview.take(perArticleLimit))
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









