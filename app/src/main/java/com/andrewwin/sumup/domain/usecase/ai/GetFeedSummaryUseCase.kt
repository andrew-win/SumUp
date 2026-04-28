package com.andrewwin.sumup.domain.usecase.ai

import android.content.Context
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.feed.FeedSummaryArticle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetFeedSummaryUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val articleRepository: ArticleRepository,
    private val shrinkTextForAdaptiveStrategyUseCase: ShrinkTextForAdaptiveStrategyUseCase,
    private val sendCloudAiRequestUseCase: SendCloudAiRequestUseCase,
    private val parseAiJsonResponseUseCase: ParseAiJsonResponseUseCase
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
            articles = feedSummaryArticles.map { it.article },
            strategy = strategy
        )
    }

    private suspend fun buildLocalSummary(feedSummaryArticles: List<FeedSummaryArticle>): SummaryResult.Digest {
        val topArticles = feedSummaryArticles
            .sortedByDescending { article ->
                article.baseImportanceScore + article.similarArticlesCount * LOCAL_SIMILAR_NEWS_BONUS_PER_MATCH
            }
            .take(LOCAL_FEED_SUMMARY_TOP_ARTICLES_COUNT)

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
        articles: List<Article>,
        strategy: AiStrategy
    ): SummaryResult {
        val prefs = userPreferencesRepository.preferences.first()
        val maxTotalChars = prefs.aiMaxCharsTotal.coerceAtLeast(MIN_TOTAL_CHARS)
        var remainingTotal = maxTotalChars
        val cloudInput = buildString {
            for (article in articles) {
                if (remainingTotal <= 0) break
                val source = articleRepository.getSourceById(article.sourceId)
                val sourceName = source?.name?.trim()?.ifBlank { sourceFallbackName } ?: sourceFallbackName
                val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()

                val fullContent = articleRepository.fetchFullContent(article)
                val contentToProcess = fullContent.ifBlank { article.content }

                val textForCloud = if (strategy == AiStrategy.ADAPTIVE) {
                    shrinkTextForAdaptiveStrategyUseCase(contentToProcess, prefs)
                } else {
                    contentToProcess.take(prefs.aiMaxCharsPerFeedArticle.coerceAtLeast(MIN_CHARS_PER_FEED_ARTICLE))
                }

                val block = buildString {
                    append("source_id: ${article.id}\n")
                    append("source_name: $sourceName\n")
                    append("source_url: $sourceUrl\n")
                    append("title: ${article.title}\n")
                    append("content: $textForCloud\n\n")
                }

                if (block.length <= remainingTotal) {
                    append(block)
                    remainingTotal -= block.length
                } else {
                    append(block.take(remainingTotal))
                    remainingTotal = 0
                }
            }
        }

        val prompt = AiPromptBuilder.buildFeedDigestPrompt(prefs.summaryLanguage)
        val jsonResponse = sendCloudAiRequestUseCase(prompt, cloudInput)
        return parseAiJsonResponseUseCase.parseFeed(jsonResponse, cloudInput)
    }

    private suspend fun buildFeedSummaryArticles(articles: List<Article>): List<FeedSummaryArticle> {
        val similarityByArticleId = articleRepository
            .getSimilaritiesForArticles(articles.map { it.id })
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
            relatedArticleIds.getOrPut(similarity.representativeId) { mutableSetOf() }.add(similarity.articleId)
            relatedArticleIds.getOrPut(similarity.articleId) { mutableSetOf() }.add(similarity.representativeId)
        }
        return relatedArticleIds.mapValues { it.value.size }
    }

    private val sourceFallbackName: String
        get() = context.getString(R.string.summary_source_fallback)

    private val mainNewsThemeTitle: String
        get() = context.getString(R.string.summary_digest_main_news_title)

    companion object {
        private const val LOCAL_SIMILAR_NEWS_BONUS_PER_MATCH = 0.25f
        private const val LOCAL_FEED_SUMMARY_TOP_ARTICLES_COUNT = 7
        private const val MIN_TOTAL_CHARS = 1000
        private const val MIN_CHARS_PER_FEED_ARTICLE = 200
    }
}
