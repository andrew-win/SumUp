package com.andrewwin.sumup.domain.summary.usecase

import android.content.Context
import com.andrewwin.sumup.R
import com.andrewwin.sumup.domain.article.model.Article
import com.andrewwin.sumup.domain.ai.prompt.AdaptiveTextShrinker
import com.andrewwin.sumup.domain.ai.prompt.AiPromptBuilder
import com.andrewwin.sumup.domain.ai.service.AiRequestSender
import com.andrewwin.sumup.domain.summary.formatter.LocalSummaryReason
import com.andrewwin.sumup.domain.summary.formatter.SummaryExecutionInfoFormatter
import com.andrewwin.sumup.domain.ai.service.SummaryExecutionInfoStore
import com.andrewwin.sumup.domain.ai.service.SummaryResponseMapper
import com.andrewwin.sumup.domain.ai.model.YoutubeSubtitleFetchSummary
import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import com.andrewwin.sumup.domain.settings.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.settings.model.AiStrategy
import com.andrewwin.sumup.domain.source.model.SourceType
import com.andrewwin.sumup.domain.summary.service.ExtractiveSummaryService
import com.andrewwin.sumup.domain.summary.service.LocalSummarySentenceSelector
import com.andrewwin.sumup.domain.summary.model.SummaryItem
import com.andrewwin.sumup.domain.summary.service.SummaryLimits
import com.andrewwin.sumup.domain.summary.model.SummaryResult
import com.andrewwin.sumup.domain.summary.model.SummarySourceRef
import com.andrewwin.sumup.domain.support.AllAiModelsFailedException
import com.andrewwin.sumup.domain.support.NoActiveModelException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SummarizeSingleArticleUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val articleRepository: ArticleRepository,
    private val getExtractiveSummaryUseCase: ExtractiveSummaryService,
    private val localSummarySentenceSelector: LocalSummarySentenceSelector,
    private val shrinkTextForAdaptiveStrategyUseCase: AdaptiveTextShrinker,
    private val aiRequestSender: AiRequestSender,
    private val summaryResponseMapper: SummaryResponseMapper,
    private val summaryExecutionInfoFormatter: SummaryExecutionInfoFormatter,
    private val summaryExecutionInfoStore: SummaryExecutionInfoStore
) {
    suspend operator fun invoke(article: Article): Result<SummaryResult> = runCatching {
        val source = articleRepository.getSourceById(article.sourceId)
        val sourceName = source?.name?.trim()?.ifBlank { "Джерело" } ?: "Джерело"
        val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
        val (contentToProcess, youtubeSubtitleSummary) = if (source?.type == SourceType.TELEGRAM) {
            article.content to YoutubeSubtitleFetchSummary()
        } else {
            val fullContent = articleRepository.fetchFullContent(article)
            fullContent.text.ifBlank { article.content } to YoutubeSubtitleFetchSummary.from(fullContent.status)
        }

        summarizeInternal(
            articleId = article.id,
            title = article.title,
            content = contentToProcess,
            sourceName = sourceName,
            sourceUrl = sourceUrl,
            youtubeSubtitleSummary = youtubeSubtitleSummary
        ).getOrThrow()
    }

    suspend operator fun invoke(title: String, content: String): Result<SummaryResult> = runCatching {
        summarizeInternal(
            articleId = -1,
            title = title,
            content = content,
            sourceName = "Текст",
            sourceUrl = "",
            youtubeSubtitleSummary = YoutubeSubtitleFetchSummary()
        ).getOrThrow()
    }

    private suspend fun summarizeInternal(
        articleId: Long,
        title: String,
        content: String,
        sourceName: String,
        sourceUrl: String,
        youtubeSubtitleSummary: YoutubeSubtitleFetchSummary
    ): Result<SummaryResult> = runCatching {
        val prefs = userPreferencesRepository.preferences.first()
        val strategy = prefs.aiStrategy
        val sourceRef = SummarySourceRef(sourceName, sourceUrl)

        if (strategy == AiStrategy.LOCAL ||
            (strategy == AiStrategy.ADAPTIVE && content.length < prefs.adaptiveExtractiveOnlyBelowChars)
        ) {
            val reason = if (strategy == AiStrategy.LOCAL) {
                LocalSummaryReason.SELECTED_LOCAL
            } else {
                LocalSummaryReason.TEXT_TOO_SHORT
            }
            summaryExecutionInfoStore.update(
                summaryExecutionInfoFormatter.buildLocalInfo(strategy, reason, youtubeSubtitleSummary)
            )
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
            val response = aiRequestSender.sendSummaryRequest(prompt, cloudInput)
            val parsedResult = summaryResponseMapper.parseSingle(response.content, cloudInput)
            summaryExecutionInfoStore.update(
                summaryExecutionInfoFormatter.buildCloudInfo(strategy, response, youtubeSubtitleSummary)
            )

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
            cloudResult.getOrElse { error ->
                summaryExecutionInfoStore.update(
                    when (error) {
                        is NoActiveModelException -> summaryExecutionInfoFormatter.buildLocalInfo(
                            strategy,
                            LocalSummaryReason.NO_API_KEYS,
                            youtubeSubtitleSummary
                        )
                        is AllAiModelsFailedException -> summaryExecutionInfoFormatter.buildLocalFallbackInfo(
                            strategy,
                            error.failures,
                            youtubeSubtitleSummary
                        )
                        else -> summaryExecutionInfoFormatter.buildLocalFallbackInfo(
                            strategy,
                            emptyList(),
                            youtubeSubtitleSummary
                        )
                    }
                )
                buildLocalSingleSummary(title, content, sourceRef)
            }
        } else {
            cloudResult.getOrThrow()
        }
    }

    private suspend fun buildLocalSingleSummary(
        title: String,
        content: String,
        sourceRef: SummarySourceRef
    ): SummaryResult.Single {
        if (!localSummarySentenceSelector.initialize()) {
            return buildFallbackLocalSingleSummary(title, content, sourceRef)
        }

        val candidates = getExtractiveSummaryUseCase.getTopCandidates(
            content,
            SummaryLimits.Single.localCandidateSentences
        )

        val sentences = localSummarySentenceSelector.selectDistinct(
            candidates = candidates,
            maxCount = SummaryLimits.Single.mainSentences + SummaryLimits.Single.maxPoints
        )

        return buildSingleSummaryResult(title, sourceRef, sentences)
    }

    private fun buildFallbackLocalSingleSummary(
        title: String,
        content: String,
        sourceRef: SummarySourceRef
    ): SummaryResult.Single {
        val sentences = getExtractiveSummaryUseCase(content, SummaryLimits.Single.localCandidateSentences)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(SummaryLimits.Single.mainSentences + SummaryLimits.Single.maxPoints)

        return buildSingleSummaryResult(title, sourceRef, sentences)
    }

    private fun buildSingleSummaryResult(
        title: String,
        sourceRef: SummarySourceRef,
        sentences: List<String>
    ): SummaryResult.Single {
        val main = sentences.firstOrNull()
        val points = sentences
            .drop(SummaryLimits.Single.mainSentences)
            .take(SummaryLimits.Single.maxPoints)
            .map { SummaryItem(text = it, sources = listOf(sourceRef)) }
        if (main.isNullOrBlank() || points.isEmpty()) {
            return buildShortTextFallbackSingleSummary(title, sourceRef)
        }
        return SummaryResult.Single(
            title = title,
            main = main,
            points = points,
            sources = listOf(sourceRef)
        )
    }

    private fun buildShortTextFallbackSingleSummary(
        title: String,
        sourceRef: SummarySourceRef
    ): SummaryResult.Single {
        val safeTitle = title.ifBlank { context.getString(R.string.summary_default_title) }
        return SummaryResult.Single(
            title = safeTitle,
            main = context.getString(R.string.summary_local_short_fallback_main, safeTitle),
            points = listOf(
                SummaryItem(
                    text = context.getString(R.string.summary_local_short_fallback_detail),
                    sources = listOf(sourceRef)
                )
            ),
            sources = listOf(sourceRef)
        )
    }
}
