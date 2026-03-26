package com.andrewwin.sumup.domain.usecase.feed

import com.andrewwin.sumup.domain.support.DispatcherProvider
import com.andrewwin.sumup.domain.repository.SuggestedThemesStateRepository
import com.andrewwin.sumup.domain.usecase.common.RefreshArticlesUseCase
import com.andrewwin.sumup.domain.usecase.sources.GetSuggestedThemesUseCase
import kotlinx.coroutines.flow.collect
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
    private val dispatcherProvider: DispatcherProvider
) : RefreshFeedUseCase {
    private val mutex = Mutex()
    private var lastRefreshAt: Long = 0

    override suspend fun invoke(): Result<Unit> = withContext(dispatcherProvider.io) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastRefreshAt < MIN_REFRESH_INTERVAL_MS) return@withLock Result.success(Unit)
            val result = runCatching { refreshArticlesUseCase() }
            
            if (result.isSuccess) {
                lastRefreshAt = now
                suggestedThemesStateRepository.setLastFeedRefreshAt(now)
                // Also refresh recommendations since we have new articles
                runCatching { getSuggestedThemesUseCase(forceRefresh = false).collect() }
            }

            result
        }
    }

    companion object {
        private const val MIN_REFRESH_INTERVAL_MS = 5_000L
    }
}









