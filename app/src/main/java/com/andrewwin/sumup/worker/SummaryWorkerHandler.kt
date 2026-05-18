package com.andrewwin.sumup.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ListenableWorker
import androidx.work.Data
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.PreparedScheduledSummary
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.domain.repository.SummaryRepository
import com.andrewwin.sumup.domain.repository.SummaryScheduler
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.ai.GenerateSummaryUseCase
import com.andrewwin.sumup.domain.usecase.ai.NoArticlesException
import com.andrewwin.sumup.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SummaryWorkerHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val summaryRepository: SummaryRepository,
    private val summaryScheduler: SummaryScheduler,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val generateSummaryUseCase: GenerateSummaryUseCase,
) {
    suspend fun execute(inputData: Data, runAttemptCount: Int): ListenableWorker.Result {
        val kind = inputData.getString(WorkerContracts.KEY_SCHEDULED_SUMMARY_WORK_KIND)
            ?.let { runCatching { ScheduledSummaryWorkKind.valueOf(it) }.getOrNull() }
            ?: ScheduledSummaryWorkKind.PREPARE
        val scheduledAt = inputData.getLong(WorkerContracts.KEY_SCHEDULED_SUMMARY_AT, 0L)
            .takeIf { it > 0L }
            ?: System.currentTimeMillis()
        return when (kind) {
            ScheduledSummaryWorkKind.PREPARE -> prepareScheduledSummary(scheduledAt, runAttemptCount)
            ScheduledSummaryWorkKind.DELIVER -> deliverScheduledSummary(scheduledAt)
        }
    }

    private suspend fun prepareScheduledSummary(
        scheduledAt: Long,
        runAttemptCount: Int
    ): ListenableWorker.Result {
        val prefs = userPreferencesRepository.preferences.first()
        if (!prefs.isScheduledSummaryEnabled) {
            summaryScheduler.cancel()
            return ListenableWorker.Result.success()
        }

        Log.d(SCHEDULED_SUMMARY_LOG_TAG, "prepare_started scheduledAt=$scheduledAt")
        return try {
            val summaryText = generateSummaryUseCase(refresh = true)
            if (summaryText.isBlank()) {
                val message = context.getString(R.string.summary_worker_empty_response)
                throw IllegalStateException(message)
            }
            summaryRepository.upsertPreparedScheduledSummary(
                PreparedScheduledSummary(
                    scheduledAt = scheduledAt,
                    content = summaryText,
                    strategy = prefs.aiStrategy
                )
            )
            Log.d(SCHEDULED_SUMMARY_LOG_TAG, "prepare_finished scheduledAt=$scheduledAt")
            if (System.currentTimeMillis() >= scheduledAt) {
                return deliverScheduledSummary(scheduledAt)
            }
            ListenableWorker.Result.success()
        } catch (e: NoArticlesException) {
            val message = context.getString(R.string.summary_worker_no_articles_today)
            summaryRepository.upsertPreparedScheduledSummary(
                PreparedScheduledSummary(
                    scheduledAt = scheduledAt,
                    content = message,
                    strategy = prefs.aiStrategy
                )
            )
            if (System.currentTimeMillis() >= scheduledAt) {
                return deliverScheduledSummary(scheduledAt)
            }
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            val prefix = context.getString(R.string.summary_worker_error_prefix)
            if (runAttemptCount < WorkerContracts.MAX_RETRY_ATTEMPTS) {
                ListenableWorker.Result.retry()
            } else {
                summaryRepository.upsertPreparedScheduledSummary(
                    PreparedScheduledSummary(
                        scheduledAt = scheduledAt,
                        content = "$prefix: ${e.localizedMessage.orEmpty()}",
                        strategy = prefs.aiStrategy,
                        isError = true
                    )
                )
                if (System.currentTimeMillis() >= scheduledAt) {
                    return deliverScheduledSummary(scheduledAt)
                }
                ListenableWorker.Result.success()
            }
        }
    }

    private suspend fun deliverScheduledSummary(scheduledAt: Long): ListenableWorker.Result {
        val prefs = userPreferencesRepository.preferences.first()
        if (!prefs.isScheduledSummaryEnabled) {
            summaryScheduler.cancel()
            return ListenableWorker.Result.success()
        }

        Log.d(SCHEDULED_SUMMARY_LOG_TAG, "deliver_started scheduledAt=$scheduledAt")
        val preparedSummary = summaryRepository.getPreparedScheduledSummary(scheduledAt)
        if (preparedSummary == null) {
            summaryScheduler.prepareNow(scheduledAt)
            summaryScheduler.schedule(prefs.scheduledSummaryTimeList)
            return ListenableWorker.Result.success()
        }

        summaryRepository.insertSummary(
            Summary(
                content = preparedSummary.content,
                strategy = preparedSummary.strategy,
                createdAt = scheduledAt,
                isError = preparedSummary.isError
            )
        )
        summaryRepository.deletePreparedScheduledSummary(scheduledAt)
        summaryRepository.deletePreparedScheduledSummariesBefore(scheduledAt)
        userPreferencesRepository.updatePreferences(
            prefs.copy(lastWorkRunTimestamp = System.currentTimeMillis())
        )
        maybeShowScheduledSummaryNotification(prefs)
        summaryScheduler.schedule(prefs.scheduledSummaryTimeList)
        Log.d(SCHEDULED_SUMMARY_LOG_TAG, "deliver_finished scheduledAt=$scheduledAt")
        return ListenableWorker.Result.success()
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

    private companion object {
        private const val SCHEDULED_SUMMARY_LOG_TAG = "ScheduledSummary"
    }
}

