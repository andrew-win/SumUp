package com.andrewwin.sumup.domain.usecase.common

import com.andrewwin.sumup.data.local.entities.DeduplicationStrategy
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.service.ArticleCluster
import com.andrewwin.sumup.domain.service.ArticleImportanceScorer
import com.andrewwin.sumup.domain.service.DeduplicationService
import com.andrewwin.sumup.domain.support.DebugTrace
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import javax.inject.Inject

class CollectScheduledSummaryArticlesUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val articleImportanceScorer: ArticleImportanceScorer,
    private val deduplicationService: DeduplicationService,
    private val aiRepository: AiRepository,
    private val manageModelUseCase: ManageModelUseCase
) {
    suspend operator fun invoke(): List<Article> {
        val prefs = userPreferencesRepository.preferences.first()
        val sinceTimestamp = System.currentTimeMillis() - LAST_24_HOURS_MS
        val recentArticles = articleRepository.getEnabledArticlesSince(sinceTimestamp)

        val hasCloudEmbedding = aiRepository.hasEnabledEmbeddingConfig()
        val resolvedModelPath = resolveModelPath(prefs)
        val hasLocalEmbedding = resolvedModelPath?.let { deduplicationService.initialize(it) } ?: false
        val canDeduplicate = when (prefs.deduplicationStrategy) {
            DeduplicationStrategy.LOCAL -> hasLocalEmbedding
            DeduplicationStrategy.CLOUD -> hasCloudEmbedding
            DeduplicationStrategy.ADAPTIVE -> hasCloudEmbedding || hasLocalEmbedding
        }

        var filteredArticles = recentArticles
        if (prefs.isImportanceFilterEnabled) {
            filteredArticles = filteredArticles.filter { article ->
                val source = articleRepository.getSourceById(article.sourceId)
                val sourceType = source?.type ?: SourceType.RSS
                articleImportanceScorer.score(article, sourceType) >= ArticleImportanceScorer.IMPORTANCE_THRESHOLD
            }
        }

        DebugTrace.d(
            "scheduled_selection",
            "recentArticles=${recentArticles.size} filteredArticles=${filteredArticles.size} canDeduplicate=$canDeduplicate dedupEnabled=${prefs.isDeduplicationEnabled} dedupStrategy=${prefs.deduplicationStrategy} hideSingles=${prefs.isHideSingleNewsEnabled} minMentions=${prefs.minMentions} importanceFilter=${prefs.isImportanceFilterEnabled}"
        )

        val baseClusters: List<ArticleCluster> =
            if (!prefs.isDeduplicationEnabled || filteredArticles.size < 2 || !canDeduplicate) {
                filteredArticles.map { ArticleCluster(it, emptyList()) }
            } else {
                val dedupThreshold = when (prefs.deduplicationStrategy) {
                    DeduplicationStrategy.LOCAL -> prefs.localDeduplicationThreshold
                    DeduplicationStrategy.CLOUD -> prefs.cloudDeduplicationThreshold
                    DeduplicationStrategy.ADAPTIVE -> if (hasCloudEmbedding) {
                        prefs.cloudDeduplicationThreshold
                    } else {
                        prefs.localDeduplicationThreshold
                    }
                }
                deduplicationService.clusterArticlesIncremental(
                    filteredArticles,
                    dedupThreshold
                ).last()
            }

        DebugTrace.d(
            "scheduled_selection",
            "baseClusters=${baseClusters.size} mentions=${baseClusters.joinToString(",") { (it.duplicates.size + 1).toString() }} visibilityFilterBypassed=true"
        )

        val representatives = baseClusters
            .map { it.representative }
            .sortedByDescending { it.publishedAt }

        val selected = representatives.take(MAX_SCHEDULED_SUMMARY_ARTICLES)
        DebugTrace.d(
            "scheduled_selection",
            "representatives=${representatives.size} selected=${selected.size} ids=${selected.joinToString(",") { it.id.toString() }}"
        )
        return selected
    }

    private fun resolveModelPath(prefs: UserPreferences): String? {
        if (!prefs.modelPath.isNullOrBlank()) return prefs.modelPath
        return if (manageModelUseCase.isModelExists()) manageModelUseCase.getModelPath() else null
    }

    private companion object {
        const val LAST_24_HOURS_MS = 24 * 60 * 60 * 1000L
        const val MAX_SCHEDULED_SUMMARY_ARTICLES = 15
    }
}
