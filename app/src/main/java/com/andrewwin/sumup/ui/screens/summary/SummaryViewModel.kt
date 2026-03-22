package com.andrewwin.sumup.ui.screens.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.ArticleImportanceScorer
import com.andrewwin.sumup.domain.repository.SummaryRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.usecase.GenerateSummaryUseCase
import com.andrewwin.sumup.domain.usecase.NoArticlesException
import com.andrewwin.sumup.domain.usecase.RefreshArticlesUseCase
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
    val displayValue: String
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
    private val sourceRepository: SourceRepository
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

    val chartData: StateFlow<List<SummaryChartItem>> = combine(
        getFeedArticlesUseCase(
            searchQueryFlow = flowOf(""),
            selectedGroupIdFlow = flowOf(null),
            dateFilterHoursFlow = flowOf(24), // Last 24h for chart
            userPreferencesFlow = userPreferences
        ),
        _chartType,
        sourceRepository.groupsWithSources
    ) { feedResult, type, groups ->
        val sourceTypeMap = groups.flatMap { it.sources }.associate { it.id to it.type }
        val clusters = feedResult.clusters
        
        when (type) {
            SummaryChartType.VIEWS -> {
                clusters.map { cluster ->
                    val totalViews = cluster.representative.viewCount + cluster.duplicates.sumOf { it.first.viewCount }
                    SummaryChartItem(
                        headline = cluster.representative.title,
                        value = totalViews.toFloat(),
                        displayValue = formatViews(totalViews)
                    )
                }.sortedByDescending { it.value }.take(7)
            }
            SummaryChartType.MENTIONS -> {
                clusters.map { cluster ->
                    val count = cluster.duplicates.size + 1
                    SummaryChartItem(
                        headline = cluster.representative.title,
                        value = count.toFloat(),
                        displayValue = count.toString()
                    )
                }.sortedByDescending { it.value }.take(7)
            }
            SummaryChartType.FACTUALITY -> {
                clusters.map { cluster ->
                    val article = cluster.representative
                    val score = importanceScorer.score(article, sourceTypeMap[article.sourceId] ?: SourceType.RSS)
                    SummaryChartItem(
                        headline = article.title,
                        value = score,
                        displayValue = "%.2f".format(score)
                    )
                }.sortedByDescending { it.value }.take(7)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
