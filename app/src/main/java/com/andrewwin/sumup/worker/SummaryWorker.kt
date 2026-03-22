package com.andrewwin.sumup.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.domain.ExtractiveSummarizer
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.SummaryRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.ArticleImportanceScorer
import com.andrewwin.sumup.data.local.dao.AiModelDao
import com.andrewwin.sumup.domain.DeduplicationService
import com.andrewwin.sumup.domain.usecase.NoArticlesException
import com.andrewwin.sumup.domain.usecase.FormatArticleHeadlineUseCase
import com.andrewwin.sumup.domain.usecase.BuildExtractiveSummaryUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class SummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val articleRepository: ArticleRepository,
    private val aiRepository: AiRepository,
    private val summaryRepository: SummaryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val articleImportanceScorer: ArticleImportanceScorer,
    private val aiModelDao: AiModelDao,
    private val deduplicationService: DeduplicationService,
    private val formatArticleHeadlineUseCase: FormatArticleHeadlineUseCase,
    private val buildExtractiveSummaryUseCase: BuildExtractiveSummaryUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting SummaryWorker...")
        val prefs = userPreferencesRepository.preferences.first()
        Log.d(TAG, "Current preferences: strategy=${prefs.aiStrategy}, scheduled=${prefs.isScheduledSummaryEnabled}")

        userPreferencesRepository.updatePreferences(
            prefs.copy(lastWorkRunTimestamp = System.currentTimeMillis())
        )

        return try {
            Log.d(TAG, "Refreshing articles...")
            articleRepository.refreshArticles()
            
            // Фільтрація за останні 24 години ("визначення дня")
            val dayTimestamp = System.currentTimeMillis() - 24 * 60 * 60 * 1000
            val recentArticles = articleRepository.getEnabledArticlesSince(dayTimestamp)
            Log.d(TAG, "Fetched ${recentArticles.size} recent articles (last 24h)")

            val hasCloudEmbedding = aiRepository.hasEnabledEmbeddingConfig()
            val dedupThreshold = if (hasCloudEmbedding) {
                prefs.cloudDeduplicationThreshold
            } else {
                prefs.localDeduplicationThreshold
            }
            if (!hasCloudEmbedding) {
                prefs.modelPath?.let { deduplicationService.initialize(it) }
            }
            
            val similarityCounts = if (recentArticles.size >= 2) {
                val clusters = deduplicationService.clusterArticlesIncremental(
                    recentArticles,
                    dedupThreshold
                ).first()
                
                val map = mutableMapOf<Long, Int>()
                for (cluster in clusters) {
                    val count = cluster.duplicates.size
                    map[cluster.representative.id] = count
                    for (duplicate in cluster.duplicates) {
                        map[duplicate.first.id] = count
                    }
                }
                map
            } else {
                emptyMap()
            }

            // Фільтрація за важливістю
            val articles = recentArticles.filter { article ->
                val source = articleRepository.getSourceById(article.sourceId)
                val baseScore = articleImportanceScorer.score(
                    article,
                    source?.type ?: com.andrewwin.sumup.data.local.entities.SourceType.RSS
                )
                val boostedScore = baseScore + (similarityCounts[article.id] ?: 0) * SIMILARITY_BOOST_PER_ARTICLE
                boostedScore >= 0.5f // Поріг для зведення
            }
            Log.d(TAG, "Articles after importance screening: ${articles.size}")

            if (articles.isEmpty()) {
                Log.d(TAG, "No articles found, inserting 'no articles' message")
                val message = applicationContext.getString(R.string.summary_worker_no_articles_today)
                summaryRepository.insertSummary(Summary(content = message, strategy = prefs.aiStrategy))
                return Result.success()
            }

            Log.d(TAG, "Strategy: ${prefs.aiStrategy}. Articles for summary: ${articles.size}")
            
            val summaryText = when (prefs.aiStrategy) {
                AiStrategy.LOCAL -> {
                    Log.d(TAG, "Building Local summary")
                    val fullContentMap = articles.map { article ->
                        val source = articleRepository.getSourceById(article.sourceId)
                        val formatted = formatArticleHeadlineUseCase(article, source?.type ?: com.andrewwin.sumup.data.local.entities.SourceType.RSS)
                        formatted.displayTitle to articleRepository.fetchFullContent(article)
                    }.toMap()
                    buildExtractiveSummary(
                        headlines = fullContentMap.keys.toList(),
                        contentMap = fullContentMap,
                        topCount = prefs.extractiveNewsInScheduled,
                        sentencesPerArticle = prefs.extractiveSentencesInScheduled
                    )
                }
                AiStrategy.CLOUD, AiStrategy.ADAPTIVE -> {
                    Log.d(TAG, "Building Cloud/Adaptive summary")
                    val perArticleLimit = prefs.aiMaxCharsPerArticle.coerceAtLeast(200)
                    val processedArticles = articles.take(MAX_ARTICLES_FOR_SUMMARIZATION).map { article -> 
                        val fullContent = articleRepository.fetchFullContent(article)
                        val contentToSummarize = if (prefs.aiStrategy == AiStrategy.ADAPTIVE && prefs.isAdaptiveExtractivePreprocessingEnabled) {
                             ExtractiveSummarizer
                                .summarize(fullContent, prefs.extractiveSentencesInScheduled)
                                .joinToString(" ")
                        } else {
                            fullContent
                        }
                        article.copy(content = contentToSummarize)
                    }
                    
                    try {
                        buildCloudSummary(processedArticles, aiRepository, perArticleLimit)
                    } catch (e: Exception) {
                        Log.e(TAG, "Cloud summary failed, falling back to local", e)
                        val fullContentMap = articles.map { article ->
                            val source = articleRepository.getSourceById(article.sourceId)
                            val formatted = formatArticleHeadlineUseCase(article, source?.type ?: com.andrewwin.sumup.data.local.entities.SourceType.RSS)
                            formatted.displayTitle to articleRepository.fetchFullContent(article)
                        }.toMap()
                        buildExtractiveSummary(
                            headlines = fullContentMap.keys.toList(),
                            contentMap = fullContentMap,
                            topCount = prefs.extractiveNewsInScheduled,
                            sentencesPerArticle = prefs.extractiveSentencesInScheduled
                        )
                    }
                }
            }

            if (summaryText.isBlank()) {
                Log.e(TAG, "Summary text is blank!")
                val message = applicationContext.getString(R.string.summary_worker_empty_response)
                throw IllegalStateException(message)
            }

            Log.d(TAG, "Summary generated successfully. Length: ${summaryText.length}")
            summaryRepository.insertSummary(Summary(content = summaryText, strategy = prefs.aiStrategy))
            Result.success()
        } catch (e: NoArticlesException) {
            Log.w(TAG, "NoArticlesException caught: ${e.message}")
            val message = applicationContext.getString(R.string.summary_worker_no_articles_today)
            summaryRepository.insertSummary(Summary(content = message, strategy = prefs.aiStrategy))
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in SummaryWorker", e)
            val prefix = applicationContext.getString(R.string.summary_worker_error_prefix)
            summaryRepository.insertSummary(Summary(content = "$prefix: ${e.localizedMessage.orEmpty()}", strategy = prefs.aiStrategy))
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private fun buildExtractiveSummary(
        headlines: List<String>,
        contentMap: Map<String, String>,
        topCount: Int,
        sentencesPerArticle: Int
    ): String = buildExtractiveSummaryUseCase(
        headlines = headlines,
        contentMap = contentMap,
        topCount = topCount,
        sentencesPerArticle = sentencesPerArticle
    )

    private suspend fun buildCloudSummary(
        articles: List<com.andrewwin.sumup.data.local.entities.Article>,
        aiRepo: AiRepository,
        perArticleLimit: Int
    ): String {
        val content = articles.joinToString("\n\n") { article ->
            val truncated = article.content.take(perArticleLimit)
            applicationContext.getString(R.string.summary_article_format, article.title, truncated)
        }
        return aiRepo.summarize(content)
    }

    companion object {
        private const val TAG = "SummaryWorker"
        private const val MAX_ARTICLES_FOR_SUMMARIZATION = 15
        private const val MAX_RETRY_ATTEMPTS = 2
        private const val SIMILARITY_BOOST_PER_ARTICLE = 0.15f
    }
}
