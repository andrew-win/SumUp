package com.andrewwin.sumup.ui.screens.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.andrewwin.sumup.data.local.AppDatabase
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.data.repository.AiRepository
import com.andrewwin.sumup.worker.SummaryWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class ModelDownloadState {
    object Idle : ModelDownloadState()
    data class Downloading(val progress: Int) : ModelDownloadState()
    object Loading : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
    object Ready : ModelDownloadState()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val aiRepository = AiRepository(db.aiModelDao())
    private val prefsDao = db.userPreferencesDao()
    private val okHttpClient = OkHttpClient()

    private val MODEL_URL = "https://huggingface.co/onnx-community/distiluse-base-multilingual-v2-merged-onnx/resolve/main/combined_tokenizer_embedded_model.onnx?download=true"
    private val MODEL_FILE_NAME = "dedup_model.onnx"

    val aiConfigs: StateFlow<List<AiModelConfig>> = aiRepository.allConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPreferences: StateFlow<UserPreferences> = prefsDao.getUserPreferences()
        .map { it ?: UserPreferences() }
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
        val file = File(getApplication<Application>().filesDir, MODEL_FILE_NAME)
        if (file.exists()) {
            _downloadState.value = ModelDownloadState.Ready
        }
    }

    fun downloadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(getApplication<Application>().filesDir, MODEL_FILE_NAME)
            _downloadState.value = ModelDownloadState.Downloading(0)
            try {
                val request = Request.Builder().url(MODEL_URL).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception(response.code.toString())
                    val body = response.body ?: throw Exception()
                    val totalBytes = body.contentLength()
                    
                    body.byteStream().use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var downloaded = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                downloaded += read
                                if (totalBytes > 0) {
                                    _downloadState.value = ModelDownloadState.Downloading((downloaded * 100 / totalBytes).toInt())
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
                _downloadState.value = ModelDownloadState.Ready
                updateDeduplicationModelPath(file.absolutePath)
            } catch (e: Exception) {
                file.delete()
                _downloadState.value = ModelDownloadState.Error(e.localizedMessage ?: "")
            }
        }
    }

    fun deleteModel() {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(getApplication<Application>().filesDir, MODEL_FILE_NAME)
            if (file.exists()) file.delete()
            _downloadState.value = ModelDownloadState.Idle
            updateDeduplicationModelPath(null)
            updateDeduplicationEnabled(false)
        }
    }

    private suspend fun updateDeduplicationModelPath(path: String?) {
        val current = userPreferences.value
        prefsDao.insertUserPreferences(current.copy(modelPath = path))
    }

    fun updateDeduplicationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = userPreferences.value
            prefsDao.insertUserPreferences(current.copy(isDeduplicationEnabled = enabled))
        }
    }

    fun updateDeduplicationThreshold(threshold: Float) {
        viewModelScope.launch {
            val current = userPreferences.value
            prefsDao.insertUserPreferences(current.copy(deduplicationThreshold = threshold))
        }
    }

    fun updateMinMentions(min: Int) {
        viewModelScope.launch {
            val current = userPreferences.value
            prefsDao.insertUserPreferences(current.copy(minMentions = min))
        }
    }

    fun loadModels(provider: AiProvider, apiKey: String) {
        viewModelScope.launch {
            _isLoadingModels.value = true
            try {
                _availableModels.value = aiRepository.fetchAvailableModels(provider, apiKey)
            } catch (e: Exception) {
                _availableModels.value = emptyList()
            } finally {
                _isLoadingModels.value = false
            }
        }
    }

    fun addAiConfig(config: AiModelConfig) {
        viewModelScope.launch {
            aiRepository.addConfig(config)
        }
    }

    fun updateAiConfig(config: AiModelConfig) {
        viewModelScope.launch {
            aiRepository.updateConfig(config)
        }
    }

    fun deleteAiConfig(config: AiModelConfig) {
        viewModelScope.launch {
            aiRepository.deleteConfig(config)
        }
    }

    fun toggleAiConfig(config: AiModelConfig, isEnabled: Boolean) {
        viewModelScope.launch {
            aiRepository.updateConfig(config.copy(isEnabled = isEnabled))
        }
    }

    fun updateScheduledSummary(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            val current = userPreferences.value
            val prefs = current.copy(isScheduledSummaryEnabled = enabled, scheduledHour = hour, scheduledMinute = minute)
            prefsDao.insertUserPreferences(prefs)
            
            if (enabled) {
                scheduleWorker(hour, minute)
            } else {
                WorkManager.getInstance(getApplication()).cancelUniqueWork("scheduled_summary")
            }
        }
    }

    private fun scheduleWorker(hour: Int, minute: Int) {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(currentDate)) {
                add(Calendar.HOUR_OF_DAY, 24)
            }
        }

        var initialDelay = dueDate.timeInMillis - currentDate.timeInMillis
        if (initialDelay < 5000) initialDelay = 5000

        val request = PeriodicWorkRequestBuilder<SummaryWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork(
            "scheduled_summary",
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }
}
