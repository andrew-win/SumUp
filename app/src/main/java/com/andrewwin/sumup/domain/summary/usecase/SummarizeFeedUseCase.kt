package com.andrewwin.sumup.domain.summary.usecase

import android.content.Context
import android.util.Log
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
import com.andrewwin.sumup.domain.feed.model.FeedSummaryArticle
import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import com.andrewwin.sumup.domain.feed.dedup.ThresholdSimilarityResolver
import com.andrewwin.sumup.domain.settings.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.settings.model.AiStrategy
import com.andrewwin.sumup.domain.source.model.SourceType
import com.andrewwin.sumup.domain.summary.model.DigestTheme
import com.andrewwin.sumup.domain.summary.model.SummaryItem
import com.andrewwin.sumup.domain.summary.service.SummaryLimits
import com.andrewwin.sumup.domain.summary.model.SummaryResult
import com.andrewwin.sumup.domain.summary.model.SummarySourceRef
import com.andrewwin.sumup.domain.support.AllAiModelsFailedException
import com.andrewwin.sumup.domain.support.NoActiveModelException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

class SummarizeFeedUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val articleRepository: ArticleRepository,
    private val shrinkTextForAdaptiveStrategyUseCase: AdaptiveTextShrinker,
    private val aiRequestSender: AiRequestSender,
    private val summaryResponseMapper: SummaryResponseMapper,
    private val thresholdSimilarityResolver: ThresholdSimilarityResolver,
    private val summaryExecutionInfoFormatter: SummaryExecutionInfoFormatter,
    private val summaryExecutionInfoStore: SummaryExecutionInfoStore
) {
    suspend fun summarizeArticles(articles: List<Article>): Result<SummaryResult> = runCatching {
        if (articles.isEmpty()) return@runCatching SummaryResult.Digest(emptyList())
        val feedSummaryArticles = buildFeedSummaryArticles(articles)
        buildSummary(feedSummaryArticles)
    }

    suspend operator fun invoke(feedSummaryArticles: List<FeedSummaryArticle>): Result<SummaryResult> = runCatching {
        if (feedSummaryArticles.isEmpty()) return@runCatching SummaryResult.Digest(emptyList())
        buildSummary(feedSummaryArticles)
    }

    private suspend fun buildSummary(feedSummaryArticles: List<FeedSummaryArticle>): SummaryResult {
        val prefs = userPreferencesRepository.preferences.first()
        val strategy = prefs.aiStrategy

        if (strategy == AiStrategy.LOCAL) {
            summaryExecutionInfoStore.update(
                summaryExecutionInfoFormatter.buildLocalInfo(strategy, LocalSummaryReason.SELECTED_LOCAL)
            )
            return buildLocalSummary(feedSummaryArticles)
        }

        return buildCloudOrAdaptiveSummary(
            feedSummaryArticles = feedSummaryArticles,
            strategy = strategy
        )
    }

    private suspend fun buildLocalSummary(feedSummaryArticles: List<FeedSummaryArticle>): SummaryResult.Digest {
        val topArticles = feedSummaryArticles
            .sortedByDescending { article ->
                article.baseImportanceScore + article.similarArticlesCount * LOCAL_SIMILAR_NEWS_BONUS_PER_MATCH
            }
            .take(SummaryLimits.Digest.maxLocalArticles)

        val items = topArticles.map { candidate ->
            val article = candidate.article
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceName = source?.name?.trim()?.ifBlank { sourceFallbackName } ?: sourceFallbackName
            val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
            SummaryItem(
                text = article.title,
                sources = listOf(SummarySourceRef(sourceName, sourceUrl))
            )
        }

        return SummaryResult.Digest(
            themes = listOf(
                DigestTheme(
                    title = mainNewsThemeTitle,
                    items = items
                )
            )
        )
    }

    private suspend fun buildCloudOrAdaptiveSummary(
        feedSummaryArticles: List<FeedSummaryArticle>,
        strategy: AiStrategy
    ): SummaryResult {
        val prefs = userPreferencesRepository.preferences.first()
        val maxTotalChars = prefs.aiMaxCharsTotal.coerceAtLeast(MIN_TOTAL_CHARS)
        var remainingTotal = maxTotalChars
        var availablePayloadChars = 0
        var processedContentChars = 0
        var originalContentChars = 0
        var includedArticlesCount = 0
        var partiallyIncludedArticlesCount = 0
        val totalArticlesCount = feedSummaryArticles.size
        val cloudArticles = loadCloudFeedArticles(feedSummaryArticles, prefs)
        val youtubeSubtitleSummary = cloudArticles.fold(YoutubeSubtitleFetchSummary()) { total, article ->
            total + article.youtubeSubtitleSummary
        }
        val cloudInput = buildString {
            append(PAYLOAD_HEADER)
            remainingTotal -= PAYLOAD_HEADER.length
            availablePayloadChars += PAYLOAD_HEADER.length

            for (cloudArticle in cloudArticles) {
                val feedSummaryArticle = cloudArticle.feedSummaryArticle
                val article = feedSummaryArticle.article
                val contentToProcess = cloudArticle.content
                originalContentChars += contentToProcess.length

                val maxCharsForFeedItem = if (feedSummaryArticle.similarArticlesCount > 0) {
                    prefs.aiMaxCharsFeedCluster
                } else {
                    prefs.aiMaxCharsSingleFeedArticle
                }
                val textForCloud = if (strategy == AiStrategy.ADAPTIVE) {
                    shrinkTextForAdaptiveStrategyUseCase
                        .shrinkDigestArticle(contentToProcess)
                        .take(maxCharsForFeedItem.coerceAtLeast(0))
                } else {
                    contentToProcess.take(maxCharsForFeedItem.coerceAtLeast(0))
                }
                processedContentChars += textForCloud.length

                val block = buildPayloadRow(
                    id = article.id,
                    sourceName = cloudArticle.sourceName,
                    sourceUrl = cloudArticle.sourceUrl,
                    title = article.title,
                    content = textForCloud
                )
                availablePayloadChars += block.length

                if (remainingTotal <= 0) {
                    continue
                } else if (block.length <= remainingTotal) {
                    append(block)
                    remainingTotal -= block.length
                    includedArticlesCount++
                } else {
                    append(block.take(remainingTotal))
                    partiallyIncludedArticlesCount++
                    remainingTotal = 0
                }
            }
        }
        val droppedPayloadChars = (availablePayloadChars - cloudInput.length).coerceAtLeast(0)
        val droppedContentChars = (originalContentChars - processedContentChars).coerceAtLeast(0)
        Log.d(
            CLOUD_CHARS_LOG_TAG,
            "strategy=$strategy " +
                "limit=$maxTotalChars " +
                "sent=${cloudInput.length} " +
                "droppedByTotalLimit=$droppedPayloadChars " +
                "availablePayload=$availablePayloadChars " +
                "originalContent=$originalContentChars " +
                "processedContent=$processedContentChars " +
                "droppedByArticleLimitOrAdaptive=$droppedContentChars " +
                "includedArticles=$includedArticlesCount " +
                "partiallyIncludedArticles=$partiallyIncludedArticlesCount " +
                "totalArticles=$totalArticlesCount"
        )

        val customPrompt = prefs.summaryPrompt.takeIf { prefs.isCustomSummaryPromptEnabled }
        val prompt = AiPromptBuilder.buildFeedDigestPrompt(prefs.summaryLanguage, customPrompt)
        val cloudResult = runCatching {
            val response = aiRequestSender.sendSummaryRequest(prompt, cloudInput)
            val parsed = summaryResponseMapper.parseFeed(response.content, cloudInput)
            summaryExecutionInfoStore.update(
                summaryExecutionInfoFormatter.buildCloudInfo(strategy, response, youtubeSubtitleSummary)
            )
            parsed
        }
        return if (strategy == AiStrategy.ADAPTIVE) {
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
                buildLocalSummary(feedSummaryArticles)
            }
        } else {
            cloudResult.getOrThrow()
        }
    }

    private suspend fun buildFeedSummaryArticles(articles: List<Article>): List<FeedSummaryArticle> {
        val prefs = userPreferencesRepository.preferences.first()
        val similarityByArticleId = thresholdSimilarityResolver.resolveSimilarityCounts(
            articles = articles,
            prefs = prefs,
            persistComputed = false,
            allowOnDemandComputation = false
        )

        return articles.map { article ->
            FeedSummaryArticle(
                article = article,
                similarArticlesCount = similarityByArticleId[article.id] ?: 0,
                baseImportanceScore = article.importanceScore
            )
        }
    }

    private data class CloudFeedArticle(
        val feedSummaryArticle: FeedSummaryArticle,
        val sourceName: String,
        val sourceUrl: String,
        val content: String,
        val youtubeSubtitleSummary: YoutubeSubtitleFetchSummary
    )

    private suspend fun loadCloudFeedArticles(
        feedSummaryArticles: List<FeedSummaryArticle>,
        prefs: com.andrewwin.sumup.domain.settings.model.UserSettings
    ): List<CloudFeedArticle> = coroutineScope {
        val sourceByArticleId = feedSummaryArticles.map { feedSummaryArticle ->
            async {
                val article = feedSummaryArticle.article
                article.id to articleRepository.getSourceById(article.sourceId)
            }
        }.awaitAll().toMap()

        var youtubeFullTextArticlesCount = 0
        val shouldFetchFullContentByArticleId = feedSummaryArticles.associate { feedSummaryArticle ->
            val article = feedSummaryArticle.article
            val sourceType = sourceByArticleId[article.id]?.type
            val shouldFetch = prefs.isFeedSummaryUseFullTextEnabled &&
                sourceType != SourceType.TELEGRAM &&
                (sourceType != SourceType.YOUTUBE ||
                    youtubeFullTextArticlesCount < MAX_YOUTUBE_FULL_TEXT_ARTICLES_IN_FEED_SUMMARY)
            if (shouldFetch && sourceType == SourceType.YOUTUBE) {
                youtubeFullTextArticlesCount++
            }
            article.id to shouldFetch
        }

        val semaphore = Semaphore(CONTENT_LOADING_PARALLELISM)
        feedSummaryArticles.map { feedSummaryArticle ->
            async {
                semaphore.withPermit {
                    val article = feedSummaryArticle.article
                    val source = sourceByArticleId[article.id]
                    val sourceName = source?.name?.trim()?.ifBlank { sourceFallbackName } ?: sourceFallbackName
                    val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
                    val shouldFetchFullContent = shouldFetchFullContentByArticleId[article.id] == true
                    val (content, youtubeSubtitleSummary) = if (shouldFetchFullContent) {
                        val fullContent = articleRepository.fetchFullContent(article)
                        fullContent.text.ifBlank { article.content } to YoutubeSubtitleFetchSummary.from(fullContent.status)
                    } else {
                        article.content to YoutubeSubtitleFetchSummary()
                    }

                    CloudFeedArticle(
                        feedSummaryArticle = feedSummaryArticle,
                        sourceName = sourceName,
                        sourceUrl = sourceUrl,
                        content = content,
                        youtubeSubtitleSummary = youtubeSubtitleSummary
                    )
                }
            }
        }.awaitAll()
    }

    private fun buildPayloadRow(
        id: Long,
        sourceName: String,
        sourceUrl: String,
        title: String,
        content: String
    ): String {
        return listOf(
            id.toString(),
            sourceName.cleanPayloadField(),
            sourceUrl.cleanPayloadField(),
            title.cleanPayloadField(),
            content.cleanPayloadField()
        ).joinToString(PAYLOAD_FIELD_SEPARATOR) + "\n"
    }

    private fun String.cleanPayloadField(): String {
        return replace(PAYLOAD_FIELD_SEPARATOR, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private val sourceFallbackName: String
        get() = context.getString(R.string.summary_source_fallback)

    private val mainNewsThemeTitle: String
        get() = context.getString(R.string.summary_digest_main_news_title)

    companion object {
        private const val LOCAL_SIMILAR_NEWS_BONUS_PER_MATCH = 0.25f
        private const val MAX_YOUTUBE_FULL_TEXT_ARTICLES_IN_FEED_SUMMARY = 7
        private const val MIN_TOTAL_CHARS = 1000
        private const val CLOUD_CHARS_LOG_TAG = "CloudChars"
        private const val PAYLOAD_FIELD_SEPARATOR = "|"
        private const val PAYLOAD_HEADER = "# id|src|url|title|content\n"
        private val CONTENT_LOADING_PARALLELISM = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
