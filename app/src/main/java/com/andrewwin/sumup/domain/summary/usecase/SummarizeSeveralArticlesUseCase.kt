package com.andrewwin.sumup.domain.summary.usecase

import com.andrewwin.sumup.domain.article.model.Article
import com.andrewwin.sumup.domain.ai.prompt.AdaptiveTextShrinker
import com.andrewwin.sumup.domain.ai.prompt.AiPromptBuilder
import com.andrewwin.sumup.domain.ai.service.AiRequestSender
import com.andrewwin.sumup.domain.summary.formatter.LocalSummaryReason
import com.andrewwin.sumup.domain.ai.prompt.ProportionalTextLimiter
import com.andrewwin.sumup.domain.ai.model.RemoteContentFetchStatus
import com.andrewwin.sumup.domain.summary.formatter.SummaryExecutionInfoFormatter
import com.andrewwin.sumup.domain.ai.service.SummaryExecutionInfoStore
import com.andrewwin.sumup.domain.ai.service.SummaryResponseMapper
import com.andrewwin.sumup.domain.ai.model.YoutubeSubtitleFetchSummary
import com.andrewwin.sumup.domain.article.repository.FullArticleContent
import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import com.andrewwin.sumup.domain.settings.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.settings.model.AiStrategy
import com.andrewwin.sumup.domain.source.model.SourceType
import com.andrewwin.sumup.domain.summary.model.ExtractiveSentenceCandidate
import com.andrewwin.sumup.domain.settings.model.UserSettings
import com.andrewwin.sumup.domain.summary.service.ExtractiveSummaryService
import com.andrewwin.sumup.domain.summary.service.LocalSummarySentenceSelector
import com.andrewwin.sumup.domain.summary.model.SummaryItem
import com.andrewwin.sumup.domain.summary.service.SummaryLimits
import com.andrewwin.sumup.domain.summary.model.SummaryResult
import com.andrewwin.sumup.domain.summary.model.SummarySourceRef
import com.andrewwin.sumup.domain.support.AllAiModelsFailedException
import com.andrewwin.sumup.domain.support.DispatcherProvider
import com.andrewwin.sumup.domain.support.LocalModelMissingException
import com.andrewwin.sumup.domain.support.NoActiveModelException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject


class SummarizeSeveralArticlesUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val articleRepository: ArticleRepository,
    private val shrinkTextForAdaptiveStrategyUseCase: AdaptiveTextShrinker,
    private val aiRequestSender: AiRequestSender,
    private val summaryResponseMapper: SummaryResponseMapper,
    private val limitTextsProportionallyUseCase: ProportionalTextLimiter,
    private val getExtractiveSummaryUseCase: ExtractiveSummaryService,
    private val localSummarySentenceSelector: LocalSummarySentenceSelector,
    private val dispatcherProvider: DispatcherProvider,
    private val summaryExecutionInfoFormatter: SummaryExecutionInfoFormatter,
    private val summaryExecutionInfoStore: SummaryExecutionInfoStore
) {
    suspend operator fun invoke(articles: List<Article>): Result<SummaryResult.Compare> = withContext(dispatcherProvider.default) {
        if (articles.size < 2) {
            return@withContext Result.failure(IllegalStateException("Недостатньо джерел для порівняння."))
        }

        val prefs = userPreferencesRepository.preferences.first()
        val strategy = prefs.aiStrategy

        // 1. Local Strategy
        if (strategy == AiStrategy.LOCAL) {
            return@withContext runCatching {
                val localComparison = performLocalComparison(articles, prefs)
                summaryExecutionInfoStore.update(
                    summaryExecutionInfoFormatter.buildLocalInfo(
                        strategy,
                        LocalSummaryReason.SELECTED_LOCAL,
                        localComparison.youtubeSubtitleSummary
                    )
                )
                localComparison.summary
            }
        }

        val cloudArticles = loadCloudCompareArticles(articles)
        val youtubeSubtitleSummary = cloudArticles.fold(YoutubeSubtitleFetchSummary()) { total, article ->
            total + article.youtubeSubtitleSummary
        }
        val totalContentLength = cloudArticles.sumOf { it.content.length }

        if (strategy == AiStrategy.ADAPTIVE && totalContentLength < prefs.adaptiveExtractiveOnlyBelowChars) {
            summaryExecutionInfoStore.update(
                summaryExecutionInfoFormatter.buildLocalInfo(
                    strategy,
                    LocalSummaryReason.TEXT_TOO_SHORT,
                    youtubeSubtitleSummary
                )
            )
            return@withContext runCatching { performLocalComparison(articles, prefs).summary }
        }

        val processedTexts = cloudArticles.map { article ->
            if (strategy == AiStrategy.ADAPTIVE) {
                shrinkTextForAdaptiveStrategyUseCase.shrinkByAdaptiveRange(
                    text = article.content,
                    prefs = prefs,
                    rangeLength = totalContentLength
                )
            } else {
                article.content
            }
        }
        val limitedTexts = limitTextsProportionallyUseCase(
            texts = processedTexts,
            maxTotalChars = prefs.aiMaxCharsNewsCluster
        )

        val cloudInput = buildString {
            for ((article, textForCloud) in cloudArticles.zip(limitedTexts)) {
                append("source_id: ${article.id}\n")
                append("source_name: ${article.sourceName}\n")
                append("source_url: ${article.sourceUrl}\n")
                append("title: ${article.title}\n")
                append("content: $textForCloud\n\n")
            }
        }

        val customPrompt = prefs.summaryPrompt.takeIf { prefs.isCustomSummaryPromptEnabled }
        val prompt = AiPromptBuilder.buildComparePrompt(prefs.summaryLanguage, customPrompt)

        val cloudResult = runCatching {
            val response = aiRequestSender.sendSummaryRequest(prompt, cloudInput)
            val parsed = summaryResponseMapper.parseCompare(response.content, cloudInput)
            summaryExecutionInfoStore.update(
                summaryExecutionInfoFormatter.buildCloudInfo(strategy, response, youtubeSubtitleSummary)
            )
            parsed
        }

        return@withContext if (strategy == AiStrategy.ADAPTIVE) {
            cloudResult.recoverCatching { error ->
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
                performLocalComparison(articles, prefs).summary
            }
        } else {
            cloudResult
        }
    }

    private data class CloudCompareArticle(
        val id: Long,
        val sourceName: String,
        val sourceUrl: String,
        val title: String,
        val content: String,
        val youtubeSubtitleSummary: YoutubeSubtitleFetchSummary
    )

    private suspend fun loadCloudCompareArticles(articles: List<Article>): List<CloudCompareArticle> = coroutineScope {
        val semaphore = Semaphore(CONTENT_LOADING_PARALLELISM)
        articles.map { article ->
            async(dispatcherProvider.io) {
                semaphore.withPermit {
                    val source = articleRepository.getSourceById(article.sourceId)
                    val sourceName = source?.name?.trim()?.ifBlank { SOURCE_FALLBACK_NAME } ?: SOURCE_FALLBACK_NAME
                    val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
                    val (contentToProcess, youtubeSubtitleSummary) = if (source?.type == SourceType.TELEGRAM) {
                        article.content to YoutubeSubtitleFetchSummary()
                    } else {
                        val fullContent = articleRepository.fetchFullContent(article)
                        fullContent.text.ifBlank { article.content } to YoutubeSubtitleFetchSummary.from(fullContent.status)
                    }

                    CloudCompareArticle(
                        id = article.id,
                        sourceName = sourceName,
                        sourceUrl = sourceUrl,
                        title = article.title,
                        content = contentToProcess,
                        youtubeSubtitleSummary = youtubeSubtitleSummary
                    )
                }
            }
        }.awaitAll()
    }

    private data class LocalComparisonResult(
        val summary: SummaryResult.Compare,
        val youtubeSubtitleSummary: YoutubeSubtitleFetchSummary
    )

    private data class LocalClusterSentenceCandidate(
        val candidate: ExtractiveSentenceCandidate,
        val source: SummarySourceRef,
        val articleId: Long
    )

    private suspend fun performLocalComparison(
        articles: List<Article>,
        prefs: UserSettings
    ): LocalComparisonResult {
        if (articles.isEmpty()) {
            return LocalComparisonResult(
                summary = SummaryResult.Compare(points = emptyList()),
                youtubeSubtitleSummary = YoutubeSubtitleFetchSummary()
            )
        }

        if (!localSummarySentenceSelector.initialize()) {
            throw LocalModelMissingException()
        }

        var youtubeSubtitleSummary = YoutubeSubtitleFetchSummary()
        val candidatesByArticle = articles.associate { article ->
            val candidates = buildLocalClusterSentenceCandidates(article)
            youtubeSubtitleSummary += candidates.youtubeSubtitleSummary
            article.id to selectDistinctLocalClusterCandidates(candidates.items)
        }

        val requiredSourceItems = buildRequiredLocalClusterSummaryItems(
            articles = articles,
            candidatesByArticle = candidatesByArticle
        )
        val selectedItems = requiredSourceItems.toMutableList()
        val selectedTexts = requiredSourceItems.map { it.text }.toMutableList()

        val remainingSlots = (SummaryLimits.LocalClusterSummary.maxSummarySentences - selectedItems.size)
            .coerceAtLeast(0)
        if (remainingSlots > 0) {
            val remainingCandidates = articles
                .flatMap { article -> candidatesByArticle[article.id].orEmpty() }
                .filterNot { candidate -> selectedTexts.any { it == candidate.candidate.text } }
                .sortedByDescending { it.candidate.score }

            for (candidate in remainingCandidates) {
                if (selectedItems.size >= SummaryLimits.LocalClusterSummary.maxSummarySentences) break
                if (localSummarySentenceSelector.isNearDuplicate(candidate.candidate.text, selectedTexts)) continue

                selectedItems += SummaryItem(
                    text = candidate.candidate.text,
                    sources = listOf(candidate.source)
                )
                selectedTexts += candidate.candidate.text
            }
        }

        return LocalComparisonResult(
            summary = SummaryResult.Compare(
                main = selectedItems.firstOrNull()?.text,
                points = selectedItems.drop(SummaryLimits.Compare.mainSentences)
            ),
            youtubeSubtitleSummary = youtubeSubtitleSummary
        )
    }

    private data class LocalClusterSentenceCandidates(
        val items: List<LocalClusterSentenceCandidate>,
        val youtubeSubtitleSummary: YoutubeSubtitleFetchSummary
    )

    private suspend fun buildLocalClusterSentenceCandidates(article: Article): LocalClusterSentenceCandidates {
        val source = articleRepository.getSourceById(article.sourceId)
        val sourceName = source?.name?.trim()?.ifBlank { "Джерело" } ?: "Джерело"
        val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
        val sourceMeta = SummarySourceRef(name = sourceName, url = sourceUrl)

        val fullContent = if (source?.type == SourceType.TELEGRAM) {
            FullArticleContent(
                text = article.content,
                status = RemoteContentFetchStatus.SUCCESS
            )
        } else {
            articleRepository.fetchFullContent(article)
        }
        val items = getExtractiveSummaryUseCase.getTopCandidates(
            fullContent.text,
            SummaryLimits.LocalClusterSummary.candidateSentencesPerSource
        )
            .map { sentenceCandidate ->
                LocalClusterSentenceCandidate(
                    candidate = sentenceCandidate,
                    source = sourceMeta,
                    articleId = article.id
                )
            }
        return LocalClusterSentenceCandidates(
            items = items,
            youtubeSubtitleSummary = YoutubeSubtitleFetchSummary.from(fullContent.status)
        )
    }

    private suspend fun selectDistinctLocalClusterCandidates(
        candidates: List<LocalClusterSentenceCandidate>
    ): List<LocalClusterSentenceCandidate> {
        if (candidates.isEmpty()) return emptyList()

        val selectedCandidates = localSummarySentenceSelector.selectDistinctCandidates(
            candidates = candidates.map { it.candidate },
            maxCount = SummaryLimits.LocalClusterSummary.candidateSentencesPerSource
        )
        val sourceCandidateByText = candidates.associateBy { it.candidate.text }
        return selectedCandidates.mapNotNull { candidate -> sourceCandidateByText[candidate.text] }
    }

    private suspend fun buildRequiredLocalClusterSummaryItems(
        articles: List<Article>,
        candidatesByArticle: Map<Long, List<LocalClusterSentenceCandidate>>
    ): List<SummaryItem> {
        val maxRequiredSources = SummaryLimits.LocalClusterSummary.maxSummarySentences /
            SummaryLimits.LocalClusterSummary.minSentencesPerSource
        val selectedTexts = mutableListOf<String>()
        val items = mutableListOf<SummaryItem>()

        for (article in articles.take(maxRequiredSources)) {
            val articleCandidates = candidatesByArticle[article.id].orEmpty()
            val selectedCandidate = articleCandidates.firstOrNull { candidate ->
                !localSummarySentenceSelector.isNearDuplicate(candidate.candidate.text, selectedTexts)
            } ?: articleCandidates.maxByOrNull { it.candidate.score }

            if (selectedCandidate != null) {
                items += SummaryItem(
                    text = selectedCandidate.candidate.text,
                    sources = listOf(selectedCandidate.source)
                )
                selectedTexts += selectedCandidate.candidate.text
            }
        }

        return items
    }

    private companion object {
        private val CONTENT_LOADING_PARALLELISM = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        private const val SOURCE_FALLBACK_NAME = "Джерело"
    }
}
