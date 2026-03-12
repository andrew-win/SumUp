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
import com.andrewwin.sumup.domain.repository.SummaryRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.GenerateSummaryUseCase
import com.andrewwin.sumup.domain.usecase.NoArticlesException
import com.andrewwin.sumup.domain.usecase.RefreshArticlesUseCase
import com.andrewwin.sumup.worker.SummaryWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val summaryRepository: SummaryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val workManager: WorkManager,
    private val refreshArticlesUseCase: RefreshArticlesUseCase,
    private val generateSummaryUseCase: GenerateSummaryUseCase
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

    fun generateSummaryNow() {
        viewModelScope.launch {
            _isGenerating.value = true
            runCatching {
                refreshArticlesUseCase()
                val summaryText = generateSummaryUseCase()
                summaryRepository.insertSummary(Summary(content = summaryText))
            }.onFailure { e ->
                val message = when (e) {
                    is NoArticlesException -> return@onFailure
                    else -> e.localizedMessage.orEmpty()
                }
                summaryRepository.insertSummary(Summary(content = message))
            }
            _isGenerating.value = false
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
