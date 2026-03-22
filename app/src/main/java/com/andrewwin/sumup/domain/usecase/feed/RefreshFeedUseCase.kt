package com.andrewwin.sumup.domain.usecase.feed

import com.andrewwin.sumup.domain.coroutines.DispatcherProvider
import com.andrewwin.sumup.domain.logger.PerformanceLogger
import com.andrewwin.sumup.domain.usecase.RefreshArticlesUseCase
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
    private val dispatcherProvider: DispatcherProvider,
    private val performanceLogger: PerformanceLogger
) : RefreshFeedUseCase {
    private val mutex = Mutex()
    private var lastRefreshAt: Long = 0

    override suspend fun invoke(): Result<Unit> = withContext(dispatcherProvider.io) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastRefreshAt < MIN_REFRESH_INTERVAL_MS) return@withLock Result.success(Unit)
            val start = System.nanoTime()
            
            val result = runCatching { refreshArticlesUseCase() }
            
            if (result.isSuccess) {
                lastRefreshAt = now
                // Also refresh recommendations since we have new articles
                runCatching { getSuggestedThemesUseCase(forceRefresh = false).collect() }
            }

            val durationMs = (System.nanoTime() - start) / 1_000_000
            performanceLogger.log(TAG, "refresh result=${result.isSuccess} ms=$durationMs")
            result
        }
    }

    companion object {
        private const val MIN_REFRESH_INTERVAL_MS = 5_000L
        private const val TAG = "FeedPerf"
    }
}
