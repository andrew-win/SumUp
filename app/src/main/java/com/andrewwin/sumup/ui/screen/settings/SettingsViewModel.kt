package com.andrewwin.sumup.ui.screen.settings

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.remote.firebase.auth.FirebaseSettingsAuthService
import com.andrewwin.sumup.data.remote.firebase.sync.SettingsSyncPreferencesStore
import com.andrewwin.sumup.data.remote.firebase.sync.SettingsSyncService
import com.andrewwin.sumup.domain.ai.model.AiModelConfig
import com.andrewwin.sumup.domain.ai.model.AiModelType
import com.andrewwin.sumup.domain.ai.model.AiProvider
import com.andrewwin.sumup.domain.ai.model.normalizedStableKey
import com.andrewwin.sumup.domain.article.deduplication.DedupRuntimeCoordinator
import com.andrewwin.sumup.domain.ai.repository.AiModelConfigRepository
import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import com.andrewwin.sumup.domain.feed.dedup.FeedDeduplicationProcessor
import com.andrewwin.sumup.domain.summary.repository.SummaryRepository
import com.andrewwin.sumup.domain.settings.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.settings.model.AiStrategy
import com.andrewwin.sumup.domain.settings.model.AppLanguage
import com.andrewwin.sumup.domain.settings.model.AppThemeMode
import com.andrewwin.sumup.domain.settings.model.DeduplicationStrategy
import com.andrewwin.sumup.domain.settings.model.ScheduledSummaryTime
import com.andrewwin.sumup.domain.settings.model.SummaryLanguage
import com.andrewwin.sumup.domain.settings.model.UserSettings
import com.andrewwin.sumup.domain.settings.model.normalizedScheduledSummaryTimes
import com.andrewwin.sumup.domain.sync.model.BackupSelection
import com.andrewwin.sumup.domain.sync.model.SyncConflictStrategy
import com.andrewwin.sumup.domain.sync.model.SyncOverwritePriority
import com.andrewwin.sumup.domain.sync.model.UserDataSyncState
import com.andrewwin.sumup.domain.summary.usecase.CreateScheduleSummaryUseCase
import com.andrewwin.sumup.domain.sync.usecase.ExportBackupUseCase
import com.andrewwin.sumup.domain.sync.usecase.ImportBackupUseCase
import com.andrewwin.sumup.domain.sync.usecase.SyncUserDataUseCase
import com.andrewwin.sumup.ui.screen.settings.model.AuthUiState
import com.andrewwin.sumup.ui.screen.settings.model.TransferState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val aiModelConfigRepository: AiModelConfigRepository,
    private val articleRepository: ArticleRepository,
    private val feedDeduplicationProcessor: FeedDeduplicationProcessor,
    private val summaryRepository: SummaryRepository,
    private val createScheduleSummaryUseCase: CreateScheduleSummaryUseCase,
    private val dedupRuntimeCoordinator: DedupRuntimeCoordinator,
    private val authService: FirebaseSettingsAuthService,
    private val syncPreferencesStore: SettingsSyncPreferencesStore,
    private val syncService: SettingsSyncService,
    private val syncUserDataUseCase: SyncUserDataUseCase,
    private val exportBackupUseCase: ExportBackupUseCase,
    private val importBackupUseCase: ImportBackupUseCase
) : AndroidViewModel(application) {
    val summaryConfigs: StateFlow<List<AiModelConfig>> = aiModelConfigRepository.getConfigsByType(AiModelType.SUMMARY)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val embeddingConfigs: StateFlow<List<AiModelConfig>> = aiModelConfigRepository.getConfigsByType(AiModelType.EMBEDDING)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPreferences: StateFlow<UserSettings> = userPreferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()
    private val _authUiState = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()
    private val _isCloudSyncEnabled = MutableStateFlow(false)
    val isCloudSyncEnabled: StateFlow<Boolean> = _isCloudSyncEnabled.asStateFlow()
    private val _syncIntervalHours = MutableStateFlow(UserDataSyncState.DEFAULT_SYNC_INTERVAL_HOURS)
    val syncIntervalHours: StateFlow<Int> = _syncIntervalHours.asStateFlow()
    private val _syncSelection = MutableStateFlow(BackupSelection())
    val syncSelection: StateFlow<BackupSelection> = _syncSelection.asStateFlow()
    private val _syncStrategy = MutableStateFlow(SyncConflictStrategy.MERGE)
    val syncStrategy: StateFlow<SyncConflictStrategy> = _syncStrategy.asStateFlow()
    private val _syncOverwritePriority = MutableStateFlow(SyncOverwritePriority.LOCAL)
    val syncOverwritePriority: StateFlow<SyncOverwritePriority> = _syncOverwritePriority.asStateFlow()
    private val _importStrategy = MutableStateFlow(SyncConflictStrategy.MERGE)
    val importStrategy: StateFlow<SyncConflictStrategy> = _importStrategy.asStateFlow()
    private val _lastSyncAt = MutableStateFlow(0L)
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()
    private val _exportSelection = MutableStateFlow(BackupSelection())
    val exportSelection: StateFlow<BackupSelection> = _exportSelection.asStateFlow()
    private val _importSelection = MutableStateFlow(BackupSelection())
    val importSelection: StateFlow<BackupSelection> = _importSelection.asStateFlow()
    private val _hasSyncPassphrase = MutableStateFlow(false)
    val hasSyncPassphrase: StateFlow<Boolean> = _hasSyncPassphrase.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                aiModelConfigRepository.migrateLegacyApiKeys()
            }.onFailure { error ->
                Log.e("SettingsViewModel", "Failed to migrate legacy AI configs", error)
            }
        }
        reloadSyncState()
        refreshAuthState()
    }

    private fun refreshAuthState() {
        val session = authService.currentSession()
        _authUiState.value = AuthUiState(
            isSignedIn = session.isSignedIn,
            displayName = session.displayName,
            email = session.email
        )
    }

    private fun reloadSyncState() {
        val state = syncPreferencesStore.loadState()
        _isCloudSyncEnabled.value = state.isCloudSyncEnabled
        _syncIntervalHours.value = state.syncIntervalHours
        _syncSelection.value = state.syncSelection
        _exportSelection.value = state.exportSelection
        _importSelection.value = state.importSelection
        _syncStrategy.value = state.syncStrategy
        _syncOverwritePriority.value = state.syncOverwritePriority
        _importStrategy.value = state.importStrategy
        _lastSyncAt.value = state.lastSyncAt
        _hasSyncPassphrase.value = state.hasSyncPassphrase
    }

    fun updateAiStrategy(strategy: AiStrategy) {
        viewModelScope.launch { updatePreferences { it.copy(aiStrategy = strategy) } }
    }

    fun updateDeduplicationEnabled(enabled: Boolean) {
        updateFeedPreferences { it.copy(isDeduplicationEnabled = enabled) }
    }

    fun updateDeduplicationStrategy(strategy: DeduplicationStrategy) {
        viewModelScope.launch {
            val current = userPreferencesRepository.preferences.first()
            if (current.deduplicationStrategy == strategy) return@launch

            val updated = current.copy(deduplicationStrategy = strategy)
            dedupRuntimeCoordinator.invalidateAfterEmbeddingsClear()
            articleRepository.clearEmbeddings()
            articleRepository.clearSimilarities()
            Log.d("FeedDedupDebug", "embedding_runs_invalidated reason=strategy_changed")
            userPreferencesRepository.updatePreferences(updated)
            rebuildFeedSimilaritiesForThresholdChange(updated)
            articleRepository.requestFeedRefresh()
        }
    }

    private fun updateThreshold(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            val current = userPreferencesRepository.preferences.first()
            val updated = transform(current)
            if (updated == current) return@launch

            rebuildFeedSimilaritiesForThresholdChange(updated)
            userPreferencesRepository.updatePreferences(updated)
            articleRepository.requestFeedRefresh()
        }
    }

    fun updateLocalDeduplicationThreshold(threshold: Float) =
        updateThreshold { it.copy(localDeduplicationThreshold = threshold) }

    fun updateCloudDeduplicationThreshold(threshold: Float) =
        updateThreshold { it.copy(cloudDeduplicationThreshold = threshold) }

    fun updateMinMentions(min: Int) {
        updateFeedPreferences { it.copy(minMentions = min) }
    }

    fun updateHideSingleNewsEnabled(enabled: Boolean) {
        updateFeedPreferences { it.copy(isHideSingleNewsEnabled = enabled) }
    }

    fun loadModels(provider: AiProvider, apiKey: String, type: AiModelType) {
        viewModelScope.launch {
            _isLoadingModels.value = true
            runCatching { _availableModels.value = aiModelConfigRepository.fetchAvailableModels(provider, apiKey, type) }
                .onFailure { _availableModels.value = emptyList() }
            _isLoadingModels.value = false
        }
    }

    fun addAiConfig(config: AiModelConfig) {
        viewModelScope.launch { persistAiConfigWithUseNow(config, isNew = true) }
    }

    fun updateAiConfig(config: AiModelConfig) {
        viewModelScope.launch { persistAiConfigWithUseNow(config, isNew = false) }
    }

    fun deleteAiConfig(config: AiModelConfig) {
        viewModelScope.launch { aiModelConfigRepository.deleteConfig(config) }
    }

    fun toggleAiConfig(config: AiModelConfig, isEnabled: Boolean) {
        viewModelScope.launch {
            val updated = config.copy(isEnabled = isEnabled)
            persistAiConfigWithUseNow(updated, isNew = false)
        }
    }

    fun moveAiConfigUp(config: AiModelConfig) {
        viewModelScope.launch { aiModelConfigRepository.moveConfig(config, -1) }
    }

    fun moveAiConfigDown(config: AiModelConfig) {
        viewModelScope.launch { aiModelConfigRepository.moveConfig(config, 1) }
    }

    private suspend fun persistAiConfigWithUseNow(config: AiModelConfig, isNew: Boolean) {
        val normalizedApiKey = normalizeApiKey(config.apiKey)
        val normalizedConfigName = normalizeAiConfigName(config.name)
        val normalizedStableKey = config.copy(apiKey = normalizedApiKey).normalizedStableKey()
        val existingDuplicate = aiModelConfigRepository.allConfigs.first()
            .firstOrNull { it.id != config.id && it.normalizedStableKey() == normalizedStableKey }
        if (existingDuplicate != null) {
            _transferState.value = TransferState.Error(
                getApplication<Application>().getString(com.andrewwin.sumup.R.string.validation_api_key_exists)
            )
            return
        }
        val existingNameDuplicate = aiModelConfigRepository.allConfigs.first()
            .firstOrNull {
                it.id != config.id &&
                    it.type == config.type &&
                    normalizedConfigName.isNotBlank() &&
                    normalizeAiConfigName(it.name) == normalizedConfigName
            }
        if (existingNameDuplicate != null) {
            _transferState.value = TransferState.Error(
                getApplication<Application>().getString(com.andrewwin.sumup.R.string.validation_ai_config_name_exists)
            )
            return
        }
        val normalizedConfig = config.copy(
            name = config.name.trim(),
            apiKey = normalizedApiKey
        )
        if (isNew) aiModelConfigRepository.addConfig(normalizedConfig) else aiModelConfigRepository.updateConfig(normalizedConfig)
    }

    fun updateScheduledSummary(enabled: Boolean, times: List<ScheduledSummaryTime>) {
        viewModelScope.launch {
            createScheduleSummaryUseCase(enabled, times)
        }
    }

    fun addScheduledSummaryTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            val prefs = userPreferences.first()
            val times = (prefs.scheduledSummaryTimeList + ScheduledSummaryTime(hour, minute))
                .normalizedScheduledSummaryTimes()
            createScheduleSummaryUseCase(true, times)
        }
    }

    fun updateScheduledSummaryTime(index: Int, hour: Int, minute: Int) {
        viewModelScope.launch {
            val prefs = userPreferences.first()
            val times = prefs.scheduledSummaryTimeList.toMutableList()
            if (index !in times.indices) return@launch
            times[index] = ScheduledSummaryTime(hour, minute)
            createScheduleSummaryUseCase(true, times.normalizedScheduledSummaryTimes())
        }
    }

    fun removeScheduledSummaryTime(index: Int) {
        viewModelScope.launch {
            val prefs = userPreferences.first()
            val times = prefs.scheduledSummaryTimeList.toMutableList()
            if (index !in times.indices || times.size <= 1) return@launch
            times.removeAt(index)
            createScheduleSummaryUseCase(prefs.isScheduledSummaryEnabled, times.normalizedScheduledSummaryTimes())
        }
    }

    fun updateScheduledSummaryPushEnabled(enabled: Boolean) {
        viewModelScope.launch { updatePreferences { it.copy(isScheduledSummaryPushEnabled = enabled) } }
    }

    fun updateImportanceFilterEnabled(enabled: Boolean) {
        updateFeedPreferences { it.copy(isImportanceFilterEnabled = enabled) }
    }

    fun updateAdaptiveExtractiveOnlyBelowChars(chars: Int) {
        viewModelScope.launch { updatePreferences { it.copy(adaptiveExtractiveOnlyBelowChars = chars) } }
    }

    fun updateAdaptiveExtractiveHighCompressionAboveChars(chars: Int) {
        viewModelScope.launch { updatePreferences { it.copy(adaptiveExtractiveHighCompressionAboveChars = chars) } }
    }

    fun updateAdaptiveExtractiveCompressionPercentFirst(percent: Int) {
        viewModelScope.launch { updatePreferences { it.copy(adaptiveExtractiveCompressionPercentFirst = percent) } }
    }

    fun updateAdaptiveExtractiveCompressionPercentMedium(percent: Int) {
        viewModelScope.launch { updatePreferences { it.copy(adaptiveExtractiveCompressionPercentMedium = percent) } }
    }

    fun updateAdaptiveExtractiveCompressionPercentHigh(percent: Int) {
        viewModelScope.launch { updatePreferences { it.copy(adaptiveExtractiveCompressionPercentHigh = percent) } }
    }

    fun updateSummaryPrompt(prompt: String) {
        viewModelScope.launch { updatePreferences { it.copy(summaryPrompt = prompt) } }
    }

    fun updateCustomSummaryPromptEnabled(enabled: Boolean) {
        viewModelScope.launch { updatePreferences { it.copy(isCustomSummaryPromptEnabled = enabled) } }
    }

    fun updateFeedMediaEnabled(enabled: Boolean) {
        updateFeedPreferences { it.copy(isFeedMediaEnabled = enabled) }
    }

    fun updateFeedDescriptionEnabled(enabled: Boolean) {
        updateFeedPreferences { it.copy(isFeedDescriptionEnabled = enabled) }
    }

    fun updateFeedSummaryUseFullTextEnabled(enabled: Boolean) {
        updateFeedPreferences { it.copy(isFeedSummaryUseFullTextEnabled = enabled) }
    }

    fun updateRecommendationsEnabled(enabled: Boolean) {
        viewModelScope.launch { updatePreferences { it.copy(isRecommendationsEnabled = enabled) } }
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

    fun updateSummaryItemsPerNewsInFeed(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(summaryItemsPerNewsInFeed = count) } }
    }

    fun updateSummaryItemsPerNewsInScheduled(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(summaryItemsPerNewsInScheduled = count) } }
    }

    fun updateSummaryNewsInFeedCloud(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(summaryNewsInFeedCloud = count) } }
    }

    fun updateSummaryNewsInScheduledCloud(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(summaryNewsInScheduledCloud = count) } }
    }

    fun updateShowInfographicNewsCount(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(showInfographicNewsCount = count) } }
    }

    fun updateAiMaxCharsSingleArticle(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(aiMaxCharsSingleArticle = count) } }
    }

    fun updateAiMaxCharsNewsCluster(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(aiMaxCharsNewsCluster = count) } }
    }

    fun updateAiMaxCharsSingleFeedArticle(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(aiMaxCharsSingleFeedArticle = count) } }
    }

    fun updateAiMaxCharsFeedCluster(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(aiMaxCharsFeedCluster = count) } }
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
            dedupRuntimeCoordinator.invalidateAfterEmbeddingsClear()
            articleRepository.clearEmbeddings()
            Log.d("FeedDedupDebug", "embeddings_cleared reason=settings_action")
            Log.d("FeedDedupDebug", "dedup_restart_skipped reason=settings_clear")
        }
    }

    fun clearScheduledSummaries() {
        viewModelScope.launch {
            summaryRepository.deleteAllSummaries()
        }
    }

    fun updateArticleAutoCleanupHours(hours: Int) {
        viewModelScope.launch {
            updatePreferences {
                it.copy(
                    articleAutoCleanupHours = hours.coerceIn(
                        UserSettings.MIN_ARTICLE_AUTO_CLEANUP_HOURS,
                        UserSettings.MAX_ARTICLE_AUTO_CLEANUP_HOURS
                    )
                )
            }
        }
    }

    fun updateSummaryLanguage(language: SummaryLanguage) {
        viewModelScope.launch { updatePreferences { it.copy(summaryLanguage = language) } }
    }

    fun resetSettingsToDefaults() {
        viewModelScope.launch {
            val defaults = UserSettings()
            userPreferencesRepository.updatePreferences(defaults)
            val languageTag = when (defaults.appLanguage) {
                AppLanguage.UK -> "uk"
                AppLanguage.EN -> "en"
            }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
            createScheduleSummaryUseCase(
                defaults.isScheduledSummaryEnabled,
                defaults.scheduledSummaryTimeList
            )
        }
    }

    fun signInWithEmail(email: String, password: String, register: Boolean) {
        viewModelScope.launch {
            _transferState.value = TransferState.Working
            runCatching {
                authService.signInWithEmail(email, password, register)
                refreshAuthState()
            }.onSuccess {
                _transferState.value = TransferState.Success(
                    getApplication<Application>().getString(R.string.settings_sign_in_success)
                )
            }.onFailure { e ->
                _transferState.value = TransferState.Error(
                    e.localizedMessage ?: getApplication<Application>().getString(R.string.settings_sign_in_error)
                )
            }
        }
    }

    fun signInWithGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            _transferState.value = TransferState.Working
            runCatching {
                authService.signInWithGoogleIdToken(idToken)
                refreshAuthState()
            }.onSuccess {
                _transferState.value = TransferState.Success(
                    getApplication<Application>().getString(R.string.settings_google_sign_in_success)
                )
            }.onFailure { e ->
                _transferState.value = TransferState.Error(
                    e.localizedMessage ?: getApplication<Application>().getString(R.string.settings_google_sign_in_error)
                )
            }
        }
    }

    fun signOut() {
        authService.signOut()
        refreshAuthState()
    }

    fun setCloudSyncEnabled(enabled: Boolean, selection: BackupSelection) {
        val effectiveSelection = sanitizeBackupSelection(selection)
        syncPreferencesStore.persistSyncSelection(effectiveSelection)
        syncPreferencesStore.updateCloudSyncEnabled(enabled)
        _isCloudSyncEnabled.value = enabled
        _syncSelection.value = effectiveSelection
        if (enabled) {
            syncPreferencesStore.scheduleCloudSyncWorker(_syncIntervalHours.value)
            syncNow(effectiveSelection)
        }
    }

    fun updateSyncSelection(selection: BackupSelection) {
        _syncSelection.value = sanitizeBackupSelection(selection)
        syncPreferencesStore.persistSyncSelection(_syncSelection.value)
    }

    fun updateExportSelection(selection: BackupSelection) {
        _exportSelection.value = sanitizeBackupSelection(selection)
        syncPreferencesStore.persistExportSelection(_exportSelection.value)
    }

    fun updateImportSelection(selection: BackupSelection) {
        _importSelection.value = sanitizeBackupSelection(selection)
        syncPreferencesStore.persistImportSelection(_importSelection.value)
    }

    fun saveSyncPassphrase(passphrase: String) {
        runCatching {
            syncService.saveSyncPassphrase(passphrase)
        }.onSuccess { message ->
            reloadSyncState()
            _transferState.value = TransferState.Success(message)
        }.onFailure { error ->
            _transferState.value = TransferState.Error(error.message.orEmpty())
        }
    }

    fun clearSyncPassphrase() {
        _transferState.value = TransferState.Success(syncService.clearSyncPassphrase())
        reloadSyncState()
    }

    fun isSyncPassphraseMatchingCurrent(passphrase: String): Boolean {
        return syncService.isSyncPassphraseMatchingCurrent(passphrase)
    }

    fun updateSyncIntervalHours(hours: Int) {
        _syncIntervalHours.value = hours
        syncPreferencesStore.updateSyncIntervalHours(hours)
        if (_isCloudSyncEnabled.value) {
            syncPreferencesStore.scheduleCloudSyncWorker(hours)
        }
    }

    fun updateSyncStrategy(strategy: SyncConflictStrategy) {
        _syncStrategy.value = strategy
        syncPreferencesStore.updateSyncStrategy(strategy)
    }

    fun updateSyncOverwritePriority(priority: SyncOverwritePriority) {
        _syncOverwritePriority.value = priority
        syncPreferencesStore.updateSyncOverwritePriority(priority)
    }

    fun updateImportStrategy(strategy: SyncConflictStrategy) {
        _importStrategy.value = strategy
        syncPreferencesStore.updateImportStrategy(strategy)
    }

    fun syncNow(selection: BackupSelection) {
        viewModelScope.launch {
            _transferState.value = TransferState.Working
            runCatching {
                syncUserDataUseCase(selection, currentSyncState())
            }.onSuccess {
                reloadSyncState()
                _transferState.value = TransferState.Success(it)
            }.onFailure { e ->
                _transferState.value = TransferState.Error(syncService.syncErrorMessage(e))
            }
        }
    }

    fun resetTransferState() {
        _transferState.value = TransferState.Idle
    }

    fun exportSettingsAndSources(uri: Uri, selection: BackupSelection) {
        viewModelScope.launch {
            _transferState.value = TransferState.Working
            runCatching {
                exportBackupUseCase(uri, currentSyncState().copy(exportSelection = selection))
            }.onSuccess {
                _transferState.value = TransferState.Success(it)
            }.onFailure { e ->
                val message = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: e.message?.takeIf { it.isNotBlank() }
                    ?: getApplication<Application>().getString(R.string.settings_export_error)
                _transferState.value = TransferState.Error(message)
            }
        }
    }

    fun importSettingsAndSources(uri: Uri, merge: Boolean, selection: BackupSelection) {
        viewModelScope.launch {
            _transferState.value = TransferState.Working
            runCatching {
                importBackupUseCase(uri, merge, currentSyncState().copy(importSelection = selection))
            }.onSuccess {
                reloadSyncState()
                _transferState.value = TransferState.Success(it)
            }.onFailure { e ->
                val message = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: e.message?.takeIf { it.isNotBlank() }
                    ?: getApplication<Application>().getString(R.string.settings_import_error)
                _transferState.value = TransferState.Error(message)
            }
        }
    }

    private suspend fun updatePreferences(transform: (UserSettings) -> UserSettings) {
        val current = userPreferencesRepository.preferences.first()
        userPreferencesRepository.updatePreferences(transform(current))
    }

    private suspend fun rebuildFeedSimilaritiesForThresholdChange(updated: UserSettings) {
        if (!updated.isDeduplicationEnabled) return

        if (articleRepository.getEnabledArticlesOnce().isEmpty()) return

        feedDeduplicationProcessor.rebuildSimilarities(updated).getOrThrow()
    }

    private fun updateFeedPreferences(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            val current = userPreferencesRepository.preferences.first()
            userPreferencesRepository.updatePreferences(transform(current))
        }
    }

    private fun sanitizeBackupSelection(selection: BackupSelection): BackupSelection {
        return if (selection.includeApiKeys && !_hasSyncPassphrase.value) {
            _transferState.value = TransferState.Error(
                getApplication<Application>().getString(R.string.settings_sync_passphrase_required)
            )
            selection.copy(includeApiKeys = false)
        } else {
            selection
        }
    }

    private fun currentSyncState() = UserDataSyncState(
        isCloudSyncEnabled = _isCloudSyncEnabled.value,
        syncIntervalHours = _syncIntervalHours.value,
        syncSelection = _syncSelection.value,
        exportSelection = _exportSelection.value,
        importSelection = _importSelection.value,
        syncStrategy = _syncStrategy.value,
        syncOverwritePriority = _syncOverwritePriority.value,
        importStrategy = _importStrategy.value,
        lastSyncAt = _lastSyncAt.value,
        hasSyncPassphrase = _hasSyncPassphrase.value
    )

    private fun normalizeAiConfigName(name: String): String {
        return name.trim().lowercase()
    }

    private fun normalizeApiKey(apiKey: String): String {
        return apiKey.trim()
    }
}
