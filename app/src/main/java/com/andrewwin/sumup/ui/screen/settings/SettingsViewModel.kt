package com.andrewwin.sumup.ui.screen.settings

import android.app.Application
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
import kotlinx.coroutines.tasks.await
import com.andrewwin.sumup.domain.usecase.settings.ScheduleSummaryUseCase
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.AppLanguage
import com.andrewwin.sumup.data.local.entities.AppThemeMode
import com.andrewwin.sumup.data.local.entities.SummaryLanguage
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.ImportedSource
import com.andrewwin.sumup.domain.repository.ImportedSourceGroup
import com.andrewwin.sumup.domain.repository.SourceRepository
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
    val includeSettingsNoApi: Boolean = true,
    val includeApiKeys: Boolean = true
)

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
    private val manageModelUseCase: ManageModelUseCase,
    private val scheduleSummaryUseCase: ScheduleSummaryUseCase,
    private val updateSummaryPromptUseCase: UpdateSummaryPromptUseCase,
    private val updateCustomSummaryPromptEnabledUseCase: UpdateCustomSummaryPromptEnabledUseCase
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
    private val _backupSelection = MutableStateFlow(BackupSelection())
    val backupSelection: StateFlow<BackupSelection> = _backupSelection.asStateFlow()

    init {
        checkModelExists()
        _isCloudSyncEnabled.value = syncPrefs.getBoolean(KEY_SYNC_ENABLED, false)
        _syncIntervalHours.value = syncPrefs.getInt(KEY_SYNC_INTERVAL_HOURS, DEFAULT_SYNC_INTERVAL_HOURS)
        _backupSelection.value = BackupSelection(
            includeSources = syncPrefs.getBoolean(KEY_INCLUDE_SOURCES, true),
            includeSubscriptions = syncPrefs.getBoolean(KEY_INCLUDE_SUBSCRIPTIONS, true),
            includeSettingsNoApi = syncPrefs.getBoolean(KEY_INCLUDE_SETTINGS_NO_API, true),
            includeApiKeys = syncPrefs.getBoolean(KEY_INCLUDE_API_KEYS, true)
        )
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
            scheduleSummaryUseCase(enabled, hour, minute)
        }
    }

    fun updateScheduledSummaryPushEnabled(enabled: Boolean) {
        viewModelScope.launch { updatePreferences { it.copy(isScheduledSummaryPushEnabled = enabled) } }
    }

    fun updateImportanceFilterEnabled(enabled: Boolean) {
        viewModelScope.launch { updatePreferences { it.copy(isImportanceFilterEnabled = enabled) } }
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
        viewModelScope.launch { updatePreferences { it.copy(isFeedMediaEnabled = enabled) } }
    }

    fun updateFeedDescriptionEnabled(enabled: Boolean) {
        viewModelScope.launch { updatePreferences { it.copy(isFeedDescriptionEnabled = enabled) } }
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
        persistBackupSelection(selection)
        syncPrefs.edit()
            .putBoolean(KEY_SYNC_ENABLED, enabled)
            .apply()
        if (enabled) {
            scheduleCloudSyncWorker(_syncIntervalHours.value)
            syncNow(selection)
        } else {
            WorkManager.getInstance(getApplication()).cancelUniqueWork(CLOUD_SYNC_WORK_NAME)
        }
    }

    fun updateBackupSelection(selection: BackupSelection) {
        persistBackupSelection(selection)
    }

    fun updateSyncIntervalHours(hours: Int) {
        _syncIntervalHours.value = hours
        syncPrefs.edit().putInt(KEY_SYNC_INTERVAL_HOURS, hours).apply()
        if (_isCloudSyncEnabled.value) {
            scheduleCloudSyncWorker(hours)
        }
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
                val docRef = firestore.collection(CLOUD_COLLECTION).document(uid)
                val remote = docRef.get().await()
                val remoteUpdatedAt = remote.getLong("updatedAt") ?: 0L
                val lastSyncAt = syncPrefs.getLong(KEY_LAST_SYNC_AT, 0L)
                if (remote.exists() && remoteUpdatedAt > lastSyncAt) {
                    val remoteBackup = remote.getString("backup").orEmpty()
                    if (remoteBackup.isNotBlank()) {
                        applyBackupJson(JSONObject(remoteBackup), merge = true, selection = selection)
                    }
                }
                val localBackup = buildBackupJson(selection)
                val now = System.currentTimeMillis()
                docRef.set(
                    mapOf(
                        "backup" to localBackup.toString(),
                        "updatedAt" to now
                    )
                ).await()
                syncPrefs.edit().putLong(KEY_LAST_SYNC_AT, now).apply()
            }.onSuccess {
                _transferState.value = TransferState.Success("Синхронізацію завершено")
            }.onFailure { e ->
                _transferState.value = TransferState.Error(e.localizedMessage ?: "Помилка синхронізації")
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

    private fun persistBackupSelection(selection: BackupSelection) {
        _backupSelection.value = selection
        syncPrefs.edit()
            .putBoolean(KEY_INCLUDE_SOURCES, selection.includeSources)
            .putBoolean(KEY_INCLUDE_SUBSCRIPTIONS, selection.includeSubscriptions)
            .putBoolean(KEY_INCLUDE_SETTINGS_NO_API, selection.includeSettingsNoApi)
            .putBoolean(KEY_INCLUDE_API_KEYS, selection.includeApiKeys)
            .apply()
    }

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

    private suspend fun buildBackupJson(selection: BackupSelection): JSONObject {
        val prefs = if (selection.includeSettingsNoApi) {
            userPreferencesRepository.preferences.first()
        } else {
            null
        }
        val aiConfigs = if (selection.includeApiKeys) aiRepository.allConfigs.first() else emptyList()
        val groups = if (selection.includeSources) sourceRepository.getGroupsWithSourcesSnapshot() else emptyList()
        val savedThemes = if (selection.includeSubscriptions) {
            subscriptionsPrefs.getStringSet(KEY_SAVED_THEMES, emptySet()).orEmpty()
        } else {
            emptySet()
        }
        val lastRecommendationAt = if (selection.includeSubscriptions) {
            subscriptionsPrefs.getLong(KEY_LAST_RECOMMENDATION_AT, 0L)
        } else {
            0L
        }

        return JSONObject().apply {
            put("schemaVersion", 1)
            put("exportedAt", System.currentTimeMillis())
            put("selection", JSONObject().apply {
                put("sources", selection.includeSources)
                put("subscriptions", selection.includeSubscriptions)
                put("settingsNoApi", selection.includeSettingsNoApi)
                put("apiKeys", selection.includeApiKeys)
            })
            if (prefs != null) put("userPreferences", prefs.toBackupJson())
            if (selection.includeApiKeys) {
                put("aiConfigs", JSONArray().apply { aiConfigs.forEach { put(it.toBackupJson()) } })
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
        }
    }

    private suspend fun applyBackupJson(root: JSONObject, merge: Boolean, selection: BackupSelection) {
        val importedPrefs = root.optJSONObject("userPreferences")?.toUserPreferencesFromBackup()
        val importedConfigs = root.optJSONArray("aiConfigs").toAiConfigsFromBackup()
        val importedGroups = root.optJSONArray("groups").toImportedGroupsFromBackup()
        val importedSubscriptions = root.optJSONObject("subscriptions")

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
    }

    private suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        val current = userPreferencesRepository.preferences.first()
        userPreferencesRepository.updatePreferences(transform(current))
    }

    companion object {
        private const val CLOUD_COLLECTION = "user_sync_backups"
        private const val CLOUD_SYNC_WORK_NAME = "cloud_sync_periodic"
        private const val SYNC_PREFS = "sync_prefs"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_SYNC_INTERVAL_HOURS = "sync_interval_hours"
        private const val KEY_INCLUDE_SOURCES = "sync_include_sources"
        private const val KEY_INCLUDE_SUBSCRIPTIONS = "sync_include_subscriptions"
        private const val KEY_INCLUDE_SETTINGS_NO_API = "sync_include_settings_no_api"
        private const val KEY_INCLUDE_API_KEYS = "sync_include_api_keys"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val SUBSCRIPTIONS_PREFS = "suggested_themes_prefs"
        private const val KEY_SAVED_THEMES = "savedThemes"
        private const val KEY_LAST_RECOMMENDATION_AT = "lastRecommendationAt"
        private const val DEFAULT_SYNC_INTERVAL_HOURS = 24
    }
}







