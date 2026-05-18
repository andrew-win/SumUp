package com.andrewwin.sumup.domain.usecase.ai

import android.content.Context
import android.util.Log
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.ai.SummaryResponseMapper
import com.andrewwin.sumup.domain.ai.AiPromptBuilder
import com.andrewwin.sumup.domain.ai.AdaptiveTextShrinker
import com.andrewwin.sumup.domain.ai.AiRequestSender
import com.andrewwin.sumup.domain.feed.FeedSummaryArticle
import com.andrewwin.sumup.domain.news.SimilarityScorer
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.summary.DigestTheme
import com.andrewwin.sumup.domain.summary.SummaryItem
import com.andrewwin.sumup.domain.summary.SummaryLimits
import com.andrewwin.sumup.domain.summary.SummaryResult
import com.andrewwin.sumup.domain.summary.SummarySourceRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetFeedSummaryUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val articleRepository: ArticleRepository,
    private val shrinkTextForAdaptiveStrategyUseCase: AdaptiveTextShrinker,
    private val aiRequestSender: AiRequestSender,
    private val summaryResponseMapper: SummaryResponseMapper,
    private val similarityScorer: SimilarityScorer
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
        var youtubeFullTextArticlesCount = 0
        val totalArticlesCount = feedSummaryArticles.size
        val cloudInput = buildString {
            append(PAYLOAD_HEADER)
            remainingTotal -= PAYLOAD_HEADER.length
            availablePayloadChars += PAYLOAD_HEADER.length

            for (feedSummaryArticle in feedSummaryArticles) {
                val article = feedSummaryArticle.article
                val source = articleRepository.getSourceById(article.sourceId)
                val sourceName = source?.name?.trim()?.ifBlank { sourceFallbackName } ?: sourceFallbackName
                val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()

                val shouldFetchFullContent = prefs.isFeedSummaryUseFullTextEnabled &&
                    (source?.type != SourceType.YOUTUBE ||
                        youtubeFullTextArticlesCount < MAX_YOUTUBE_FULL_TEXT_ARTICLES_IN_FEED_SUMMARY)
                if (shouldFetchFullContent && source?.type == SourceType.YOUTUBE) {
                    youtubeFullTextArticlesCount++
                }
                val contentToProcess = if (shouldFetchFullContent) {
                    articleRepository.fetchFullContent(article).ifBlank { article.content }
                } else {
                    article.content
                }
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
                    sourceName = sourceName,
                    sourceUrl = sourceUrl,
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
            val jsonResponse = aiRequestSender.sendSummaryRequest(prompt, cloudInput)
            summaryResponseMapper.parseFeed(jsonResponse, cloudInput)
        }
        return if (strategy == AiStrategy.ADAPTIVE) {
            cloudResult.getOrElse { buildLocalSummary(feedSummaryArticles) }
        } else {
            cloudResult.getOrThrow()
        }
    }

    private suspend fun buildFeedSummaryArticles(articles: List<Article>): List<FeedSummaryArticle> {
        val prefs = userPreferencesRepository.preferences.first()
        val strategyKey = similarityScorer.similarityCacheKeyForStrategy(prefs.deduplicationStrategy)
        val threshold = when (prefs.deduplicationStrategy) {
            com.andrewwin.sumup.data.local.entities.DeduplicationStrategy.LOCAL -> prefs.localDeduplicationThreshold
            com.andrewwin.sumup.data.local.entities.DeduplicationStrategy.CLOUD -> prefs.cloudDeduplicationThreshold
        }
        val similarityByArticleId = articleRepository
            .getSimilaritiesForArticles(articles.map { it.id }, strategyKey)
            .filter { it.score >= threshold }
            .groupBySimilarityCount()

        return articles.map { article ->
            FeedSummaryArticle(
                article = article,
                similarArticlesCount = similarityByArticleId[article.id] ?: 0,
                baseImportanceScore = article.importanceScore
            )
        }
    }

    private fun List<com.andrewwin.sumup.data.local.entities.ArticleSimilarity>.groupBySimilarityCount(): Map<Long, Int> {
        val relatedArticleIds = mutableMapOf<Long, MutableSet<Long>>()
        forEach { similarity ->
            relatedArticleIds.getOrPut(similarity.leftArticleId) { mutableSetOf() }.add(similarity.rightArticleId)
            relatedArticleIds.getOrPut(similarity.rightArticleId) { mutableSetOf() }.add(similarity.leftArticleId)
        }
        return relatedArticleIds.mapValues { it.value.size }
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
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
