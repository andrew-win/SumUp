package com.andrewwin.sumup.ui.screen.settings

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import com.andrewwin.sumup.domain.usecase.settings.ScheduleSummaryUseCase
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.AppLanguage
import com.andrewwin.sumup.data.local.entities.AppThemeMode
import com.andrewwin.sumup.data.local.entities.DeduplicationStrategy
import com.andrewwin.sumup.data.local.entities.SummaryLanguage
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.data.security.SecretEncryptionManager
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.ImportedSource
import com.andrewwin.sumup.domain.repository.ImportedSourceGroup
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.SummaryRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.support.DebugTrace
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import com.andrewwin.sumup.worker.CloudSyncWorker

sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState
    data class Downloading(val progress: Int) : ModelDownloadState
    data object Loading : ModelDownloadState
    data class Error(val message: String) : ModelDownloadState
    data object Ready : ModelDownloadState
}

sealed interface TransferState {
    data object Idle : TransferState
    data object Working : TransferState
    data class Success(val message: String) : TransferState
    data class Error(val message: String) : TransferState
}

data class BackupSelection(
    val includeSources: Boolean = true,
    val includeSubscriptions: Boolean = true,
    val includeSavedArticles: Boolean = true,
    val includeSettingsNoApi: Boolean = true,
    val includeApiKeys: Boolean = false
)

enum class SyncConflictStrategy {
    OVERWRITE,
    MERGE
}

enum class SyncOverwritePriority {
    LOCAL,
    CLOUD
}

data class AuthUiState(
    val isSignedIn: Boolean = false,
    val displayName: String = "",
    val email: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val aiRepository: AiRepository,
    private val articleRepository: ArticleRepository,
    private val sourceRepository: SourceRepository,
    private val summaryRepository: SummaryRepository,
    private val manageModelUseCase: ManageModelUseCase,
    private val scheduleSummaryUseCase: ScheduleSummaryUseCase,
    private val updateSummaryPromptUseCase: UpdateSummaryPromptUseCase,
    private val updateCustomSummaryPromptEnabledUseCase: UpdateCustomSummaryPromptEnabledUseCase,
    private val secretEncryptionManager: SecretEncryptionManager
) : AndroidViewModel(application) {
    private val firebaseAuth by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { FirebaseAuth.getInstance() }
    private val firestore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { FirebaseFirestore.getInstance() }
    private val subscriptionsPrefs by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        getApplication<Application>().getSharedPreferences(SUBSCRIPTIONS_PREFS, 0)
    }
    private val syncPrefs by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        getApplication<Application>().getSharedPreferences(SYNC_PREFS, 0)
    }

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

    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()
    private val _authUiState = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()
    private val _isCloudSyncEnabled = MutableStateFlow(false)
    val isCloudSyncEnabled: StateFlow<Boolean> = _isCloudSyncEnabled.asStateFlow()
    private val _syncIntervalHours = MutableStateFlow(DEFAULT_SYNC_INTERVAL_HOURS)
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
        checkModelExists()
        viewModelScope.launch {
            aiRepository.migrateLegacyApiKeys()
        }
        _isCloudSyncEnabled.value = syncPrefs.getBoolean(KEY_SYNC_ENABLED, false)
        _syncIntervalHours.value = syncPrefs.getInt(KEY_SYNC_INTERVAL_HOURS, DEFAULT_SYNC_INTERVAL_HOURS)
        _syncStrategy.value = parseSyncConflictStrategy(
            syncPrefs.getString(KEY_SYNC_STRATEGY, SyncConflictStrategy.MERGE.name)
        )
        _syncOverwritePriority.value = parseSyncOverwritePriority(
            syncPrefs.getString(KEY_SYNC_OVERWRITE_PRIORITY, SyncOverwritePriority.LOCAL.name)
        )
        _importStrategy.value = parseSyncConflictStrategy(
            syncPrefs.getString(KEY_IMPORT_STRATEGY, SyncConflictStrategy.MERGE.name)
        )
        _lastSyncAt.value = syncPrefs.getLong(KEY_LAST_SYNC_AT, 0L)
        _syncSelection.value = readSelectionFromPrefs(KEY_PREFIX_SYNC)
        _exportSelection.value = readSelectionFromPrefs(KEY_PREFIX_EXPORT)
        _importSelection.value = readSelectionFromPrefs(KEY_PREFIX_IMPORT)
        refreshSyncPassphraseState()
        if (!_hasSyncPassphrase.value) {
            _syncSelection.value = _syncSelection.value.copy(includeApiKeys = false)
            _exportSelection.value = _exportSelection.value.copy(includeApiKeys = false)
            _importSelection.value = _importSelection.value.copy(includeApiKeys = false)
            persistSelection(KEY_PREFIX_SYNC, _syncSelection.value)
            persistSelection(KEY_PREFIX_EXPORT, _exportSelection.value)
            persistSelection(KEY_PREFIX_IMPORT, _importSelection.value)
        }
        refreshAuthState()
        if (_isCloudSyncEnabled.value) {
            scheduleCloudSyncWorker(_syncIntervalHours.value)
        }
    }

    private fun refreshAuthState() {
        val user = firebaseAuth.currentUser
        _authUiState.value = if (user != null) {
            AuthUiState(
                isSignedIn = true,
                displayName = user.displayName.orEmpty(),
                email = user.email.orEmpty()
            )
        } else {
            AuthUiState()
        }
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
        updateFeedPreferences { it.copy(isDeduplicationEnabled = enabled) }
    }

    fun updateDeduplicationStrategy(strategy: DeduplicationStrategy) {
        viewModelScope.launch {
            val current = userPreferencesRepository.preferences.first()
            if (current.deduplicationStrategy != strategy) {
                articleRepository.clearEmbeddings()
                articleRepository.clearSimilarities()
            }
            userPreferencesRepository.updatePreferences(current.copy(deduplicationStrategy = strategy))
            articleRepository.refreshArticles()
        }
    }

    private fun updateThreshold(transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch {
            articleRepository.clearSimilarities()
            updatePreferences(transform)
            articleRepository.refreshArticles()
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
            runCatching { _availableModels.value = aiRepository.fetchAvailableModels(provider, apiKey, type) }
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
        viewModelScope.launch { aiRepository.deleteConfig(config) }
    }

    fun toggleAiConfig(config: AiModelConfig, isEnabled: Boolean) {
        viewModelScope.launch {
            val updated = if (!isEnabled) config.copy(isEnabled = false, isUseNow = false)
            else config.copy(isEnabled = true)
            persistAiConfigWithUseNow(updated, isNew = false)
        }
    }

    private suspend fun persistAiConfigWithUseNow(config: AiModelConfig, isNew: Boolean) {
        if (config.isUseNow) {
            val configs = aiRepository.getConfigsByType(config.type).first()
            configs
                .filter { it.id != config.id && it.isUseNow }
                .forEach { existing ->
                    aiRepository.updateConfig(existing.copy(isUseNow = false))
                }
        }
        if (isNew) aiRepository.addConfig(config) else aiRepository.updateConfig(config)
    }

    fun updateScheduledSummary(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            scheduleSummaryUseCase(enabled, hour, minute)
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

    fun updateAdaptiveExtractiveCompressAboveChars(chars: Int) {
        viewModelScope.launch { updatePreferences { it.copy(adaptiveExtractiveCompressAboveChars = chars) } }
    }

    fun updateAdaptiveExtractiveCompressionPercent(percent: Int) {
        viewModelScope.launch { updatePreferences { it.copy(adaptiveExtractiveCompressionPercent = percent) } }
    }

    fun updateSummaryPrompt(prompt: String) {
        viewModelScope.launch { updateSummaryPromptUseCase(prompt) }
    }

    fun updateCustomSummaryPromptEnabled(enabled: Boolean) {
        viewModelScope.launch { updateCustomSummaryPromptEnabledUseCase(enabled) }
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

    fun updateSummaryNewsInFeedExtractive(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(summaryNewsInFeedExtractive = count) } }
    }

    fun updateSummaryNewsInFeedCloud(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(summaryNewsInFeedCloud = count) } }
    }

    fun updateSummaryNewsInScheduledExtractive(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(summaryNewsInScheduledExtractive = count) } }
    }

    fun updateSummaryNewsInScheduledCloud(count: Int) {
        viewModelScope.launch { updatePreferences { it.copy(summaryNewsInScheduledCloud = count) } }
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

    fun clearScheduledSummaries() {
        viewModelScope.launch {
            summaryRepository.deleteAllSummaries()
        }
    }

    fun updateArticleAutoCleanupDays(days: Int) {
        viewModelScope.launch {
            updatePreferences { it.copy(articleAutoCleanupDays = days.coerceIn(1, 10)) }
        }
    }

    fun updateSummaryLanguage(language: SummaryLanguage) {
        viewModelScope.launch { updatePreferences { it.copy(summaryLanguage = language) } }
    }

    fun resetSettingsToDefaults() {
        viewModelScope.launch {
            val defaults = UserPreferences()
            userPreferencesRepository.updatePreferences(defaults)
            val languageTag = when (defaults.appLanguage) {
                AppLanguage.UK -> "uk"
                AppLanguage.EN -> "en"
            }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
            scheduleSummaryUseCase(
                defaults.isScheduledSummaryEnabled,
                defaults.scheduledHour,
                defaults.scheduledMinute
            )
        }
    }

    fun signInWithEmail(email: String, password: String, register: Boolean) {
        viewModelScope.launch {
            _transferState.value = TransferState.Working
            runCatching {
                if (register) {
                    firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
                } else {
                    firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
                }
                refreshAuthState()
            }.onSuccess {
                _transferState.value = TransferState.Success("Вхід успішний")
            }.onFailure { e ->
                _transferState.value = TransferState.Error(e.localizedMessage ?: "Помилка входу")
            }
        }
    }

    fun signInWithGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            _transferState.value = TransferState.Working
            runCatching {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                firebaseAuth.signInWithCredential(credential).await()
                refreshAuthState()
            }.onSuccess {
                _transferState.value = TransferState.Success("Google-вхід успішний")
            }.onFailure { e ->
                _transferState.value = TransferState.Error(e.localizedMessage ?: "Помилка Google-входу")
            }
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
        refreshAuthState()
    }

    fun setCloudSyncEnabled(enabled: Boolean, selection: BackupSelection) {
        _isCloudSyncEnabled.value = enabled
        val effectiveSelection = sanitizeBackupSelection(selection)
        persistSelection(KEY_PREFIX_SYNC, effectiveSelection)
        syncPrefs.edit()
            .putBoolean(KEY_SYNC_ENABLED, enabled)
            .apply()
        if (enabled) {
            scheduleCloudSyncWorker(_syncIntervalHours.value)
            syncNow(effectiveSelection)
        } else {
            WorkManager.getInstance(getApplication()).cancelUniqueWork(CLOUD_SYNC_WORK_NAME)
        }
    }

    fun updateSyncSelection(selection: BackupSelection) {
        persistSelection(KEY_PREFIX_SYNC, sanitizeBackupSelection(selection))
    }

    fun updateExportSelection(selection: BackupSelection) {
        persistSelection(KEY_PREFIX_EXPORT, sanitizeBackupSelection(selection))
    }

    fun updateImportSelection(selection: BackupSelection) {
        persistSelection(KEY_PREFIX_IMPORT, sanitizeBackupSelection(selection))
    }

    fun saveSyncPassphrase(passphrase: String) {
        val normalized = passphrase.trim()
        if (normalized.length < MIN_SYNC_PASSPHRASE_LENGTH) {
            _transferState.value = TransferState.Error("Пароль синхронізації має містити щонайменше $MIN_SYNC_PASSPHRASE_LENGTH символів")
            return
        }
        secretEncryptionManager.setSyncPassphrase(normalized)
        refreshSyncPassphraseState()
        _transferState.value = TransferState.Success("Пароль синхронізації API-ключів збережено")
    }

    fun clearSyncPassphrase() {
        secretEncryptionManager.clearSyncPassphrase()
        refreshSyncPassphraseState()
        persistSelection(KEY_PREFIX_SYNC, _syncSelection.value.copy(includeApiKeys = false))
        persistSelection(KEY_PREFIX_EXPORT, _exportSelection.value.copy(includeApiKeys = false))
        persistSelection(KEY_PREFIX_IMPORT, _importSelection.value.copy(includeApiKeys = false))
        _transferState.value = TransferState.Success("Синхронізацію API-ключів вимкнено, пароль очищено")
    }

    fun updateSyncIntervalHours(hours: Int) {
        _syncIntervalHours.value = hours
        syncPrefs.edit().putInt(KEY_SYNC_INTERVAL_HOURS, hours).apply()
        if (_isCloudSyncEnabled.value) {
            scheduleCloudSyncWorker(hours)
        }
    }

    fun updateSyncStrategy(strategy: SyncConflictStrategy) {
        _syncStrategy.value = strategy
        syncPrefs.edit().putString(KEY_SYNC_STRATEGY, strategy.name).apply()
    }

    fun updateSyncOverwritePriority(priority: SyncOverwritePriority) {
        _syncOverwritePriority.value = priority
        syncPrefs.edit().putString(KEY_SYNC_OVERWRITE_PRIORITY, priority.name).apply()
    }

    fun updateImportStrategy(strategy: SyncConflictStrategy) {
        _importStrategy.value = strategy
        syncPrefs.edit().putString(KEY_IMPORT_STRATEGY, strategy.name).apply()
    }

    fun syncNow(selection: BackupSelection) {
        viewModelScope.launch {
            val uid = firebaseAuth.currentUser?.uid
            if (uid.isNullOrBlank()) {
                _transferState.value = TransferState.Error("Спочатку увійдіть у акаунт")
                return@launch
            }
            if (!_isCloudSyncEnabled.value) return@launch

            _transferState.value = TransferState.Working
            runCatching {
                DebugTrace.d(
                    "backup_sync",
                    "syncNow strategy=${_syncStrategy.value.name} selection sources=${selection.includeSources} subscriptions=${selection.includeSubscriptions} saved=${selection.includeSavedArticles} settings=${selection.includeSettingsNoApi} apiKeys=${selection.includeApiKeys}"
                )
                val docRef = firestore.collection(CLOUD_COLLECTION).document(uid)
                val remote = docRef.get().await()
                val remoteBackupJson = remote.getString("backup")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::JSONObject)
                val remoteUpdatedAt = remote.getLong("updatedAt") ?: 0L
                val lastSyncAt = syncPrefs.getLong(KEY_LAST_SYNC_AT, 0L)
                val mergeMode = _syncStrategy.value == SyncConflictStrategy.MERGE
                val shouldApplyRemote = shouldApplyRemoteBeforePush(
                    remoteExists = remote.exists(),
                    remoteUpdatedAt = remoteUpdatedAt,
                    lastSyncAt = lastSyncAt,
                    strategy = _syncStrategy.value,
                    overwritePriority = _syncOverwritePriority.value
                )
                if (shouldApplyRemote) {
                    if (remoteBackupJson != null) {
                        applyBackupJson(remoteBackupJson, merge = mergeMode, selection = selection)
                    }
                }
                val localBackup = buildBackupJson(selection, remoteBackupJson)
                val now = System.currentTimeMillis()
                docRef.set(
                    mapOf(
                        "backup" to localBackup.toString(),
                        "updatedAt" to now
                    )
                ).await()
                syncPrefs.edit().putLong(KEY_LAST_SYNC_AT, now).apply()
                _lastSyncAt.value = now
            }.onSuccess {
                _transferState.value = TransferState.Success("Синхронізацію завершено")
            }.onFailure { e ->
                val message = when ((e as? FirebaseFirestoreException)?.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE ->
                        if (hasInternetConnection()) {
                            "Є мережа, але Firebase тимчасово недоступний (UNAVAILABLE). Перевірте дату/час, VPN/Proxy та доступ до Google Firebase."
                        } else {
                            "Немає інтернету. Синхронізація буде доступна після відновлення мережі."
                        }
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "Немає доступу до хмарної синхронізації. Перевірте вхід у акаунт."
                    else -> e.localizedMessage ?: "Помилка синхронізації"
                }
                _transferState.value = TransferState.Error(message)
            }
        }
    }

    fun resetTransferState() {
        _transferState.value = TransferState.Idle
    }

    private fun scheduleCloudSyncWorker(intervalHours: Int) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(intervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork(
            CLOUD_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun persistSelection(prefix: String, selection: BackupSelection) {
        when (prefix) {
            KEY_PREFIX_SYNC -> _syncSelection.value = selection
            KEY_PREFIX_EXPORT -> _exportSelection.value = selection
            KEY_PREFIX_IMPORT -> _importSelection.value = selection
        }
        syncPrefs.edit()
            .putBoolean(prefKey(prefix, KEY_INCLUDE_SOURCES_SUFFIX), selection.includeSources)
            .putBoolean(prefKey(prefix, KEY_INCLUDE_SUBSCRIPTIONS_SUFFIX), selection.includeSubscriptions)
            .putBoolean(prefKey(prefix, KEY_INCLUDE_SAVED_ARTICLES_SUFFIX), selection.includeSavedArticles)
            .putBoolean(prefKey(prefix, KEY_INCLUDE_SETTINGS_NO_API_SUFFIX), selection.includeSettingsNoApi)
            .putBoolean(prefKey(prefix, KEY_INCLUDE_API_KEYS_SUFFIX), selection.includeApiKeys)
            .apply()
    }

    private fun readSelectionFromPrefs(prefix: String): BackupSelection = BackupSelection(
        includeSources = syncPrefs.getBoolean(prefKey(prefix, KEY_INCLUDE_SOURCES_SUFFIX), true),
        includeSubscriptions = syncPrefs.getBoolean(prefKey(prefix, KEY_INCLUDE_SUBSCRIPTIONS_SUFFIX), true),
        includeSavedArticles = syncPrefs.getBoolean(prefKey(prefix, KEY_INCLUDE_SAVED_ARTICLES_SUFFIX), true),
        includeSettingsNoApi = syncPrefs.getBoolean(prefKey(prefix, KEY_INCLUDE_SETTINGS_NO_API_SUFFIX), true),
        includeApiKeys = syncPrefs.getBoolean(prefKey(prefix, KEY_INCLUDE_API_KEYS_SUFFIX), false)
    )

    private fun prefKey(prefix: String, suffix: String): String = "${prefix}_$suffix"

    fun exportSettingsAndSources(uri: Uri, selection: BackupSelection) {
        viewModelScope.launch {
            _transferState.value = TransferState.Working
            runCatching {
                val root = buildBackupJson(selection)

                val resolver = getApplication<Application>().contentResolver
                resolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(root.toString())
                } ?: error("Failed to open output stream")
            }.onSuccess {
                _transferState.value = TransferState.Success("Експорт завершено")
            }.onFailure { e ->
                _transferState.value = TransferState.Error(e.localizedMessage ?: "Не вдалося експортувати")
            }
        }
    }

    fun importSettingsAndSources(uri: Uri, merge: Boolean, selection: BackupSelection) {
        viewModelScope.launch {
            _transferState.value = TransferState.Working
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                val content = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Failed to read import file")
                applyBackupJson(JSONObject(content), merge, selection)
            }.onSuccess {
                _transferState.value = TransferState.Success("Імпорт завершено")
            }.onFailure { e ->
                _transferState.value = TransferState.Error(e.localizedMessage ?: "Не вдалося імпортувати")
            }
        }
    }

    private suspend fun buildBackupJson(selection: BackupSelection, remoteBackupRoot: JSONObject? = null): JSONObject {
        val prefs = if (selection.includeSettingsNoApi) {
            userPreferencesRepository.preferences.first()
        } else {
            null
        }
        val aiConfigs = if (selection.includeApiKeys) aiRepository.allConfigs.first() else emptyList()
        val syncPassphrase = if (selection.includeApiKeys) {
            requireSyncPassphrase()
        } else {
            null
        }
        val groups = if (selection.includeSources) sourceRepository.getGroupsWithSourcesSnapshot() else emptyList()
        val savedThemes = if (selection.includeSubscriptions) {
            subscriptionsPrefs.getStringSet(KEY_SAVED_THEMES, emptySet()).orEmpty()
        } else {
            emptySet()
        }
        val savedArticlesSnapshot = if (selection.includeSavedArticles) {
            articleRepository.getSavedArticlesSnapshot()
        } else {
            emptyList()
        }
        DebugTrace.d(
            "backup_sync",
            "buildBackupJson savedThemes=${savedThemes.size} savedArticles=${savedArticlesSnapshot.size} includeSaved=${selection.includeSavedArticles}"
        )
        val lastRecommendationAt = if (selection.includeSubscriptions) {
            subscriptionsPrefs.getLong(KEY_LAST_RECOMMENDATION_AT, 0L)
        } else {
            0L
        }

        return JSONObject().apply {
            put("schemaVersion", 1)
            put("exportedAt", System.currentTimeMillis())
            put("syncStrategy", _syncStrategy.value.name)
            put("syncOverwritePriority", _syncOverwritePriority.value.name)
            put("importStrategy", _importStrategy.value.name)
            put("selection", JSONObject().apply {
                put("sources", selection.includeSources)
                put("subscriptions", selection.includeSubscriptions)
                put("savedArticles", selection.includeSavedArticles)
                put("settingsNoApi", selection.includeSettingsNoApi)
                put("apiKeys", selection.includeApiKeys)
            })
            if (prefs != null) put("userPreferences", prefs.toBackupJson())
            if (selection.includeApiKeys) {
                put("aiConfigs", JSONArray().apply {
                    aiConfigs.forEach { put(it.toBackupJson(secretEncryptionManager, syncPassphrase!!)) }
                })
            } else if (remoteBackupRoot?.has("aiConfigs") == true) {
                put("aiConfigs", remoteBackupRoot.optJSONArray("aiConfigs"))
            }
            if (selection.includeSources) {
                put("groups", JSONArray().apply {
                    groups.forEach { groupWithSources ->
                        put(JSONObject().apply {
                            put("name", groupWithSources.group.name)
                            put("isEnabled", groupWithSources.group.isEnabled)
                            put("isDeletable", groupWithSources.group.isDeletable)
                            put("sources", JSONArray().apply {
                                groupWithSources.sources.forEach { source -> put(source.toBackupJson()) }
                            })
                        })
                    }
                })
            }
            if (selection.includeSubscriptions) {
                put("subscriptions", JSONObject().apply {
                    put(KEY_SAVED_THEMES, JSONArray(savedThemes.toList()))
                    put(KEY_LAST_RECOMMENDATION_AT, lastRecommendationAt)
                })
            }
            if (selection.includeSavedArticles) {
                put("savedArticles", JSONArray().apply {
                    savedArticlesSnapshot.forEach { put(it.toBackupJson()) }
                })
            }
        }
    }

    private suspend fun applyBackupJson(root: JSONObject, merge: Boolean, selection: BackupSelection) {
        val importedSyncStrategy = parseSyncConflictStrategy(root.optString("syncStrategy", _syncStrategy.value.name))
        updateSyncStrategy(importedSyncStrategy)
        val importedOverwritePriority = parseSyncOverwritePriority(
            root.optString("syncOverwritePriority", _syncOverwritePriority.value.name)
        )
        updateSyncOverwritePriority(importedOverwritePriority)
        val importedImportStrategy = parseSyncConflictStrategy(
            root.optString("importStrategy", _importStrategy.value.name)
        )
        updateImportStrategy(importedImportStrategy)

        val importedPrefs = root.optJSONObject("userPreferences")?.toUserPreferencesFromBackup()
        val importedConfigs = if (selection.includeApiKeys) {
            root.optJSONArray("aiConfigs").toAiConfigsFromBackup(
                secretEncryptionManager = secretEncryptionManager,
                syncPassphrase = requireSyncPassphrase()
            )
        } else {
            emptyList()
        }
        val importedGroups = root.optJSONArray("groups").toImportedGroupsFromBackup()
        val importedSubscriptions = root.optJSONObject("subscriptions")
        val hasSavedArticlesField = root.has("savedArticles")
        val rawSavedArticles = root.optJSONArray("savedArticles")
        val importedSavedArticles = rawSavedArticles.toSavedArticlesFromBackup()
        val importedSavedArticleUrls = rawSavedArticles
            ?.let { arr ->
                buildList {
                    for (index in 0 until arr.length()) {
                        if (arr.optJSONObject(index) != null) continue
                        arr.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }.orEmpty()
        DebugTrace.d(
            "backup_sync",
            "applyBackupJson merge=$merge includeSaved=${selection.includeSavedArticles} hasSavedField=$hasSavedArticlesField importedSavedArticles=${importedSavedArticles.size} importedSavedUrlsLegacy=${importedSavedArticleUrls.size}"
        )

        if (selection.includeSettingsNoApi && importedPrefs != null) {
            userPreferencesRepository.updatePreferences(importedPrefs.copy(id = 0))
            val languageTag = when (importedPrefs.appLanguage) {
                AppLanguage.UK -> "uk"
                AppLanguage.EN -> "en"
            }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
            scheduleSummaryUseCase(
                importedPrefs.isScheduledSummaryEnabled,
                importedPrefs.scheduledHour,
                importedPrefs.scheduledMinute
            )
        }

        if (selection.includeApiKeys) {
            val existingConfigs = aiRepository.allConfigs.first()
            if (!merge) {
                existingConfigs.forEach { aiRepository.deleteConfig(it) }
            }
            val configsAfterClear = if (merge) existingConfigs else emptyList()
            for (imported in importedConfigs) {
                val matched = configsAfterClear.firstOrNull {
                    it.type == imported.type &&
                        it.provider == imported.provider &&
                        it.modelName.equals(imported.modelName, ignoreCase = true) &&
                        it.name.equals(imported.name, ignoreCase = true)
                }
                if (matched == null) {
                    aiRepository.addConfig(imported.copy(id = 0))
                } else {
                    aiRepository.updateConfig(imported.copy(id = matched.id))
                }
            }
        }

        if (selection.includeSources) {
            sourceRepository.importGroupsWithSources(importedGroups, merge)
        }

        if (selection.includeSubscriptions) {
            if (importedSubscriptions != null) {
                val savedThemes = importedSubscriptions.optJSONArray(KEY_SAVED_THEMES)
                    ?.let { arr ->
                        buildSet {
                            for (i in 0 until arr.length()) {
                                arr.optString(i)?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                            }
                        }
                    }
                    .orEmpty()
                subscriptionsPrefs.edit()
                    .putStringSet(KEY_SAVED_THEMES, savedThemes)
                    .putLong(
                        KEY_LAST_RECOMMENDATION_AT,
                        importedSubscriptions.optLong(KEY_LAST_RECOMMENDATION_AT, 0L)
                    )
                    .apply()
            } else if (!merge) {
                subscriptionsPrefs.edit()
                    .remove(KEY_SAVED_THEMES)
                    .remove(KEY_LAST_RECOMMENDATION_AT)
                    .apply()
            }
        }

        if (selection.includeSavedArticles && hasSavedArticlesField) {
            if (importedSavedArticles.isNotEmpty()) {
                if (merge) {
                    articleRepository.mergeSavedArticlesSnapshot(importedSavedArticles)
                } else {
                    articleRepository.replaceSavedArticlesSnapshot(importedSavedArticles)
                }
            } else if (merge) {
                articleRepository.mergeFavoriteArticlesByUrls(importedSavedArticleUrls)
            } else {
                articleRepository.replaceFavoriteArticlesByUrls(importedSavedArticleUrls)
            }
        }
    }

    private suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        val current = userPreferencesRepository.preferences.first()
        userPreferencesRepository.updatePreferences(transform(current))
    }

    private fun updateFeedPreferences(transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch {
            val current = userPreferencesRepository.preferences.first()
            userPreferencesRepository.updatePreferences(transform(current))
            articleRepository.refreshArticles()
        }
    }

    private fun sanitizeBackupSelection(selection: BackupSelection): BackupSelection {
        return if (selection.includeApiKeys && !_hasSyncPassphrase.value) {
            _transferState.value = TransferState.Error("Спочатку задайте пароль синхронізації для API-ключів")
            selection.copy(includeApiKeys = false)
        } else {
            selection
        }
    }

    private fun refreshSyncPassphraseState() {
        _hasSyncPassphrase.value = secretEncryptionManager.hasSyncPassphrase()
    }

    private fun requireSyncPassphrase(): String =
        secretEncryptionManager.getSyncPassphraseOrNull()
            ?: error("Спочатку задайте пароль синхронізації для API-ключів")

    private fun hasInternetConnection(): Boolean {
        val connectivityManager = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val CLOUD_COLLECTION = "user_sync_backups"
        private const val CLOUD_SYNC_WORK_NAME = "cloud_sync_periodic"
        private const val SYNC_PREFS = "sync_prefs"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_SYNC_INTERVAL_HOURS = "sync_interval_hours"
        private const val KEY_SYNC_STRATEGY = "sync_strategy"
        private const val KEY_SYNC_OVERWRITE_PRIORITY = "sync_overwrite_priority"
        private const val KEY_IMPORT_STRATEGY = "import_strategy"
        private const val KEY_PREFIX_SYNC = "sync"
        private const val KEY_PREFIX_EXPORT = "export"
        private const val KEY_PREFIX_IMPORT = "import"
        private const val KEY_INCLUDE_SOURCES_SUFFIX = "include_sources"
        private const val KEY_INCLUDE_SUBSCRIPTIONS_SUFFIX = "include_subscriptions"
        private const val KEY_INCLUDE_SAVED_ARTICLES_SUFFIX = "include_saved_articles"
        private const val KEY_INCLUDE_SETTINGS_NO_API_SUFFIX = "include_settings_no_api"
        private const val KEY_INCLUDE_API_KEYS_SUFFIX = "include_api_keys"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val SUBSCRIPTIONS_PREFS = "suggested_themes_prefs"
        private const val KEY_SAVED_THEMES = "savedThemes"
        private const val KEY_LAST_RECOMMENDATION_AT = "lastRecommendationAt"
        private const val DEFAULT_SYNC_INTERVAL_HOURS = 24
        private const val MIN_SYNC_PASSPHRASE_LENGTH = 8
    }

    private fun parseSyncConflictStrategy(rawValue: String?): SyncConflictStrategy {
        return runCatching { SyncConflictStrategy.valueOf(rawValue.orEmpty()) }
            .getOrDefault(SyncConflictStrategy.MERGE)
    }

    private fun parseSyncOverwritePriority(rawValue: String?): SyncOverwritePriority {
        return runCatching { SyncOverwritePriority.valueOf(rawValue.orEmpty()) }
            .getOrDefault(SyncOverwritePriority.LOCAL)
    }

    private fun shouldApplyRemoteBeforePush(
        remoteExists: Boolean,
        remoteUpdatedAt: Long,
        lastSyncAt: Long,
        strategy: SyncConflictStrategy,
        overwritePriority: SyncOverwritePriority
    ): Boolean {
        if (!remoteExists) return false
        if (strategy == SyncConflictStrategy.MERGE) {
            return remoteUpdatedAt > lastSyncAt
        }
        return when (overwritePriority) {
            SyncOverwritePriority.CLOUD -> true
            SyncOverwritePriority.LOCAL -> false
        }
    }
}







