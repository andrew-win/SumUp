package com.andrewwin.sumup.domain.usecase.sources

import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.local.entities.DeduplicationStrategy
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
            .filter { it.isEnabled && (it.sources.isNotEmpty() || it.recommendationAnchors.isNotEmpty()) }
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
        val sourceUrlById = allSources.associate { it.id to it.url }

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

        val recommendationMode = resolveRecommendationMode(prefsData.deduplicationStrategy)
        val isModelLoaded = when (recommendationMode) {
            RecommendationMode.CLOUD -> aiModelConfigRepository.getEnabledConfigsByType(AiModelType.EMBEDDING).isNotEmpty()
            RecommendationMode.LOCAL -> manageModelUseCase.isModelExists()
        }

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

        val localEmbeddingReady = if (recommendationMode == RecommendationMode.CLOUD) {
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

        val simThreshold = resolveSimilarityThreshold(
            recommendationMode = recommendationMode,
            cloudThreshold = prefsData.cloudDeduplicationThreshold
        )

        val suggestions = mutableListOf<ThemeSuggestion>()
        for (theme in themeProfiles) {
            val themeSourceUrls = theme.sources.map { it.url }.toSet()
            val themeUserEmbeddings = userArticles
                .filterNot { article -> sourceUrlById[article.sourceId] in themeSourceUrls }
                .mapNotNull { article ->
                    getEmbedding(article.title, recommendationMode)
                        .takeIf { it.isNotEmpty() }
                        ?.let { normalize(it) }
                }
            if (themeUserEmbeddings.isEmpty()) {
                suggestions.add(
                    ThemeSuggestion(
                        theme = theme,
                        score = 0f,
                        isSubscribed = theme.sources.all { allSourcesUrls.contains(it.url) },
                        isRecommended = false
                    )
                )
                continue
            }

            var relatedSources = 0
            var comparableSources = 0

            for (source in theme.sources) {
                val comparisonEmbeddings = getComparableSourceEmbeddings(
                    source = source,
                    allSourcesUrls = allSourcesUrls,
                    sourceIdByUrl = sourceIdByUrl,
                    allEnabledArticles = allEnabledArticles,
                    recommendationMode = recommendationMode
                )
                if (comparisonEmbeddings.isEmpty()) {
                    continue
                }
                comparableSources += 1
                if (countRelatedArticles(themeUserEmbeddings, comparisonEmbeddings, simThreshold) >= MIN_MATCHED_USER_ARTICLES_PER_SOURCE) {
                    relatedSources += 1
                }
            }

            if (comparableSources == 0) {
                val anchorEmbeddings = theme.recommendationAnchors
                    .take(MAX_ANCHORS_PER_THEME_RECOMMENDATIONS)
                    .mapNotNull { anchor ->
                        getEmbedding(anchor, recommendationMode)
                            .takeIf { it.isNotEmpty() }
                            ?.let { normalize(it) }
                    }
                if (anchorEmbeddings.isNotEmpty()) {
                    comparableSources = 1
                    if (countRelatedArticles(themeUserEmbeddings, anchorEmbeddings, simThreshold) >= MIN_MATCHED_USER_ARTICLES_PER_SOURCE) {
                        relatedSources = 1
                    }
                }
            }

            val relatedRatio = if (comparableSources > 0) {
                relatedSources.toFloat() / comparableSources.toFloat()
            } else {
                0f
            }
            suggestions.add(
                ThemeSuggestion(
                    theme = theme,
                    score = relatedRatio * PERCENT_SCALE,
                    isSubscribed = theme.sources.all { allSourcesUrls.contains(it.url) },
                    isRecommended = comparableSources > 0 && relatedRatio >= RECOMMENDED_THEME_MIN_RELATED_RATIO
                )
            )
        }

        val sorted = suggestions.sortedByDescending { it.score }
        val resultList = sorted
            .sortedByDescending { if (it.isRecommended) 1 else 0 }

        suggestedThemesStateRepository.saveRecommendationState(
            savedThemeIds = resultList.filter { it.isRecommended }.map { it.theme.id }.toSet(),
            sourcesHash = currentSourcesHash,
            timestamp = now
        )

        emit(resultList)
    }

    private suspend fun getComparableSourceEmbeddings(
        source: com.andrewwin.sumup.domain.repository.ImportedSource,
        allSourcesUrls: Set<String>,
        sourceIdByUrl: Map<String, Long>,
        allEnabledArticles: List<com.andrewwin.sumup.data.local.entities.Article>,
        recommendationMode: RecommendationMode
    ): List<FloatArray> {
        val sourceArticles = if (allSourcesUrls.contains(source.url)) {
            val sourceId = sourceIdByUrl[source.url]
            allEnabledArticles.filter { it.sourceId == sourceId }
        } else {
            fetchSourceArticles(source)
        }
        if (sourceArticles.isEmpty()) return emptyList()

        val averageViews = sourceArticles
            .asSequence()
            .map { it.viewCount }
            .filter { it > 0L }
            .average()
            .toLong()

        return sourceArticles
            .filter { article ->
                articleImportanceScorer.score(article, averageViews, source.type) >= ArticleImportanceScorer.IMPORTANCE_THRESHOLD
            }
            .sortedByDescending { it.publishedAt }
            .take(MAX_ARTICLES_PER_SOURCE_FOR_THEME_RECOMMENDATIONS)
            .mapNotNull { article ->
                getEmbedding(article.title, recommendationMode)
                    .takeIf { it.isNotEmpty() }
                    ?.let { normalize(it) }
            }
    }

    private suspend fun fetchSourceArticles(
        source: com.andrewwin.sumup.domain.repository.ImportedSource
    ): List<com.andrewwin.sumup.data.local.entities.Article> {
        return try {
            remoteArticleDataSource.fetchArticles(
                Source(
                    id = -1L,
                    groupId = -1L,
                    name = source.name.ifBlank { source.url },
                    url = source.url,
                    type = source.type
                )
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun countRelatedArticles(
        userEmbeddings: List<FloatArray>,
        comparisonEmbeddings: List<FloatArray>,
        simThreshold: Float
    ): Int {
        if (userEmbeddings.isEmpty() || comparisonEmbeddings.isEmpty()) return 0
        return userEmbeddings.count { userEmbedding ->
            comparisonEmbeddings.any { comparisonEmbedding ->
                dotProduct(userEmbedding, comparisonEmbedding) >= simThreshold
            }
        }
    }

    private fun resolveSimilarityThreshold(
        recommendationMode: RecommendationMode,
        cloudThreshold: Float
    ): Float {
        return when (recommendationMode) {
            RecommendationMode.CLOUD -> (cloudThreshold - CLOUD_RECOMMENDATION_SIMILARITY_THRESHOLD_OFFSET).coerceIn(0f, 0.99f)
            RecommendationMode.LOCAL -> LOCAL_RECOMMENDATION_SIMILARITY_THRESHOLD
        }
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

    private suspend fun getEmbedding(text: String, recommendationMode: RecommendationMode): FloatArray {
        if (recommendationMode == RecommendationMode.CLOUD) {
            generateCloudEmbeddingUseCase(text)?.let { return it }
        }
        return embeddingService.getEmbedding(text)
    }

    private fun resolveRecommendationMode(strategy: DeduplicationStrategy): RecommendationMode {
        return when (strategy) {
            DeduplicationStrategy.CLOUD -> RecommendationMode.CLOUD
            DeduplicationStrategy.LOCAL -> RecommendationMode.LOCAL
        }
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
        private const val MAX_ARTICLES_PER_SOURCE_FOR_THEME_RECOMMENDATIONS = 3
        private const val MAX_ANCHORS_PER_THEME_RECOMMENDATIONS = 3
        private const val MIN_MATCHED_USER_ARTICLES_PER_SOURCE = 1
        private const val CLOUD_RECOMMENDATION_SIMILARITY_THRESHOLD_OFFSET = 0.015f
        private const val LOCAL_RECOMMENDATION_SIMILARITY_THRESHOLD = 0.4f
        private const val RECOMMENDED_THEME_MIN_RELATED_RATIO = 0.33f
        private const val PERCENT_SCALE = 100f
    }
}

private enum class RecommendationMode {
    CLOUD,
    LOCAL
}






