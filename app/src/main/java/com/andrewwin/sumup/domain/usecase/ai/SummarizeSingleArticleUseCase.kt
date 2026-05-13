package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.ai.SummaryResponseMapper
import com.andrewwin.sumup.domain.ai.AiPromptBuilder
import com.andrewwin.sumup.domain.ai.AdaptiveTextShrinker
import com.andrewwin.sumup.domain.ai.AiRequestSender
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.summary.SummaryItem
import com.andrewwin.sumup.domain.summary.SummaryLimits
import com.andrewwin.sumup.domain.summary.SummaryResult
import com.andrewwin.sumup.domain.summary.SummarySourceRef
import com.andrewwin.sumup.domain.summary.ExtractiveSummaryService
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SummarizeSingleArticleUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val articleRepository: ArticleRepository,
    private val getExtractiveSummaryUseCase: ExtractiveSummaryService,
    private val shrinkTextForAdaptiveStrategyUseCase: AdaptiveTextShrinker,
    private val aiRequestSender: AiRequestSender,
    private val summaryResponseMapper: SummaryResponseMapper
) {
    suspend operator fun invoke(article: Article): Result<SummaryResult> = runCatching {
        val source = articleRepository.getSourceById(article.sourceId)
        val sourceName = source?.name?.trim()?.ifBlank { "Джерело" } ?: "Джерело"
        val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
        
        val fullContent = articleRepository.fetchFullContent(article)
        val contentToProcess = fullContent.ifBlank { article.content }

        summarizeInternal(
            articleId = article.id,
            title = article.title,
            content = contentToProcess,
            sourceName = sourceName,
            sourceUrl = sourceUrl
        ).getOrThrow()
    }

    suspend operator fun invoke(title: String, content: String): Result<SummaryResult> = runCatching {
        summarizeInternal(
            articleId = -1,
            title = title,
            content = content,
            sourceName = "Текст",
            sourceUrl = ""
        ).getOrThrow()
    }

    private suspend fun summarizeInternal(
        articleId: Long,
        title: String,
        content: String,
        sourceName: String,
        sourceUrl: String
    ): Result<SummaryResult> = runCatching {
        val prefs = userPreferencesRepository.preferences.first()
        val strategy = prefs.aiStrategy
        val sourceRef = SummarySourceRef(sourceName, sourceUrl)

        if (strategy == AiStrategy.LOCAL ||
            (strategy == AiStrategy.ADAPTIVE && content.length < prefs.adaptiveExtractiveOnlyBelowChars)
        ) {
            return@runCatching buildLocalSingleSummary(title, content, sourceRef)
        }

        // 2. Adaptive (Shrink text)
        val textForCloud = if (strategy == AiStrategy.ADAPTIVE) {
            shrinkTextForAdaptiveStrategyUseCase(content, prefs)
        } else {
            content.take(prefs.aiMaxCharsSingleArticle)
        }

        // 3. Build Prompt & Cloud Input
        val customPrompt = prefs.summaryPrompt.takeIf { prefs.isCustomSummaryPromptEnabled }
        val prompt = AiPromptBuilder.buildSingleArticlePrompt(prefs.summaryLanguage, customPrompt)
        val cloudInput = buildString {
            append("source_id: $articleId\n")
            append("source_name: $sourceName\n")
            append("source_url: $sourceUrl\n")
            append("title: $title\n")
            append("content: $textForCloud")
        }

        val cloudResult = runCatching {
            val jsonResponse = aiRequestSender.sendSummaryRequest(prompt, cloudInput)
            val parsedResult = summaryResponseMapper.parseSingle(jsonResponse, cloudInput)

            if (parsedResult.sources.isEmpty()) {
                parsedResult.copy(
                    points = parsedResult.points.map { if (it.sources.isEmpty()) it.copy(sources = listOf(sourceRef)) else it },
                    sources = listOf(sourceRef)
                )
            } else {
                parsedResult
            }
        }

        if (strategy == AiStrategy.ADAPTIVE) {
            cloudResult.getOrElse { buildLocalSingleSummary(title, content, sourceRef) }
        } else {
            cloudResult.getOrThrow()
        }
    }

    private fun buildLocalSingleSummary(
        title: String,
        content: String,
        sourceRef: SummarySourceRef
    ): SummaryResult.Single {
        val sentences = getExtractiveSummaryUseCase(content, SummaryLimits.Single.localExtractiveSentences)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(SummaryLimits.Single.localExtractiveSentences)

        val main = sentences.firstOrNull()
        val points = sentences
            .drop(SummaryLimits.Single.mainSentences)
            .take(SummaryLimits.Single.maxPoints)
            .map { SummaryItem(text = it, sources = listOf(sourceRef)) }
        return SummaryResult.Single(
            title = title,
            main = main,
            points = points,
            sources = listOf(sourceRef)
        )
    }
}
