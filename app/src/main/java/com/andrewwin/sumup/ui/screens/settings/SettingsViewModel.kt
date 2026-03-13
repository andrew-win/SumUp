package com.andrewwin.sumup.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCase
import com.andrewwin.sumup.worker.SummaryWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class ModelDownloadState {
    object Idle : ModelDownloadState()
    data class Downloading(val progress: Int) : ModelDownloadState()
    object Loading : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
    object Ready : ModelDownloadState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val aiRepository: AiRepository,
    private val manageModelUseCase: ManageModelUseCase,
    private val workManager: WorkManager
) : AndroidViewModel(application) {

    val aiConfigs: StateFlow<List<AiModelConfig>> = aiRepository.allConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    init {
        checkModelExists()
    }

    private fun checkModelExists() {
        if (manageModelUseCase.isModelExists()) {
            _downloadState.value = ModelDownloadState.Ready
        }
    }

    fun downloadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            _downloadState.value = ModelDownloadState.Downloading(0)
            runCatching {
                manageModelUseCase.downloadModel().collect { progress ->
                    _downloadState.value = ModelDownloadState.Downloading(progress)
                }
                _downloadState.value = ModelDownloadState.Ready
                updatePreferences { it.copy(modelPath = manageModelUseCase.getModelPath()) }
            }.onFailure { e ->
                manageModelUseCase.deleteModel()
                _downloadState.value = ModelDownloadState.Error(e.localizedMessage.orEmpty())
            }
        }
    }

    fun deleteModel() {
        viewModelScope.launch(Dispatchers.IO) {
            manageModelUseCase.deleteModel()
            _downloadState.value = ModelDownloadState.Idle
            updatePreferences { it.copy(modelPath = null, isDeduplicationEnabled = false) }
        }
    }

    fun updateAiStrategy(strategy: AiStrategy) {
        viewModelScope.launch { updatePreferences { it.copy(aiStrategy = strategy) } }
    }

    fun updateDeduplicationEnabled(enabled: Boolean) {
        viewModelScope.launch { updatePreferences { it.copy(isDeduplicationEnabled = enabled) } }
    }

    fun updateDeduplicationThreshold(threshold: Float) {
        viewModelScope.launch { updatePreferences { it.copy(deduplicationThreshold = threshold) } }
    }

    fun updateMinMentions(min: Int) {
        viewModelScope.launch { updatePreferences { it.copy(minMentions = min) } }
    }

    fun loadModels(provider: AiProvider, apiKey: String) {
        viewModelScope.launch {
            _isLoadingModels.value = true
            runCatching { _availableModels.value = aiRepository.fetchAvailableModels(provider, apiKey) }
                .onFailure { _availableModels.value = emptyList() }
            _isLoadingModels.value = false
        }
    }

    fun addAiConfig(config: AiModelConfig) {
        viewModelScope.launch { aiRepository.addConfig(config) }
    }

    fun updateAiConfig(config: AiModelConfig) {
        viewModelScope.launch { aiRepository.updateConfig(config) }
    }

    fun deleteAiConfig(config: AiModelConfig) {
        viewModelScope.launch { aiRepository.deleteConfig(config) }
    }

    fun toggleAiConfig(config: AiModelConfig, isEnabled: Boolean) {
        viewModelScope.launch { aiRepository.updateConfig(config.copy(isEnabled = isEnabled)) }
    }

    fun updateScheduledSummary(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            updatePreferences {
                it.copy(
                    isScheduledSummaryEnabled = enabled,
                    scheduledHour = hour,
                    scheduledMinute = minute
                )
            }
            if (enabled) scheduleWorker(hour, minute) else workManager.cancelUniqueWork(SCHEDULED_SUMMARY_WORK_NAME)
        }
    }

    fun updateImportanceFilterEnabled(enabled: Boolean) {
        viewModelScope.launch { updatePreferences { it.copy(isImportanceFilterEnabled = enabled) } }
    }

    private suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        userPreferencesRepository.updatePreferences(transform(userPreferences.value))
    }

    private fun scheduleWorker(hour: Int, minute: Int) {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(currentDate)) add(Calendar.HOUR_OF_DAY, 24)
        }

        val initialDelay = maxOf(dueDate.timeInMillis - currentDate.timeInMillis, MIN_INITIAL_DELAY_MS)

        val request = PeriodicWorkRequestBuilder<SummaryWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_DELAY_MINUTES, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(SCHEDULED_SUMMARY_WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, request)
    }

    companion object {
        private const val SCHEDULED_SUMMARY_WORK_NAME = "scheduled_summary"
        private const val MIN_INITIAL_DELAY_MS = 5000L
        private const val BACKOFF_DELAY_MINUTES = 15L
    }
}
