package com.andrewwin.sumup.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.dao.AiModelDao
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.domain.ArticleCluster
import com.andrewwin.sumup.domain.ArticleImportanceScorer
import com.andrewwin.sumup.domain.DeduplicationService
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.SummaryRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.summary.SummarySourceMeta
import com.andrewwin.sumup.domain.usecase.BuildExtractiveSummaryUseCase
import com.andrewwin.sumup.domain.usecase.FormatArticleHeadlineUseCase
import com.andrewwin.sumup.domain.usecase.NoArticlesException
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCase
import com.andrewwin.sumup.ui.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last

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
    private val buildExtractiveSummaryUseCase: BuildExtractiveSummaryUseCase,
    private val manageModelUseCase: ManageModelUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = userPreferencesRepository.preferences.first()

        userPreferencesRepository.updatePreferences(
            prefs.copy(lastWorkRunTimestamp = System.currentTimeMillis())
        )

        return try {
            articleRepository.refreshArticles()
            
            val dayTimestamp = System.currentTimeMillis() - 24 * 60 * 60 * 1000
            val recentArticles = articleRepository.getEnabledArticlesSince(dayTimestamp)

            val hasCloudEmbedding = aiRepository.hasEnabledEmbeddingConfig()
            val resolvedModelPath = resolveModelPath(prefs)
            val hasLocalEmbedding = resolvedModelPath?.let { deduplicationService.initialize(it) } ?: false
            val canDeduplicate = when (prefs.aiStrategy) {
                AiStrategy.LOCAL -> hasLocalEmbedding
                AiStrategy.CLOUD, AiStrategy.ADAPTIVE -> hasCloudEmbedding || hasLocalEmbedding
            }
            Log.d(
                TAG,
                "dedup_state(worker): strategy=${prefs.aiStrategy}, enabled=${prefs.isDeduplicationEnabled}, modelPathSet=${!prefs.modelPath.isNullOrBlank()}, resolvedModelPathSet=${!resolvedModelPath.isNullOrBlank()}, hasCloudEmbedding=$hasCloudEmbedding, hasLocalEmbedding=$hasLocalEmbedding, canDeduplicate=$canDeduplicate, thresholdLocal=${prefs.localDeduplicationThreshold}, thresholdCloud=${prefs.cloudDeduplicationThreshold}, minMentions=${prefs.minMentions}, recentArticles=${recentArticles.size}"
            )

            var filteredArticles = recentArticles
            if (prefs.isImportanceFilterEnabled) {
                filteredArticles = filteredArticles.filter { article ->
                    val source = articleRepository.getSourceById(article.sourceId)
                    val sourceType = source?.type ?: com.andrewwin.sumup.data.local.entities.SourceType.RSS
                    articleImportanceScorer.score(article, sourceType) >= ArticleImportanceScorer.IMPORTANCE_THRESHOLD
                }
            }

            val baseClusters: List<ArticleCluster> =
                if (!prefs.isDeduplicationEnabled || filteredArticles.size < 2 || !canDeduplicate) {
                    filteredArticles.map { ArticleCluster(it, emptyList()) }
                } else {
                    val dedupThreshold = when (prefs.aiStrategy) {
                        AiStrategy.LOCAL -> prefs.localDeduplicationThreshold
                        AiStrategy.CLOUD, AiStrategy.ADAPTIVE -> if (hasCloudEmbedding) {
                            prefs.cloudDeduplicationThreshold
                        } else {
                            prefs.localDeduplicationThreshold
                        }
                    }
                    deduplicationService.clusterArticlesIncremental(
                        filteredArticles,
                        dedupThreshold
                    ).last()
                }

            var clusters = baseClusters
            if (prefs.isDeduplicationEnabled) {
                clusters = clusters.filter { cluster ->
                    val mentions = cluster.duplicates.size + 1
                    val isSingle = cluster.duplicates.isEmpty()

                    if (isSingle) {
                        !prefs.isHideSingleNewsEnabled
                    } else {
                        mentions >= prefs.minMentions
                    }
                }
            }

            val articles = clusters.map { it.representative }.sortedByDescending { it.publishedAt }

            if (articles.isEmpty()) {
                val message = applicationContext.getString(R.string.summary_worker_no_articles_today)
                summaryRepository.insertSummary(Summary(content = message, strategy = prefs.aiStrategy))
                maybeShowScheduledSummaryNotification(prefs)
                return Result.success()
            }

            val summaryTextRaw = when (prefs.aiStrategy) {
                AiStrategy.LOCAL -> {
                    val extractiveTopCount = prefs.summaryNewsInScheduledExtractive.coerceAtLeast(1)
                    val articlesForExtractive = articles.take(extractiveTopCount)
                    val fullContentMap = mutableMapOf<String, String>()
                    for (article in articlesForExtractive) {
                        val source = articleRepository.getSourceById(article.sourceId)
                        val formatted = formatArticleHeadlineUseCase(article, source?.type ?: com.andrewwin.sumup.data.local.entities.SourceType.RSS)
                        fullContentMap[formatted.displayTitle] = articleRepository.fetchFullContent(article)
                    }
                    buildExtractiveSummary(
                        headlines = fullContentMap.keys.toList(),
                        contentMap = fullContentMap,
                        topCount = extractiveTopCount,
                        sentencesPerArticle = prefs.summaryItemsPerNewsInScheduled
                    )
                }
                AiStrategy.CLOUD, AiStrategy.ADAPTIVE -> {
                    val perArticleLimit = prefs.aiMaxCharsPerArticle.coerceAtLeast(200)
                    val cloudTopCount = prefs.summaryNewsInScheduledCloud.coerceAtLeast(1)
                    val articlesToSummarize = articles.take(cloudTopCount).take(MAX_ARTICLES_FOR_SUMMARIZATION)
                    val processedArticles = mutableListOf<com.andrewwin.sumup.data.local.entities.Article>()
                    
                    for (article in articlesToSummarize) {
                        val fullContent = articleRepository.fetchFullContent(article)
                        val contentToSummarize = fullContent.take(perArticleLimit)
                        processedArticles.add(article.copy(content = contentToSummarize))
                    }
                    
                    try {
                        buildCloudSummary(processedArticles, aiRepository, perArticleLimit, prefs.summaryItemsPerNewsInScheduled)
                    } catch (e: Exception) {
                        val extractiveTopCount = prefs.summaryNewsInScheduledExtractive.coerceAtLeast(1)
                        val articlesForExtractive = articles.take(extractiveTopCount)
                        val fullContentMap = mutableMapOf<String, String>()
                        for (article in articlesForExtractive) {
                            val source = articleRepository.getSourceById(article.sourceId)
                            val formatted = formatArticleHeadlineUseCase(article, source?.type ?: com.andrewwin.sumup.data.local.entities.SourceType.RSS)
                            fullContentMap[formatted.displayTitle] = articleRepository.fetchFullContent(article)
                        }
                        buildExtractiveSummary(
                            headlines = fullContentMap.keys.toList(),
                            contentMap = fullContentMap,
                            topCount = extractiveTopCount,
                            sentencesPerArticle = prefs.summaryItemsPerNewsInScheduled
                        )
                    }
                }
            }
            val summaryText = appendSourceMetadata(summaryTextRaw, articles)

            if (summaryText.isBlank()) {
                val message = applicationContext.getString(R.string.summary_worker_empty_response)
                throw IllegalStateException(message)
            }

            summaryRepository.insertSummary(Summary(content = summaryText, strategy = prefs.aiStrategy))
            maybeShowScheduledSummaryNotification(prefs)
            Result.success()
        } catch (e: NoArticlesException) {
            val message = applicationContext.getString(R.string.summary_worker_no_articles_today)
            summaryRepository.insertSummary(Summary(content = message, strategy = prefs.aiStrategy))
            maybeShowScheduledSummaryNotification(prefs)
            Result.success()
        } catch (e: Exception) {
            val prefix = applicationContext.getString(R.string.summary_worker_error_prefix)
            summaryRepository.insertSummary(Summary(content = "$prefix: ${e.localizedMessage.orEmpty()}", strategy = prefs.aiStrategy))
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private suspend fun buildExtractiveSummary(
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
        perArticleLimit: Int,
        pointsPerNews: Int
    ): String {
        val contentBuilder = StringBuilder()
        for (article in articles) {
            val source = articleRepository.getSourceById(article.sourceId)
            val formatted = formatArticleHeadlineUseCase(
                article,
                source?.type ?: com.andrewwin.sumup.data.local.entities.SourceType.RSS
            )
            val truncated = article.content.take(perArticleLimit)
            if (contentBuilder.isNotEmpty()) contentBuilder.append("\n\n")
            contentBuilder.append(
                applicationContext.getString(R.string.summary_article_format, formatted.displayTitle, truncated)
            )
        }
        return aiRepo.summarize(contentBuilder.toString(), pointsPerNews)
    }

    companion object {
        private const val TAG = "SummaryWorker"
        private const val MAX_ARTICLES_FOR_SUMMARIZATION = 15
        private const val MAX_RETRY_ATTEMPTS = 2
        private const val SUMMARY_CHANNEL_ID = "scheduled_summary_channel"
        private const val SUMMARY_NOTIFICATION_ID = 1001
    }

    private suspend fun appendSourceMetadata(
        summaryText: String,
        orderedArticles: List<com.andrewwin.sumup.data.local.entities.Article>
    ): String {
        if (summaryText.isBlank()) return summaryText

        data class SourceMeta(val titleKey: String, val sourceName: String, val sourceUrl: String)
        val metas = mutableListOf<SourceMeta>()
        for (article in orderedArticles) {
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceName = source?.name?.trim().orEmpty()
            val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
            if (sourceName.isBlank() || sourceUrl.isBlank()) continue
            val formatted = formatArticleHeadlineUseCase(
                article,
                source?.type ?: com.andrewwin.sumup.data.local.entities.SourceType.RSS
            )
            metas.add(SourceMeta(normalizeKey(formatted.displayTitle), sourceName, sourceUrl))
        }
        if (metas.isEmpty()) return summaryText

        val sections = summaryText.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
        if (sections.isEmpty()) return summaryText

        val used = BooleanArray(metas.size)
        var fallbackIndex = 0
        val enrichedSections = sections.map { section ->
            val lines = section.lines().map { it.trimEnd() }
            if (lines.any { it.startsWith(SummarySourceMeta.PREFIX) }) return@map section
            val titleLine = lines.firstOrNull()?.trim().orEmpty()
            val titleKey = normalizeKey(titleLine.removeSuffix(":"))
            var exactIndex = -1
            for (i in metas.indices) {
                if (used[i]) continue
                if (metas[i].titleKey == titleKey) {
                    exactIndex = i
                    break
                }
            }
            val pickedIndex = if (exactIndex >= 0) {
                exactIndex
            } else {
                while (fallbackIndex < metas.size && used[fallbackIndex]) fallbackIndex++
                if (fallbackIndex < metas.size) fallbackIndex else -1
            }
            if (pickedIndex == -1) return@map section
            used[pickedIndex] = true
            val meta = metas[pickedIndex]
            "$section\n${SummarySourceMeta.PREFIX}${meta.sourceName}|${meta.sourceUrl}"
        }
        return enrichedSections.joinToString("\n\n")
    }

    private fun normalizeKey(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun resolveModelPath(prefs: com.andrewwin.sumup.data.local.entities.UserPreferences): String? {
        if (!prefs.modelPath.isNullOrBlank()) return prefs.modelPath
        return if (manageModelUseCase.isModelExists()) {
            manageModelUseCase.getModelPath()
        } else {
            null
        }
    }

    private fun maybeShowScheduledSummaryNotification(prefs: com.andrewwin.sumup.data.local.entities.UserPreferences) {
        if (!prefs.isScheduledSummaryPushEnabled) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        createNotificationChannelIfNeeded()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, SUMMARY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_summary_page)
            .setContentTitle(applicationContext.getString(R.string.summary_notification_title))
            .setContentText(applicationContext.getString(R.string.summary_notification_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(SUMMARY_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            SUMMARY_CHANNEL_ID,
            applicationContext.getString(R.string.summary_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = applicationContext.getString(R.string.summary_notification_channel_description)
        }
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
