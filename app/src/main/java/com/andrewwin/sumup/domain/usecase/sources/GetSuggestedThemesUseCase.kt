package com.andrewwin.sumup.domain.usecase.sources

import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.RemoteArticleDataSource
import com.andrewwin.sumup.data.repository.PublicSubscriptionsSyncManager
import com.andrewwin.sumup.domain.service.ArticleImportanceScorer
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.domain.repository.AiModelConfigRepository
import com.andrewwin.sumup.domain.repository.ImportedSourceGroup
import com.andrewwin.sumup.domain.usecase.ai.GenerateCloudEmbeddingUseCase
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.EmbeddingService
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.SuggestedThemesStateRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

data class ThemeSuggestion(
    val theme: ImportedSourceGroup,
    val score: Float,
    val isSubscribed: Boolean = false,
    val isRecommended: Boolean = false
)

@Singleton
class GetSuggestedThemesUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val sourceRepository: SourceRepository,
    private val aiModelConfigRepository: AiModelConfigRepository,
    private val generateCloudEmbeddingUseCase: GenerateCloudEmbeddingUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val embeddingService: EmbeddingService,
    private val manageModelUseCase: ManageModelUseCase,
    private val remoteArticleDataSource: RemoteArticleDataSource,
    private val articleImportanceScorer: ArticleImportanceScorer,
    private val suggestedThemesStateRepository: SuggestedThemesStateRepository,
    private val publicSubscriptionsSyncManager: PublicSubscriptionsSyncManager
) {
    operator fun invoke(forceRefresh: Boolean = false): Flow<List<ThemeSuggestion>> = flow {
        val prefsData = userPreferencesRepository.preferences.first()
        if (!prefsData.isRecommendationsEnabled) {
            emit(emptyList())
            return@flow
        }

        val themeProfiles = publicSubscriptionsSyncManager.getCachedGroups()
            .filter { it.isEnabled && it.recommendationAnchors.isNotEmpty() }
            .sortedBy { it.sortOrder }
        if (themeProfiles.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val groupsWithSources = sourceRepository.groupsWithSources.first()
        val allSources = groupsWithSources.flatMap { it.sources }
        val allSourcesUrls = allSources.map { it.url }.toSet()
        val sourceIdByUrl = allSources.associate { it.url to it.id }
        val currentSourcesHash = allSourcesUrls.hashCode()
        val sourceTypeMap = allSources.associate { it.id to it.type }

        val savedThemeIds = suggestedThemesStateRepository.getSavedThemeIds()
        val savedThemeTitlesLegacy = suggestedThemesStateRepository.getSavedThemeTitlesLegacy()
        val lastRecommendationAt = suggestedThemesStateRepository.getLastRecommendationAt()
        val lastFeedRefreshAt = suggestedThemesStateRepository.getLastFeedRefreshAt()
        val now = System.currentTimeMillis()
        val shouldRecalculate = forceRefresh || (
            (now - lastRecommendationAt) >= SuggestedThemesRefreshConstants.REFRESH_INTERVAL_MS &&
            lastFeedRefreshAt > lastRecommendationAt
        )

        if (!shouldRecalculate) {
            val cached = themeProfiles.map {
                ThemeSuggestion(
                    it,
                    score = 10f,
                    isSubscribed = it.sources.all { s -> allSourcesUrls.contains(s.url) },
                    isRecommended = isThemeSaved(it, savedThemeIds, savedThemeTitlesLegacy)
                )
            }.sortedByDescending { if (it.isRecommended) 1 else 0 }
            emit(cached)
            return@flow
        }

        val hasCloudEmbedding = aiModelConfigRepository.getEnabledConfigsByType(AiModelType.EMBEDDING).isNotEmpty()
        val isModelLoaded = hasCloudEmbedding || manageModelUseCase.isModelExists()

        if (!isModelLoaded) {
            emit(themeProfiles.map { 
                ThemeSuggestion(it, 0f, isSubscribed = it.sources.all { s -> allSourcesUrls.contains(s.url) })
            })
            return@flow
        }
        
        // If we have cache and refresh window has not passed, return cache.
        if (!forceRefresh && (savedThemeIds != null || savedThemeTitlesLegacy != null) && !shouldRecalculate) {
            val cached = themeProfiles.map {
                 ThemeSuggestion(
                     it,
                     score = 10f,
                     isSubscribed = it.sources.all { s -> allSourcesUrls.contains(s.url) },
                     isRecommended = isThemeSaved(it, savedThemeIds, savedThemeTitlesLegacy)
                 ) 
            }.sortedByDescending { if (it.isRecommended) 1 else 0 }
            emit(cached)
            return@flow
        }

        // Initial emit: if no cache, emit all. If cache exists (even if out of date or forcing), emit it while recalculating
        if (savedThemeIds == null && savedThemeTitlesLegacy == null) {
            emit(themeProfiles.map { 
                ThemeSuggestion(it, 0f, isSubscribed = it.sources.all { s -> allSourcesUrls.contains(s.url) })
            })
        } else {
            val cached = themeProfiles.map { 
                 ThemeSuggestion(
                     it,
                     score = 10f,
                     isSubscribed = it.sources.all { s -> allSourcesUrls.contains(s.url) },
                     isRecommended = isThemeSaved(it, savedThemeIds, savedThemeTitlesLegacy)
                 ) 
            }.sortedByDescending { if (it.isRecommended) 1 else 0 }
            emit(cached)
        }
        
        val allEnabledArticles = articleRepository.getEnabledArticlesOnce()
        val userArticles = allEnabledArticles
            .groupBy { it.sourceId }
            .flatMap { entry -> 
                val averageViews = entry.value
                    .asSequence()
                    .map { it.viewCount }
                    .filter { it > 0L }
                    .average()
                    .toLong()
                entry.value.sortedByDescending { it.publishedAt }
                     .filter { 
                         val importance = articleImportanceScorer.score(
                             article = it,
                             averageViews = averageViews,
                             sourceType = sourceTypeMap[it.sourceId] ?: SourceType.RSS
                         )
                         importance >= ArticleImportanceScorer.IMPORTANCE_THRESHOLD
                     }
                     .take(MAX_ARTICLES_PER_SOURCE_FOR_THEME_RECOMMENDATIONS)
            }

        if (userArticles.isEmpty()) {
            emit(themeProfiles.map { 
                ThemeSuggestion(it, 0f, isSubscribed = it.sources.all { s -> allSourcesUrls.contains(s.url) })
            })
            return@flow
        }

        val localEmbeddingReady = if (hasCloudEmbedding) {
            true
        } else {
            val modelPath = manageModelUseCase.getModelPath()
            embeddingService.initialize(modelPath)
        }
        if (!localEmbeddingReady) {
            emit(themeProfiles.map { 
                ThemeSuggestion(it, 0f, isSubscribed = it.sources.all { s -> allSourcesUrls.contains(s.url) })
            })
            return@flow
        }

        val simThreshold = (
            if (hasCloudEmbedding) {
                prefsData.cloudDeduplicationThreshold - CLOUD_RECOMMENDATION_SIMILARITY_THRESHOLD_OFFSET
            } else {
                prefsData.localDeduplicationThreshold - LOCAL_RECOMMENDATION_SIMILARITY_THRESHOLD_OFFSET
            }
        ).coerceIn(0f, 0.99f)

        val userEmbeddings = userArticles.mapNotNull {
           val emb = getEmbedding(it.title, hasCloudEmbedding)
           it.title to normalize(emb)
        }

        val staticAnchorEmbeddings: Map<ImportedSourceGroup, FloatArray> = themeProfiles.associateWith { theme ->
            val anchorEmbs = theme.recommendationAnchors.map { normalize(getEmbedding(it, hasCloudEmbedding)) }
            val vectorSize = anchorEmbs.first().size
            val sumVector = FloatArray(vectorSize)
            for (emb in anchorEmbs) {
                for (i in 0 until vectorSize) sumVector[i] += emb[i]
            }
            normalize(FloatArray(vectorSize) { sumVector[it] / anchorEmbs.size })
        }

        val suggestions = mutableListOf<ThemeSuggestion>()
        for (theme in themeProfiles) {
            val firstSource = theme.sources.firstOrNull()
            if (firstSource == null) {
                suggestions.add(ThemeSuggestion(theme, 0f, isSubscribed = false))
                continue
            }
            
            // Optimization: if we already have articles for this source, use them instead of fetching
            val themeArticles = if (allSourcesUrls.contains(firstSource.url)) {
                val sId = sourceIdByUrl[firstSource.url]
                allEnabledArticles.filter { it.sourceId == sId }
            } else {
                try {
                    remoteArticleDataSource.fetchArticles(
                        Source(
                            id = -1L,
                            groupId = -1L,
                            name = firstSource.url,
                            url = firstSource.url,
                            type = firstSource.type
                        )
                    )
                } catch (e: Exception) {
                    emptyList()
                }
            }

            if (themeArticles.isEmpty()) {
                suggestions.add(ThemeSuggestion(theme, 0f, isSubscribed = theme.sources.all { allSourcesUrls.contains(it.url) }))
                continue
            }
            
            val themeEmbeddings = themeArticles
                .let { articles ->
                    val averageViews = articles
                        .asSequence()
                        .map { it.viewCount }
                        .filter { it > 0L }
                        .average()
                        .toLong()
                    articles to averageViews
                }
                .let { (articles, averageViews) ->
                    articles.filter {
                        val importance = articleImportanceScorer.score(it, averageViews, firstSource.type)
                        importance >= ArticleImportanceScorer.IMPORTANCE_THRESHOLD
                    }
                }
                .take(MAX_ARTICLES_PER_SOURCE_FOR_THEME_RECOMMENDATIONS)
                .mapNotNull { 
                    val emb = getEmbedding(it.title, hasCloudEmbedding)
                    normalize(emb)
                }

            if (themeEmbeddings.isEmpty()) {
                suggestions.add(ThemeSuggestion(theme, 0f, isSubscribed = theme.sources.all { allSourcesUrls.contains(it.url) }))
                continue
            }

            // Combine static anchors + fetched article embeddings into one anchor vector
            val staticEmb = staticAnchorEmbeddings[theme]!!
            val vectorSize = staticEmb.size
            val allEmbs = themeEmbeddings + listOf(staticEmb)
            val sumVector = FloatArray(vectorSize)
            for (emb in allEmbs) {
                for (i in 0 until vectorSize) {
                    sumVector[i] += emb[i]
                }
            }
            val themeAnchorEmb = normalize(FloatArray(vectorSize) { sumVector[it] / allEmbs.size })

            var matchScore = 0f
            for ((uTitle, uEmb) in userEmbeddings) {
                val sim = dotProduct(uEmb, themeAnchorEmb)
                if (sim >= simThreshold) {
                    matchScore += sim
                }
            }
            suggestions.add(ThemeSuggestion(theme, matchScore, isSubscribed = theme.sources.all { allSourcesUrls.contains(it.url) }))
        }

        val sorted = suggestions.sortedByDescending { it.score }
        val resultList = sorted
            .map { it.copy(isRecommended = it.score >= RECOMMENDED_THEME_MIN_SCORE) }
            .sortedByDescending { if (it.isRecommended) 1 else 0 }

        suggestedThemesStateRepository.saveRecommendationState(
            savedThemeIds = resultList.filter { it.isRecommended }.map { it.theme.id }.toSet(),
            sourcesHash = currentSourcesHash,
            timestamp = now
        )

        emit(resultList)
    }

    private fun normalize(vector: FloatArray): FloatArray {
        val mag = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        return if (mag > 0f) FloatArray(vector.size) { vector[it] / mag } else vector
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    private suspend fun getEmbedding(text: String, hasCloudEmbedding: Boolean): FloatArray {
        if (hasCloudEmbedding) {
            generateCloudEmbeddingUseCase(text)?.let { return it }
        }
        return embeddingService.getEmbedding(text)
    }

    private fun isThemeSaved(
        theme: ImportedSourceGroup,
        savedThemeIds: Set<String>?,
        savedThemeTitlesLegacy: Set<String>?
    ): Boolean {
        return savedThemeIds?.contains(theme.id) == true ||
            savedThemeTitlesLegacy?.contains(theme.name) == true ||
            savedThemeTitlesLegacy?.contains(theme.nameUk) == true ||
            savedThemeTitlesLegacy?.contains(theme.nameEn) == true
    }

    private companion object {
        private const val MAX_ARTICLES_PER_SOURCE_FOR_THEME_RECOMMENDATIONS = 7
        private const val CLOUD_RECOMMENDATION_SIMILARITY_THRESHOLD_OFFSET = 0.015f
        private const val LOCAL_RECOMMENDATION_SIMILARITY_THRESHOLD_OFFSET = 0.2f
        private const val RECOMMENDED_THEME_MIN_SCORE = 2.5f
    }
}









