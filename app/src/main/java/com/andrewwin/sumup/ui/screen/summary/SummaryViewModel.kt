package com.andrewwin.sumup.ui.screen.summary

import com.andrewwin.sumup.ui.screen.summary.model.SummaryChartItem

import com.andrewwin.sumup.ui.screen.summary.model.SummaryChartType

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.andrewwin.sumup.R
import com.andrewwin.sumup.domain.ai.model.AiModelType
import com.andrewwin.sumup.domain.summary.formatter.SummaryExecutionInfoFormatter
import com.andrewwin.sumup.domain.ai.service.SummaryExecutionInfoStore
import com.andrewwin.sumup.domain.export.model.ExportDestination
import com.andrewwin.sumup.domain.export.model.SummaryExportItem
import com.andrewwin.sumup.domain.export.model.SummaryExportStrategy
import com.andrewwin.sumup.domain.ai.repository.AiModelConfigRepository
import com.andrewwin.sumup.domain.summary.repository.SummaryRepository
import com.andrewwin.sumup.domain.settings.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.settings.model.AiStrategy
import com.andrewwin.sumup.domain.settings.model.UserSettings
import com.andrewwin.sumup.domain.summary.model.SummaryRecord
import com.andrewwin.sumup.domain.summary.scheduled.NoArticlesException
import com.andrewwin.sumup.domain.summary.scheduled.ScheduledSummaryTextGenerator
import com.andrewwin.sumup.domain.support.AllAiModelsFailedException
import com.andrewwin.sumup.domain.export.service.ExportSummariesUseCase
import com.andrewwin.sumup.domain.summary.usecase.CreateNewsStatisticsUseCase
import com.andrewwin.sumup.domain.summary.usecase.NewsStatisticsMetric
import com.andrewwin.sumup.domain.summary.usecase.NewsStatisticsType
import com.andrewwin.sumup.worker.summary.model.ScheduledSummaryWorkKind
import com.andrewwin.sumup.worker.summary.SummaryWorker
import com.andrewwin.sumup.worker.summary.SummaryConstants
import com.andrewwin.sumup.worker.summary.SummaryWorkerHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SummaryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val summaryRepository: SummaryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val workManager: WorkManager,
    private val scheduledSummaryTextGenerator: ScheduledSummaryTextGenerator,
    private val createNewsStatisticsUseCase: CreateNewsStatisticsUseCase,
    private val exportSummariesUseCase: ExportSummariesUseCase,
    private val aiModelConfigRepository: AiModelConfigRepository,
    private val summaryExecutionInfoFormatter: SummaryExecutionInfoFormatter,
    private val summaryExecutionInfoStore: SummaryExecutionInfoStore
) : ViewModel() {

    val summaries: StateFlow<List<SummaryRecord>> = summaryRepository.allSummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPreferences: StateFlow<UserSettings> = userPreferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val workInfo: StateFlow<List<WorkInfo>> =
        workManager.getWorkInfosByTagFlow(SummaryConstants.SCHEDULED_SUMMARY_WORK_TAG)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _chartType = MutableStateFlow(SummaryChartType.VIEWS)
    val chartType: StateFlow<SummaryChartType> = _chartType.asStateFlow()

    val isVectorizationEnabled: StateFlow<Boolean> = combine(
        userPreferences,
        aiModelConfigRepository.getConfigsByType(AiModelType.EMBEDDING)
    ) { prefs, embeddingConfigs ->
        prefs.modelPath != null || embeddingConfigs.any { it.isEnabled }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val activeSummaryModelName: StateFlow<String?> = aiModelConfigRepository.getConfigsByType(AiModelType.SUMMARY)
        .combine(aiModelConfigRepository.lastUsedSummaryModelName) { configs, lastUsed ->
            lastUsed?.takeIf { it.isNotBlank() }
                ?: configs.firstOrNull { it.isEnabled }?.modelName?.takeIf { it.isNotBlank() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chartData: StateFlow<List<SummaryChartItem>> = createNewsStatisticsUseCase(
        chartTypeFlow = _chartType.map { it.toNewsStatisticsType() },
        userPreferencesFlow = userPreferences
    ).map { items ->
        items.map { item ->
            SummaryChartItem(
                headline = item.headline,
                value = item.value,
                displayValue = item.metric.toDisplayValue(context, ::formatViews),
                sourceName = item.sourceName,
                sourceUrl = item.sourceUrl,
                isValueUnavailable = item.metric is NewsStatisticsMetric.Views && !item.metric.hasKnownViews
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setChartType(type: SummaryChartType) {
        _chartType.value = type
    }

    fun generateSummaryNow() {
        viewModelScope.launch {
            _isGenerating.value = true
            runCatching {
                summaryExecutionInfoStore.clear()
                val summaryText = scheduledSummaryTextGenerator(refresh = true)
                val executionInfo = summaryExecutionInfoStore.current()
                summaryRepository.insertSummary(
                    SummaryRecord(
                        content = summaryText,
                        strategy = userPreferences.value.aiStrategy,
                        executionLabel = executionInfo.label.takeIf { it.isNotBlank() },
                        executionNote = executionInfo.note.takeIf { it.isNotBlank() }
                    )
                )
            }.onFailure { e ->
                val message = when (e) {
                    is NoArticlesException -> return@onFailure
                    is AllAiModelsFailedException -> summaryExecutionInfoFormatter.buildCloudFailureText(e.failures)
                    else -> e.localizedMessage.orEmpty()
                }
                val executionInfo = if (e is AllAiModelsFailedException) {
                    summaryExecutionInfoFormatter.buildCloudFailureInfo(
                        strategy = userPreferences.value.aiStrategy,
                        failures = e.failures
                    )
                } else {
                    summaryExecutionInfoStore.current()
                }
                summaryRepository.insertSummary(
                    SummaryRecord(
                        content = message,
                        strategy = userPreferences.value.aiStrategy,
                        isError = true,
                        executionLabel = executionInfo.label.takeIf { it.isNotBlank() },
                        executionNote = executionInfo.note.takeIf { it.isNotBlank() }
                    )
                )
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
            .setInputData(
                workDataOf(
                    SummaryWorkerHandler.KEY_SCHEDULED_SUMMARY_WORK_KIND to ScheduledSummaryWorkKind.PREPARE.name,
                    SummaryWorkerHandler.KEY_SCHEDULED_SUMMARY_AT to System.currentTimeMillis()
                )
            )
            .build()
        workManager.enqueue(request)
    }

    fun deleteSummary(summaryId: Long) {
        viewModelScope.launch {
            summaryRepository.deleteSummaryById(summaryId)
        }
    }

    fun deleteSummaries(summaryIds: List<Long>) {
        viewModelScope.launch {
            summaryRepository.deleteSummariesByIds(summaryIds)
        }
    }

    suspend fun exportSummaries(summaries: List<SummaryRecord>, uri: Uri): Result<Unit> {
        return exportSummariesUseCase(
            summaries = summaries.map { summary ->
                SummaryExportItem(
                    content = summary.content,
                    createdAt = summary.createdAt,
                    strategy = when (summary.strategy) {
                        AiStrategy.CLOUD -> SummaryExportStrategy.CLOUD
                        AiStrategy.LOCAL -> SummaryExportStrategy.LOCAL
                        AiStrategy.ADAPTIVE -> SummaryExportStrategy.ADAPTIVE
                    }
                )
            },
            destination = ExportDestination {
                context.contentResolver.openOutputStream(uri)
            }
        )
    }

    fun toggleFavorite(summary: SummaryRecord) {
        viewModelScope.launch {
            summaryRepository.setFavorite(summary.id, !summary.isFavorite)
        }
    }

    private fun SummaryChartType.toNewsStatisticsType(): NewsStatisticsType {
        return when (this) {
            SummaryChartType.VIEWS -> NewsStatisticsType.VIEWS
            SummaryChartType.MENTIONS -> NewsStatisticsType.MENTIONS
            SummaryChartType.FACTUALITY -> NewsStatisticsType.FACTUALITY
        }
    }

    private fun NewsStatisticsMetric.toDisplayValue(
        context: Context,
        viewFormatter: (Long) -> String
    ): String {
        return when (this) {
            is NewsStatisticsMetric.Views -> {
                if (hasKnownViews) {
                    context.getString(R.string.summary_stat_views_count, viewFormatter(totalViews))
                } else {
                    context.getString(R.string.summary_stat_not_available)
                }
            }

            is NewsStatisticsMetric.Mentions -> {
                context.getString(R.string.summary_stat_mentions_count, count)
            }

            is NewsStatisticsMetric.Factuality -> {
                String.format(java.util.Locale.getDefault(), "%.2f", score)
            }
        }
    }
}





