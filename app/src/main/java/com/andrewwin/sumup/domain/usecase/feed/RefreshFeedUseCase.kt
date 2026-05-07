package com.andrewwin.sumup.domain.usecase.feed

import com.andrewwin.sumup.domain.support.DispatcherProvider
import com.andrewwin.sumup.domain.repository.SuggestedThemesStateRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.common.RefreshArticlesUseCase
import com.andrewwin.sumup.domain.usecase.sources.GetSuggestedThemesUseCase
import com.andrewwin.sumup.domain.usecase.sources.SuggestedThemesRefreshConstants
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
    suspend operator fun invoke(): Result<Unit>
}

class RefreshFeedUseCaseImpl @Inject constructor(
    private val refreshArticlesUseCase: RefreshArticlesUseCase,
    private val getSuggestedThemesUseCase: GetSuggestedThemesUseCase,
    private val suggestedThemesStateRepository: SuggestedThemesStateRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dispatcherProvider: DispatcherProvider
) : RefreshFeedUseCase {
    private val mutex = Mutex()
    private var lastRefreshAt: Long = 0
    private val backgroundScope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)

    override suspend fun invoke(): Result<Unit> = withContext(dispatcherProvider.io) {
        var shouldRefreshSuggestedThemes = false
        val result = mutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastRefreshAt < MIN_REFRESH_INTERVAL_MS) return@withLock Result.success(Unit)
            val refreshResult = runCatching { refreshArticlesUseCase() }
            
            if (refreshResult.isSuccess) {
                lastRefreshAt = now
                suggestedThemesStateRepository.setLastFeedRefreshAt(now)
                val lastRecommendationAt = suggestedThemesStateRepository.getLastRecommendationAt()
                shouldRefreshSuggestedThemes =
                    (now - lastRecommendationAt) >= SuggestedThemesRefreshConstants.REFRESH_INTERVAL_MS
            }

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

    companion object {
        private const val MIN_REFRESH_INTERVAL_MS = 5_000L
    }
}









