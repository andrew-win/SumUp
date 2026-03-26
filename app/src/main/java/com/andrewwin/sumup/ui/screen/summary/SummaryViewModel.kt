package com.andrewwin.sumup.ui.screen.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.service.ArticleImportanceScorer
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.SummaryRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.usecase.common.GenerateSummaryUseCase
import com.andrewwin.sumup.domain.usecase.common.NoArticlesException
import com.andrewwin.sumup.domain.usecase.common.RefreshArticlesUseCase
import com.andrewwin.sumup.domain.usecase.feed.GetFeedArticlesUseCase
import com.andrewwin.sumup.worker.SummaryWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SummaryChartType {
    VIEWS, MENTIONS, FACTUALITY
}

data class SummaryChartItem(
    val headline: String,
    val value: Float,
    val displayValue: String,
    val sourceName: String? = null,
    val sourceUrl: String? = null
)

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val summaryRepository: SummaryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val workManager: WorkManager,
    private val refreshArticlesUseCase: RefreshArticlesUseCase,
    private val generateSummaryUseCase: GenerateSummaryUseCase,
    private val getFeedArticlesUseCase: GetFeedArticlesUseCase,
    private val importanceScorer: ArticleImportanceScorer,
    private val sourceRepository: SourceRepository,
    private val aiRepository: AiRepository
) : ViewModel() {

    val summaries: StateFlow<List<Summary>> = summaryRepository.allSummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val workInfo: StateFlow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(SCHEDULED_SUMMARY_WORK_NAME)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _chartType = MutableStateFlow(SummaryChartType.VIEWS)
    val chartType: StateFlow<SummaryChartType> = _chartType.asStateFlow()

    val isVectorizationEnabled: StateFlow<Boolean> = combine(
        userPreferences,
        aiRepository.getConfigsByType(AiModelType.EMBEDDING)
    ) { prefs, embeddingConfigs ->
        prefs.modelPath != null || embeddingConfigs.any { it.isEnabled }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val chartData: StateFlow<List<SummaryChartItem>> = combine(
        getFeedArticlesUseCase(
            searchQueryFlow = flowOf(""),
            selectedGroupIdFlow = flowOf(null),
            dateFilterHoursFlow = flowOf(24), // Last 24h for chart
            savedOnlyFlow = flowOf(false),
            userPreferencesFlow = userPreferences
        ),
        _chartType,
        userPreferences,
        sourceRepository.groupsWithSources
    ) { feedResult, type, prefs, groups ->
        val sourceById = groups.flatMap { it.sources }.associateBy { it.id }
        val sourceTypeMap = sourceById.mapValues { it.value.type }
        val clusters = feedResult.clusters
        val limit = prefs.showInfographicNewsCount.coerceAtLeast(1)
        
        when (type) {
            SummaryChartType.VIEWS -> {
                clusters.map { cluster ->
                    val totalViews = cluster.representative.viewCount + cluster.duplicates.sumOf { it.first.viewCount }
                    val source = sourceById[cluster.representative.sourceId]
                    SummaryChartItem(
                        headline = cluster.representative.title,
                        value = totalViews.toFloat(),
                        displayValue = formatViews(totalViews),
                        sourceName = source?.name,
                        sourceUrl = cluster.representative.url.takeIf { it.isNotBlank() } ?: source?.url
                    )
                }.sortedByDescending { it.value }.take(limit)
            }
            SummaryChartType.MENTIONS -> {
                clusters.map { cluster ->
                    val count = cluster.duplicates.size + 1
                    val source = sourceById[cluster.representative.sourceId]
                    SummaryChartItem(
                        headline = cluster.representative.title,
                        value = count.toFloat(),
                        displayValue = count.toString(),
                        sourceName = source?.name,
                        sourceUrl = cluster.representative.url.takeIf { it.isNotBlank() } ?: source?.url
                    )
                }.sortedByDescending { it.value }.take(limit)
            }
            SummaryChartType.FACTUALITY -> {
                clusters.map { cluster ->
                    val article = cluster.representative
                    val score = importanceScorer.score(article, sourceTypeMap[article.sourceId] ?: SourceType.RSS)
                    val source = sourceById[article.sourceId]
                    SummaryChartItem(
                        headline = article.title,
                        value = score,
                        displayValue = "%.2f".format(score),
                        sourceName = source?.name,
                        sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url
                    )
                }.sortedByDescending { it.value }.take(limit)
            }
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setChartType(type: SummaryChartType) {
        _chartType.value = type
    }

    fun generateSummaryNow() {
        viewModelScope.launch {
            _isGenerating.value = true
            runCatching {
                refreshArticlesUseCase()
                val summaryText = generateSummaryUseCase()
                summaryRepository.insertSummary(Summary(content = summaryText, strategy = userPreferences.value.aiStrategy))
            }.onFailure { e ->
                val message = when (e) {
                    is NoArticlesException -> return@onFailure
                    else -> e.localizedMessage.orEmpty()
                }
                summaryRepository.insertSummary(Summary(content = message, strategy = userPreferences.value.aiStrategy))
            }
            _isGenerating.value = false
        }
    }

    private fun formatViews(views: Long): String {
        return when {
            views >= 1_000_000 -> "%.1fM".format(views / 1_000_000f)
            views >= 1_000 -> "%.1fK".format(views / 1_000f)
            else -> views.toString()
        }
    }

    fun testWorkerNow() {
        val request = OneTimeWorkRequestBuilder<SummaryWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueue(request)
    }

    companion object {
        private const val SCHEDULED_SUMMARY_WORK_NAME = "scheduled_summary"
    }
}







