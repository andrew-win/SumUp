package com.andrewwin.sumup.worker.summary

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker
import com.andrewwin.sumup.R
import com.andrewwin.sumup.domain.summary.formatter.SummaryExecutionInfoFormatter
import com.andrewwin.sumup.domain.ai.service.SummaryExecutionInfoStore
import com.andrewwin.sumup.domain.summary.repository.SummaryRepository
import com.andrewwin.sumup.domain.summary.repository.SummaryScheduler
import com.andrewwin.sumup.domain.settings.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.settings.model.UserSettings
import com.andrewwin.sumup.domain.summary.model.ScheduledSummaryDraft
import com.andrewwin.sumup.domain.summary.model.SummaryRecord
import com.andrewwin.sumup.domain.summary.scheduled.NoArticlesException
import com.andrewwin.sumup.domain.summary.scheduled.ScheduledSummaryTextGenerator
import com.andrewwin.sumup.domain.support.AllAiModelsFailedException
import com.andrewwin.sumup.worker.summary.model.ScheduledSummaryWorkKind
import com.andrewwin.sumup.worker.summary.notification.SummaryNotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SummaryWorkerHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val summaryRepository: SummaryRepository,
    private val summaryScheduler: SummaryScheduler,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val scheduledSummaryTextGenerator: ScheduledSummaryTextGenerator,
    private val summaryExecutionInfoFormatter: SummaryExecutionInfoFormatter,
    private val summaryExecutionInfoStore: SummaryExecutionInfoStore,
    private val notificationHelper: SummaryNotificationHelper
) {
    suspend fun execute(inputData: Data, runAttemptCount: Int): ListenableWorker.Result {
        val kind = inputData.getString(KEY_SCHEDULED_SUMMARY_WORK_KIND)
            ?.let { runCatching { ScheduledSummaryWorkKind.valueOf(it) }.getOrNull() }
            ?: ScheduledSummaryWorkKind.PREPARE
        val scheduledAt = inputData.getLong(KEY_SCHEDULED_SUMMARY_AT, 0L)
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
            summaryExecutionInfoStore.clear()
            val summaryText = scheduledSummaryTextGenerator(refresh = true)
            if (summaryText.isBlank()) {
                val message = context.getString(R.string.summary_worker_empty_response)
                throw IllegalStateException(message)
            }
            val executionInfo = summaryExecutionInfoStore.current()
            summaryRepository.upsertPreparedScheduledSummary(
                ScheduledSummaryDraft(
                    scheduledAt = scheduledAt,
                    content = summaryText,
                    strategy = prefs.aiStrategy,
                    executionLabel = executionInfo.label.takeIf { it.isNotBlank() },
                    executionNote = executionInfo.note.takeIf { it.isNotBlank() }
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
                ScheduledSummaryDraft(
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
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                ListenableWorker.Result.retry()
            } else {
                val executionInfo = if (e is AllAiModelsFailedException) {
                    summaryExecutionInfoFormatter.buildCloudFailureInfo(
                        strategy = prefs.aiStrategy,
                        failures = e.failures
                    )
                } else {
                    summaryExecutionInfoStore.current()
                }
                val content = if (e is AllAiModelsFailedException) {
                    summaryExecutionInfoFormatter.buildCloudFailureText(e.failures)
                } else {
                    "${context.getString(R.string.summary_worker_error_prefix)}: ${e.localizedMessage.orEmpty()}"
                }
                summaryRepository.upsertPreparedScheduledSummary(
                    ScheduledSummaryDraft(
                        scheduledAt = scheduledAt,
                        content = content,
                        strategy = prefs.aiStrategy,
                        isError = true,
                        executionLabel = executionInfo.label.takeIf { it.isNotBlank() },
                        executionNote = executionInfo.note.takeIf { it.isNotBlank() }
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
            SummaryRecord(
                content = preparedSummary.content,
                strategy = preparedSummary.strategy,
                createdAt = scheduledAt,
                isError = preparedSummary.isError,
                executionLabel = preparedSummary.executionLabel,
                executionNote = preparedSummary.executionNote
            )
        )
        summaryRepository.deletePreparedScheduledSummary(scheduledAt)
        summaryRepository.deletePreparedScheduledSummariesBefore(scheduledAt)
        userPreferencesRepository.updatePreferences(
            prefs.copy(lastWorkRunTimestamp = System.currentTimeMillis())
        )
        notificationHelper.maybeShowScheduledSummaryNotification(prefs.isScheduledSummaryPushEnabled)
        summaryScheduler.schedule(prefs.scheduledSummaryTimeList)
        Log.d(SCHEDULED_SUMMARY_LOG_TAG, "deliver_finished scheduledAt=$scheduledAt")
        return ListenableWorker.Result.success()
    }

    companion object {
        private const val SCHEDULED_SUMMARY_LOG_TAG = "ScheduledSummary"
        const val KEY_SCHEDULED_SUMMARY_WORK_KIND = "scheduled_summary_work_kind"
        const val KEY_SCHEDULED_SUMMARY_AT = "scheduled_summary_at"
        const val MAX_RETRY_ATTEMPTS = 2
    }
}
