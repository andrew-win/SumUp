package com.andrewwin.sumup.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import com.andrewwin.sumup.domain.usecase.NoArticlesException
import com.andrewwin.sumup.domain.usecase.ai.SummaryContext
import com.andrewwin.sumup.domain.usecase.ai.SummarizationEngineUseCase
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
    private val summarizationEngineUseCase: SummarizationEngineUseCase,
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

            val limit = when (prefs.aiStrategy) {
                AiStrategy.LOCAL -> prefs.summaryNewsInScheduledExtractive.coerceAtLeast(1)
                AiStrategy.CLOUD, AiStrategy.ADAPTIVE -> prefs.summaryNewsInScheduledCloud.coerceAtLeast(1)
            }
            val articlesToSummarize = articles.take(limit).take(MAX_ARTICLES_FOR_SUMMARIZATION)
            val summaryTextRaw = summarizationEngineUseCase
                .summarizeArticles(
                    articles = articlesToSummarize,
                    context = SummaryContext.ScheduledSummary(articleCount = articlesToSummarize.size)
                )
                .getOrThrow()
            val summaryText = summaryTextRaw

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

    companion object {
        private const val TAG = "SummaryWorker"
        private const val MAX_ARTICLES_FOR_SUMMARIZATION = 15
        private const val MAX_RETRY_ATTEMPTS = 2
        private const val SUMMARY_CHANNEL_ID = "scheduled_summary_channel"
        private const val SUMMARY_NOTIFICATION_ID = 1001
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
