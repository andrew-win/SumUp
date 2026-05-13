package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SummaryLanguage
import com.andrewwin.sumup.domain.ai.SummaryResponseMapper
import com.andrewwin.sumup.domain.ai.AiPromptBuilder
import com.andrewwin.sumup.domain.ai.AdaptiveTextShrinker
import com.andrewwin.sumup.domain.ai.AiRequestSender
import com.andrewwin.sumup.domain.ai.ProportionalTextLimiter
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.summary.SummaryResult
import com.andrewwin.sumup.domain.support.UnsupportedStrategyException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AskQuestionAboutNewsUseCase @Inject constructor(
    private val userPrefsRepo: UserPreferencesRepository,
    private val articleRepo: ArticleRepository,
    private val shrinkTextUseCase: AdaptiveTextShrinker,
    private val limitTextsProportionallyUseCase: ProportionalTextLimiter,
    private val aiRequestSender: AiRequestSender,
    private val summaryResponseMapper: SummaryResponseMapper
) {
    suspend operator fun invoke(articles: List<Article>, question: String): Result<SummaryResult> = runCatching {
        val prefs = userPrefsRepo.preferences.first()
        if (prefs.aiStrategy == AiStrategy.LOCAL) throw UnsupportedStrategyException()

        val articlePayloads = articles.map { article ->
            val source = articleRepo.getSourceById(article.sourceId)
            val fullContent = articleRepo.fetchFullContent(article).ifBlank { article.content }

            val processedContent = if (prefs.aiStrategy == AiStrategy.ADAPTIVE) {
                shrinkTextUseCase(fullContent, prefs)
            } else {
                fullContent
            }
            QuestionArticlePayload(
                article = article,
                sourceName = source?.name?.trim()?.ifBlank { "Джерело" } ?: "Джерело",
                sourceUrl = article.url.ifBlank { source?.url.orEmpty() },
                content = processedContent
            )
        }
        val contentLimit = if (articlePayloads.size > 1) {
            prefs.aiMaxCharsNewsCluster
        } else {
            prefs.aiMaxCharsSingleArticle
        }
        val limitedContents = limitTextsProportionallyUseCase(
            texts = articlePayloads.map { it.content },
            maxTotalChars = contentLimit
        )

        val processedArticles = articlePayloads.zip(limitedContents).map { (payload, processedContent) ->
            """
            source_id: ${payload.article.id}
            source_name: ${payload.sourceName}
            source_url: ${payload.sourceUrl}
            title: ${payload.article.title}
            content: $processedContent
            """.trimIndent()
        }

        val cloudInput = processedArticles.joinToString(separator = "\n\n")

        val customPrompt = prefs.summaryPrompt.takeIf { prefs.isCustomSummaryPromptEnabled }
        val prompt = AiPromptBuilder.buildQuestionPrompt(prefs.summaryLanguage, question, customPrompt)
        val jsonResponse = aiRequestSender.sendSummaryRequest(prompt, cloudInput)
        val parsed = summaryResponseMapper.parseQuestion(jsonResponse, cloudInput, question)

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

    private data class QuestionArticlePayload(
        val article: Article,
        val sourceName: String,
        val sourceUrl: String,
        val content: String
    )
}
