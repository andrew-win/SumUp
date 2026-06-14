package com.andrewwin.sumup.domain.feed.usecase

import android.util.Log
import com.andrewwin.sumup.domain.article.deduplication.SimilarityScorer
import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import com.andrewwin.sumup.domain.feed.pipeline.UpdateArticlesFromSources
import com.andrewwin.sumup.domain.feed.dedup.FeedDeduplicationProcessor
import com.andrewwin.sumup.domain.settings.repository.SuggestedThemesStateRepository
import com.andrewwin.sumup.domain.settings.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.source.util.SuggestedThemesRefreshPolicy
import com.andrewwin.sumup.domain.support.DispatcherProvider
import com.andrewwin.sumup.domain.source.usecase.GetRecommendationsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface RefreshFeedUseCase {
    suspend operator fun invoke(onStageChange: suspend (FeedRefreshProgress) -> Unit = {}): Result<FeedRefreshResult>
}

enum class FeedRefreshStage {
    PARSING_NEWS,
    DEDUPLICATING_NEWS
}

data class FeedRefreshProgress(
    val stage: FeedRefreshStage,
    val runId: Long
)

data class FeedRefreshResult(
    val cloudEmbeddingsIncomplete: Boolean = false
)

class RefreshFeedUseCaseImpl @Inject constructor(
    private val updateArticlesFromSources: UpdateArticlesFromSources,
    private val feedDeduplicationProcessor: FeedDeduplicationProcessor,
    private val articleRepository: ArticleRepository,
    private val similarityScorer: SimilarityScorer,
    private val getRecommendationsUseCase: GetRecommendationsUseCase,
    private val suggestedThemesStateRepository: SuggestedThemesStateRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dispatcherProvider: DispatcherProvider
) : RefreshFeedUseCase {
    private val mutex = Mutex()
    private var lastRefreshAt: Long = 0
    private var nextRunId: Long = 1
    private val backgroundScope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)

    override suspend fun invoke(onStageChange: suspend (FeedRefreshProgress) -> Unit): Result<FeedRefreshResult> = withContext(dispatcherProvider.io) {
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
                return@withLock Result.success(FeedRefreshResult())
            }
            val runId = nextRunId++
            val runStartedAt = System.currentTimeMillis()
            logRefreshDebug("refresh_use_case_started runId=$runId ageMs=$ageMs")
            val refreshResult = runCatching {
                emitStage(runId, FeedRefreshStage.PARSING_NEWS, onStageChange)
                val articleRefreshResult = updateArticlesFromSources()
                val prefs = userPreferencesRepository.preferences.first()
                var cloudEmbeddingsIncomplete = false
                if (prefs.isDeduplicationEnabled) {
                    emitStage(runId, FeedRefreshStage.DEDUPLICATING_NEWS, onStageChange)
                    delay(DEDUPE_STAGE_UI_BOUNDARY_MS)
                    val enabledArticles = articleRepository.getEnabledArticlesOnce().distinctBy { it.id }
                    val embeddingType = similarityScorer.embeddingTypeForStrategy(prefs.deduplicationStrategy)
                    val missingEmbeddingArticleIds = if (enabledArticles.size >= MIN_DEDUP_ARTICLES) {
                        articleRepository.getMissingEmbeddingArticleIds(
                            articleIds = enabledArticles.map { it.id },
                            embeddingType = embeddingType
                        )
                    } else {
                        emptyList()
                    }
                    val affectedArticleIds = (articleRefreshResult.changedArticleIds + missingEmbeddingArticleIds)
                        .distinct()
                    val deduplicationResult = affectedArticleIds
                        .takeIf { it.isNotEmpty() }
                        ?.let { ids ->
                            feedDeduplicationProcessor.ensureSimilaritiesForArticles(
                                prefs = prefs,
                                articleIds = ids
                            ).getOrThrow()
                        }
                    cloudEmbeddingsIncomplete = deduplicationResult?.cloudEmbeddingsIncomplete == true
                }
                FeedRefreshResult(cloudEmbeddingsIncomplete = cloudEmbeddingsIncomplete)
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
                runCatching { getRecommendationsUseCase(forceRefresh = false).collect() }
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

    private suspend fun emitStage(
        runId: Long,
        stage: FeedRefreshStage,
        onStageChange: suspend (FeedRefreshProgress) -> Unit
    ) {
        logRefreshDebug("refresh_use_case_stage runId=$runId stage=$stage")
        onStageChange(FeedRefreshProgress(stage = stage, runId = runId))
    }

    companion object {
        private const val MIN_REFRESH_INTERVAL_MS = 5_000L
        private const val DEDUPE_STAGE_UI_BOUNDARY_MS = 250L
        private const val MIN_DEDUP_ARTICLES = 2
        private const val REFRESH_TRIGGER_LOGS_ENABLED = false
        private const val REFRESH_TRIGGER_LOG_TAG = "RefreshTriggerDebug"
        private const val APP_PACKAGE_PREFIX = "com.andrewwin.sumup"
        private const val CALLER_STACK_FRAME_LIMIT = 8
    }
}


