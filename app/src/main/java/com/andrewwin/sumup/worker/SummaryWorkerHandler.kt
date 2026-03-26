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
import androidx.work.ListenableWorker
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.domain.service.ArticleImportanceScorer
import com.andrewwin.sumup.domain.service.ArticleCluster
import com.andrewwin.sumup.domain.service.DeduplicationService
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.SummaryRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.common.NoArticlesException
import com.andrewwin.sumup.domain.usecase.ai.SummaryContext
import com.andrewwin.sumup.domain.usecase.ai.SummarizationEngineUseCase
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCase
import com.andrewwin.sumup.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import javax.inject.Inject

class SummaryWorkerHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val articleRepository: ArticleRepository,
    private val aiRepository: AiRepository,
    private val summaryRepository: SummaryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val articleImportanceScorer: ArticleImportanceScorer,
    private val deduplicationService: DeduplicationService,
    private val summarizationEngineUseCase: SummarizationEngineUseCase,
    private val manageModelUseCase: ManageModelUseCase
) {
    suspend fun execute(runAttemptCount: Int): ListenableWorker.Result {
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
                    val sourceType = source?.type ?: SourceType.RSS
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

                    if (isSingle) !prefs.isHideSingleNewsEnabled else mentions >= prefs.minMentions
                }
            }

            val articles = clusters.map { it.representative }.sortedByDescending { it.publishedAt }

            if (articles.isEmpty()) {
                val message = context.getString(R.string.summary_worker_no_articles_today)
                summaryRepository.insertSummary(Summary(content = message, strategy = prefs.aiStrategy))
                maybeShowScheduledSummaryNotification(prefs)
                return ListenableWorker.Result.success()
            }

            val limit = when (prefs.aiStrategy) {
                AiStrategy.LOCAL -> prefs.summaryNewsInScheduledExtractive.coerceAtLeast(1)
                AiStrategy.CLOUD, AiStrategy.ADAPTIVE -> prefs.summaryNewsInScheduledCloud.coerceAtLeast(1)
            }
            val articlesToSummarize = articles.take(limit).take(WorkerContracts.MAX_ARTICLES_FOR_SUMMARIZATION)
            val summaryText = summarizationEngineUseCase
                .summarizeArticles(
                    articles = articlesToSummarize,
                    context = SummaryContext.ScheduledSummary(articleCount = articlesToSummarize.size)
                )
                .getOrThrow()

            if (summaryText.isBlank()) {
                val message = context.getString(R.string.summary_worker_empty_response)
                throw IllegalStateException(message)
            }

            summaryRepository.insertSummary(Summary(content = summaryText, strategy = prefs.aiStrategy))
            maybeShowScheduledSummaryNotification(prefs)
            ListenableWorker.Result.success()
        } catch (e: NoArticlesException) {
            val message = context.getString(R.string.summary_worker_no_articles_today)
            summaryRepository.insertSummary(Summary(content = message, strategy = prefs.aiStrategy))
            maybeShowScheduledSummaryNotification(prefs)
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            val prefix = context.getString(R.string.summary_worker_error_prefix)
            summaryRepository.insertSummary(Summary(content = "$prefix: ${e.localizedMessage.orEmpty()}", strategy = prefs.aiStrategy))
            if (runAttemptCount < WorkerContracts.MAX_RETRY_ATTEMPTS) {
                ListenableWorker.Result.retry()
            } else {
                ListenableWorker.Result.failure()
            }
        }
    }

    private fun resolveModelPath(prefs: com.andrewwin.sumup.data.local.entities.UserPreferences): String? {
        if (!prefs.modelPath.isNullOrBlank()) return prefs.modelPath
        return if (manageModelUseCase.isModelExists()) manageModelUseCase.getModelPath() else null
    }

    private fun maybeShowScheduledSummaryNotification(prefs: com.andrewwin.sumup.data.local.entities.UserPreferences) {
        if (!prefs.isScheduledSummaryPushEnabled) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        createNotificationChannelIfNeeded()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, WorkerContracts.SUMMARY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_summary_page)
            .setContentTitle(context.getString(R.string.summary_notification_title))
            .setContentText(context.getString(R.string.summary_notification_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context)
            .notify(WorkerContracts.SUMMARY_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            WorkerContracts.SUMMARY_CHANNEL_ID,
            context.getString(R.string.summary_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.summary_notification_channel_description)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}






