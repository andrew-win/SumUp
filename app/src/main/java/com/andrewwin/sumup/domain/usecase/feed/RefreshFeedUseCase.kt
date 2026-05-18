package com.andrewwin.sumup.domain.usecase.feed

import android.util.Log
import com.andrewwin.sumup.domain.support.DispatcherProvider
import com.andrewwin.sumup.domain.repository.SuggestedThemesStateRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.common.RefreshArticlesUseCase
import com.andrewwin.sumup.domain.usecase.sources.GetSuggestedThemesUseCase
import com.andrewwin.sumup.domain.source.SuggestedThemesRefreshPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface RefreshFeedUseCase {
    suspend operator fun invoke(onStageChange: suspend (FeedRefreshStage) -> Unit = {}): Result<Unit>
}

enum class FeedRefreshStage {
    PARSING_NEWS,
    DEDUPLICATING_NEWS
}

class RefreshFeedUseCaseImpl @Inject constructor(
    private val refreshArticlesUseCase: RefreshArticlesUseCase,
    private val feedDeduplicationProcessor: FeedDeduplicationProcessor,
    private val getSuggestedThemesUseCase: GetSuggestedThemesUseCase,
    private val suggestedThemesStateRepository: SuggestedThemesStateRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dispatcherProvider: DispatcherProvider
) : RefreshFeedUseCase {
    private val mutex = Mutex()
    private var lastRefreshAt: Long = 0
    private var nextRunId: Long = 1
    private val backgroundScope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)

    override suspend fun invoke(onStageChange: suspend (FeedRefreshStage) -> Unit): Result<Unit> = withContext(dispatcherProvider.io) {
        var shouldRefreshSuggestedThemes = false
        val requestAt = System.currentTimeMillis()
        logRefreshDebug(
            "refresh_use_case_requested ageMs=${refreshAgeMs(requestAt)} " +
                "isLocked=${mutex.isLocked} caller=${callerStack()}"
        )
        val result = mutex.withLock {
            val now = System.currentTimeMillis()
            val ageMs = refreshAgeMs(now)
            if (ageMs in 0 until MIN_REFRESH_INTERVAL_MS) {
                logRefreshDebug("refresh_use_case_skipped_min_interval ageMs=$ageMs")
                return@withLock Result.success(Unit)
            }
            val runId = nextRunId++
            val runStartedAt = System.currentTimeMillis()
            logRefreshDebug("refresh_use_case_started runId=$runId ageMs=$ageMs")
            val refreshResult = runCatching {
                onStageChange(FeedRefreshStage.PARSING_NEWS)
                refreshArticlesUseCase()
                val prefs = userPreferencesRepository.preferences.first()
                onStageChange(FeedRefreshStage.DEDUPLICATING_NEWS)
                feedDeduplicationProcessor.rebuildSimilarities(prefs).getOrThrow()
            }
            
            if (refreshResult.isSuccess) {
                lastRefreshAt = now
                suggestedThemesStateRepository.setLastFeedRefreshAt(now)
                val lastRecommendationAt = suggestedThemesStateRepository.getLastRecommendationAt()
                shouldRefreshSuggestedThemes =
                    (now - lastRecommendationAt) >= SuggestedThemesRefreshPolicy.REFRESH_INTERVAL_MS
            }

            logRefreshDebug(
                "refresh_use_case_finished runId=$runId success=${refreshResult.isSuccess} " +
                    "durationMs=${System.currentTimeMillis() - runStartedAt}"
            )
            refreshResult
        }

        val shouldRecalculateRecommendations = userPreferencesRepository.preferences.first().isRecommendationsEnabled
        if (result.isSuccess && shouldRefreshSuggestedThemes && shouldRecalculateRecommendations) {
            backgroundScope.launch {
                runCatching { getSuggestedThemesUseCase(forceRefresh = false).collect() }
            }
        }

        result
    }

    private fun refreshAgeMs(now: Long): Long =
        if (lastRefreshAt == 0L) -1L else now - lastRefreshAt

    private fun callerStack(): String =
        Throwable().stackTrace
            .asSequence()
            .filter { frame ->
                frame.className.startsWith(APP_PACKAGE_PREFIX) &&
                    !frame.className.contains("RefreshFeedUseCaseImpl")
            }
            .take(CALLER_STACK_FRAME_LIMIT)
            .joinToString(" <- ") { frame ->
                "${frame.className.substringAfterLast('.')}.${frame.methodName}:${frame.lineNumber}"
            }

    private fun logRefreshDebug(message: String) {
        if (REFRESH_TRIGGER_LOGS_ENABLED) {
            Log.d(REFRESH_TRIGGER_LOG_TAG, message)
        }
    }

    companion object {
        private const val MIN_REFRESH_INTERVAL_MS = 5_000L
        private const val REFRESH_TRIGGER_LOGS_ENABLED = true
        private const val REFRESH_TRIGGER_LOG_TAG = "RefreshTriggerDebug"
        private const val APP_PACKAGE_PREFIX = "com.andrewwin.sumup"
        private const val CALLER_STACK_FRAME_LIMIT = 8
    }
}









