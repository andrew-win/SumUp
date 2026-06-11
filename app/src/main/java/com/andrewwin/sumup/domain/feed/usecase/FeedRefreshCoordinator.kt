package com.andrewwin.sumup.domain.feed.usecase

import android.util.Log
import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

enum class FeedRefreshStatus {
    IDLE,
    PARSING_NEWS,
    DEDUPLICATING_NEWS,
    BUILDING_FEED
}

data class FeedRefreshCoordinatorState(
    val status: FeedRefreshStatus = FeedRefreshStatus.IDLE,
    val buildSignal: Long? = null
) {
    val isRefreshing: Boolean
        get() = status != FeedRefreshStatus.IDLE
}

@Singleton
class FeedRefreshCoordinator @Inject constructor(
    private val refreshFeedUseCase: RefreshFeedUseCase,
    private val articleRepository: ArticleRepository
) {
    private val mutex = Mutex()
    private val isStartupRefreshRequested = AtomicBoolean(false)
    private val _state = MutableStateFlow(FeedRefreshCoordinatorState())

    val state: StateFlow<FeedRefreshCoordinatorState> = _state.asStateFlow()

    suspend fun refreshOnAppStart() {
        if (!isStartupRefreshRequested.compareAndSet(false, true)) return

        delay(STARTUP_REFRESH_DELAY_MS)
        refresh()
    }

    suspend fun refreshManually(): Result<Unit> = refresh()

    fun markFeedBuildFinished(signal: Long) {
        _state.update { state ->
            if (state.status == FeedRefreshStatus.BUILDING_FEED && state.buildSignal == signal) {
                FeedRefreshCoordinatorState()
            } else {
                state
            }
        }
    }

    private suspend fun refresh(): Result<Unit> {
        if (_state.value.isRefreshing) return Result.success(Unit)

        return mutex.withLock {
            if (_state.value.isRefreshing) return@withLock Result.success(Unit)

            val result = runCatching {
                refreshFeedUseCase { progress ->
                    _state.value = _state.value.copy(status = progress.stage.toStatus())
                }.getOrThrow()
            }

            result.fold(
                onSuccess = {
                    val signal = System.currentTimeMillis()
                    articleRepository.requestFeedRefresh(signal)
                    _state.value = FeedRefreshCoordinatorState(
                        status = FeedRefreshStatus.BUILDING_FEED,
                        buildSignal = signal
                    )
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Log.w(LOG_TAG, "Feed refresh failed", error)
                    _state.value = FeedRefreshCoordinatorState()
                    Result.failure(error)
                }
            )
        }
    }

    private fun FeedRefreshStage.toStatus(): FeedRefreshStatus {
        return when (this) {
            FeedRefreshStage.PARSING_NEWS -> FeedRefreshStatus.PARSING_NEWS
            FeedRefreshStage.DEDUPLICATING_NEWS -> FeedRefreshStatus.DEDUPLICATING_NEWS
        }
    }

    private companion object {
        private const val STARTUP_REFRESH_DELAY_MS = 1_000L
        private const val LOG_TAG = "FeedRefreshCoordinator"
    }
}
