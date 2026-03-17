package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.coroutines.DispatcherProvider
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.FormatArticleHeadlineUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AskFeedUseCase @Inject constructor(
    private val askQuestionUseCase: AskQuestionUseCase,
    private val sourceRepository: SourceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val formatArticleHeadlineUseCase: FormatArticleHeadlineUseCase,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(articles: List<Article>, question: String): Result<String> {
        val prefs = userPreferencesRepository.preferences.first()
        val perArticleLimit = prefs.aiMaxCharsPerFeedArticle.coerceAtLeast(200)
        val maxTotalChars = prefs.aiMaxCharsTotal.coerceAtLeast(1000)
        val sourceTypes = withContext(dispatcherProvider.io) {
            val ids = articles.map { it.sourceId }.distinct()
            sourceRepository.getSourcesByIds(ids).associate { it.id to it.type }
        }
        val content = withContext(dispatcherProvider.default) {
            val builder = StringBuilder()
            for (article in articles) {
                val formatted = formatArticleHeadlineUseCase(article, sourceTypes[article.sourceId] ?: SourceType.RSS)
                if (builder.isNotEmpty()) builder.append("\n\n")
                builder.append(formatted.displayTitle)
                    .append(": ")
                    .append(article.content.take(perArticleLimit))
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
