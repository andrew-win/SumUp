package com.andrewwin.sumup.domain.summary.usecase

import com.andrewwin.sumup.domain.article.model.Article
import com.andrewwin.sumup.domain.ai.prompt.AdaptiveTextShrinker
import com.andrewwin.sumup.domain.ai.prompt.AiPromptBuilder
import com.andrewwin.sumup.domain.ai.service.AiRequestSender
import com.andrewwin.sumup.domain.ai.prompt.ProportionalTextLimiter
import com.andrewwin.sumup.domain.summary.formatter.SummaryExecutionInfoFormatter
import com.andrewwin.sumup.domain.ai.service.SummaryExecutionInfoStore
import com.andrewwin.sumup.domain.ai.service.SummaryResponseMapper
import com.andrewwin.sumup.domain.ai.model.YoutubeSubtitleFetchSummary
import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import com.andrewwin.sumup.domain.settings.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.settings.model.AiStrategy
import com.andrewwin.sumup.domain.settings.model.SummaryLanguage
import com.andrewwin.sumup.domain.summary.model.SummaryResult
import com.andrewwin.sumup.domain.support.UnsupportedStrategyException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AskQuestionUseCase @Inject constructor(
    private val userPrefsRepo: UserPreferencesRepository,
    private val articleRepo: ArticleRepository,
    private val shrinkTextUseCase: AdaptiveTextShrinker,
    private val limitTextsProportionallyUseCase: ProportionalTextLimiter,
    private val aiPromptBuilder: AiPromptBuilder,
    private val aiRequestSender: AiRequestSender,
    private val summaryResponseMapper: SummaryResponseMapper,
    private val summaryExecutionInfoFormatter: SummaryExecutionInfoFormatter,
    private val summaryExecutionInfoStore: SummaryExecutionInfoStore
) {
    suspend operator fun invoke(articles: List<Article>, question: String): Result<SummaryResult> = runCatching {
        val prefs = userPrefsRepo.preferences.first()
        if (prefs.aiStrategy == AiStrategy.LOCAL) throw UnsupportedStrategyException()

        val articlePayloads = articles.map { article ->
            val source = articleRepo.getSourceById(article.sourceId)
            val fullContent = articleRepo.fetchFullContent(article)
            val contentToProcess = fullContent.text.ifBlank { article.content }

            val processedContent = if (prefs.aiStrategy == AiStrategy.ADAPTIVE) {
                shrinkTextUseCase(contentToProcess, prefs)
            } else {
                contentToProcess
            }
            QuestionArticlePayload(
                article = article,
                sourceName = source?.name?.trim()?.ifBlank { "Джерело" } ?: "Джерело",
                sourceUrl = article.url.ifBlank { source?.url.orEmpty() },
                content = processedContent,
                youtubeSubtitleSummary = YoutubeSubtitleFetchSummary.from(fullContent.status)
            )
        }
        val youtubeSubtitleSummary = articlePayloads.fold(YoutubeSubtitleFetchSummary()) { total, payload ->
            total + payload.youtubeSubtitleSummary
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
        val prompt = aiPromptBuilder.buildQuestionPrompt(prefs.summaryLanguage, question, customPrompt)
        val response = aiRequestSender.sendSummaryRequest(prompt, cloudInput)
        val parsed = summaryResponseMapper.parseQuestion(response.content, cloudInput, question)
        summaryExecutionInfoStore.update(
            summaryExecutionInfoFormatter.buildCloudInfo(prefs.aiStrategy, response, youtubeSubtitleSummary)
        )

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
        val content: String,
        val youtubeSubtitleSummary: YoutubeSubtitleFetchSummary
    )
}
