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
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.SummaryRepository
import com.andrewwin.sumup.domain.repository.SummaryScheduler
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.common.GenerateSummaryUseCase
import com.andrewwin.sumup.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SummaryWorkerHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val articleRepository: ArticleRepository,
    private val summaryRepository: SummaryRepository,
    private val summaryScheduler: SummaryScheduler,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val generateSummaryUseCase: GenerateSummaryUseCase,
) {
    suspend fun execute(runAttemptCount: Int): ListenableWorker.Result {
        val prefs = userPreferencesRepository.preferences.first()

        userPreferencesRepository.updatePreferences(
            prefs.copy(lastWorkRunTimestamp = System.currentTimeMillis())
        )

        val result = try {
            val summaryText = generateSummaryUseCase(refresh = true)

                if (summaryText.isBlank()) {
                    val message = context.getString(R.string.summary_worker_empty_response)
                    throw IllegalStateException(message)
                }

                summaryRepository.insertSummary(Summary(content = summaryText, strategy = prefs.aiStrategy))
                maybeShowScheduledSummaryNotification(prefs)
            ListenableWorker.Result.success()
        } catch (e: com.andrewwin.sumup.domain.usecase.common.NoArticlesException) {
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
        if (result !is ListenableWorker.Result.Retry) {
            val latestPrefs = userPreferencesRepository.preferences.first()
            if (latestPrefs.isScheduledSummaryEnabled) {
                summaryScheduler.schedule(latestPrefs.scheduledHour, latestPrefs.scheduledMinute)
            } else {
                summaryScheduler.cancel()
            }
        }
        return result
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





