package com.andrewwin.sumup.ui.screens.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.andrewwin.sumup.data.local.dao.SummaryDao
import com.andrewwin.sumup.data.local.dao.UserPreferencesDao
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.usecase.GenerateSummaryUseCase
import com.andrewwin.sumup.domain.usecase.RefreshArticlesUseCase
import com.andrewwin.sumup.worker.SummaryWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val summaryDao: SummaryDao,
    private val prefsDao: UserPreferencesDao,
    private val workManager: WorkManager,
    private val refreshArticlesUseCase: RefreshArticlesUseCase,
    private val generateSummaryUseCase: GenerateSummaryUseCase
) : ViewModel() {

    val summaries: StateFlow<List<Summary>> = summaryDao.getAllSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPreferences: StateFlow<UserPreferences> = prefsDao.getUserPreferences()
        .map { it ?: UserPreferences() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val workInfo: StateFlow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow("scheduled_summary")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    fun generateSummaryNow() {
        viewModelScope.launch {
            _isGenerating.value = true
            try {
                refreshArticlesUseCase()
                val summaryText = generateSummaryUseCase()
                summaryDao.insertSummary(Summary(content = summaryText))
            } catch (e: Exception) {
                summaryDao.insertSummary(
                    Summary(content = e.localizedMessage.orEmpty())
                )
            } finally {
                _isGenerating.value = false
            }
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
}
