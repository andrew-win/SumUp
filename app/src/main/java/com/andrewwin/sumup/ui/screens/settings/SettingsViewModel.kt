package com.andrewwin.sumup.ui.screens.settings

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.domain.usecase.settings.ScheduleSummaryUseCase
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.AppLanguage
import com.andrewwin.sumup.data.local.entities.AppThemeMode
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCase
import com.andrewwin.sumup.domain.usecase.settings.UpdateCustomSummaryPromptEnabledUseCase
import com.andrewwin.sumup.domain.usecase.settings.UpdateSummaryPromptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState
    data class Downloading(val progress: Int) : ModelDownloadState
    data object Loading : ModelDownloadState
    data class Error(val message: String) : ModelDownloadState
    data object Ready : ModelDownloadState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val aiRepository: AiRepository,
    private val articleRepository: ArticleRepository,
    private val manageModelUseCase: ManageModelUseCase,
    private val scheduleSummaryUseCase: ScheduleSummaryUseCase,
    private val updateSummaryPromptUseCase: UpdateSummaryPromptUseCase,
    private val updateCustomSummaryPromptEnabledUseCase: UpdateCustomSummaryPromptEnabledUseCase
) : AndroidViewModel(application) {

    val summaryConfigs: StateFlow<List<AiModelConfig>> = aiRepository.getConfigsByType(AiModelType.SUMMARY)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val embeddingConfigs: StateFlow<List<AiModelConfig>> = aiRepository.getConfigsByType(AiModelType.EMBEDDING)
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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

    private fun updateThreshold(transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch {
            articleRepository.clearSimilarities()
            updatePreferences(transform)
        }
    }

    fun updateLocalDeduplicationThreshold(threshold: Float) =
        updateThreshold { it.copy(localDeduplicationThreshold = threshold) }

    fun updateCloudDeduplicationThreshold(threshold: Float) =
        updateThreshold { it.copy(cloudDeduplicationThreshold = threshold) }

    fun updateMinMentions(min: Int) {
        viewModelScope.launch { updatePreferences { it.copy(minMentions = min) } }
    }

    fun updateHideSingleNewsEnabled(enabled: Boolean) {
        viewModelScope.launch { updatePreferences { it.copy(isHideSingleNewsEnabled = enabled) } }
    }

    fun loadModels(provider: AiProvider, apiKey: String, type: AiModelType) {
        viewModelScope.launch {
            _isLoadingModels.value = true
            runCatching { _availableModels.value = aiRepository.fetchAvailableModels(provider, apiKey, type) }
                .onFailure { _availableModels.value = emptyList() }
            _isLoadingModels.value = false
        }
    }

    fun addAiConfig(config: AiModelConfig) {
        viewModelScope.launch { aiRepository.addConfig(config) }
    }

    fun deleteAiConfig(config: AiModelConfig) {
        viewModelScope.launch { aiRepository.deleteConfig(config) }
    }

    fun toggleAiConfig(config: AiModelConfig, isEnabled: Boolean) {
        viewModelScope.launch { aiRepository.updateConfig(config.copy(isEnabled = isEnabled)) }
    }

    fun updateScheduledSummary(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            scheduleSummaryUseCase(enabled, hour, minute)
        }
    }

    fun updateImportanceFilterEnabled(enabled: Boolean) {
        viewModelScope.launch { updatePreferences { it.copy(isImportanceFilterEnabled = enabled) } }
    }

    fun updateAdaptiveExtractivePreprocessingEnabled(enabled: Boolean) {
        viewModelScope.launch { updatePreferences { it.copy(isAdaptiveExtractivePreprocessingEnabled = enabled) } }
    }

    fun updateSummaryPrompt(prompt: String) {
        viewModelScope.launch { updateSummaryPromptUseCase(prompt) }
    }

    fun updateCustomSummaryPromptEnabled(enabled: Boolean) {
        viewModelScope.launch { updateCustomSummaryPromptEnabledUseCase(enabled) }
    }

    fun updateFeedMediaEnabled(enabled: Boolean) {
        viewModelScope.launch { updatePreferences { it.copy(isFeedMediaEnabled = enabled) } }
    }

    fun updateFeedDescriptionEnabled(enabled: Boolean) {
        viewModelScope.launch { updatePreferences { it.copy(isFeedDescriptionEnabled = enabled) } }
    }

    fun updateAppThemeMode(themeMode: AppThemeMode) {
        viewModelScope.launch { updatePreferences { it.copy(appThemeMode = themeMode) } }
    }

    fun updateAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            updatePreferences { it.copy(appLanguage = language) }
            val languageTag = when (language) {
                AppLanguage.UK -> "uk"
                AppLanguage.EN -> "en"
            }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        }
    }

    fun updateExtractiveSentencesInFeed(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(extractiveSentencesInFeed = count) } }
    }

    fun updateExtractiveSentencesInScheduled(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(extractiveSentencesInScheduled = count) } }
    }

    fun updateExtractiveNewsInScheduled(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(extractiveNewsInScheduled = count) } }
    }

    fun updateShowLastSummariesCount(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(showLastSummariesCount = count) } }
    }

    fun updateShowInfographicNewsCount(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(showInfographicNewsCount = count) } }
    }

    fun updateAiMaxCharsPerArticle(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(aiMaxCharsPerArticle = count) } }
    }

    fun updateAiMaxCharsPerFeedArticle(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(aiMaxCharsPerFeedArticle = count) } }
    }

    fun updateAiMaxCharsTotal(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(aiMaxCharsTotal = count) } }
    }

    fun clearAllArticles() {
        viewModelScope.launch {
            articleRepository.clearAllArticles()
        }
    }

    fun clearEmbeddings() {
        viewModelScope.launch {
            articleRepository.clearEmbeddings()
        }
    }

    private suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        val current = userPreferencesRepository.preferences.first()
        userPreferencesRepository.updatePreferences(transform(current))
    }
}
