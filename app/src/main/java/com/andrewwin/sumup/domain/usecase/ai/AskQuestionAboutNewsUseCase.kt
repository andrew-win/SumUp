package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SummaryLanguage
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.support.UnsupportedStrategyException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AskQuestionAboutNewsUseCase @Inject constructor(
    private val userPrefsRepo: UserPreferencesRepository,
    private val articleRepo: ArticleRepository,
    private val shrinkTextUseCase: ShrinkTextForAdaptiveStrategyUseCase,
    private val sendCloudAiRequestUseCase: SendCloudAiRequestUseCase,
    private val parseAiJsonResponseUseCase: ParseAiJsonResponseUseCase
) {
    suspend operator fun invoke(articles: List<Article>, question: String): Result<SummaryResult> = runCatching {
        val prefs = userPrefsRepo.preferences.first()
        if (prefs.aiStrategy == AiStrategy.LOCAL) throw UnsupportedStrategyException()

        val processedArticles = articles.map { article ->
            val source = articleRepo.getSourceById(article.sourceId)
            val fullContent = articleRepo.fetchFullContent(article).ifBlank { article.content }

            val processedContent = if (prefs.aiStrategy == AiStrategy.ADAPTIVE) {
                shrinkTextUseCase(fullContent, prefs)
            } else {
                fullContent.take(prefs.aiMaxCharsPerArticle.coerceAtLeast(1000))
            }

            """
            source_id: ${article.id}
            source_name: ${source?.name?.trim()?.ifBlank { "Джерело" } ?: "Джерело"}
            source_url: ${article.url.ifBlank { source?.url.orEmpty() }}
            title: ${article.title}
            content: $processedContent
            """.trimIndent()
        }

        val cloudInput = processedArticles.joinToString(separator = "\n\n")

        val prompt = AiPromptBuilder.buildQuestionPrompt(prefs.summaryLanguage, question)
        val jsonResponse = sendCloudAiRequestUseCase(prompt, cloudInput)
        val parsed = parseAiJsonResponseUseCase.parseQuestion(jsonResponse, cloudInput, question)

        if (parsed.details.isEmpty() && parsed.shortAnswer.isBlank()) {
            val fallback = if (prefs.summaryLanguage == SummaryLanguage.UK) {
                "За даними джерел не вдалося знайти відповідь на питання."
            } else {
                "The sources do not contain enough information to answer."
            }
            parsed.copy(shortAnswer = fallback)
        } else {
            parsed
        }
    }
}