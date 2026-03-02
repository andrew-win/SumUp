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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val aiRepository: AiRepository
    private val db = AppDatabase.getDatabase(application)
    private val prefsDao = db.userPreferencesDao()

    init {
        aiRepository = AiRepository(db.aiModelDao())
    }

    val aiConfigs: StateFlow<List<AiModelConfig>> = aiRepository.allConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPreferences: StateFlow<UserPreferences> = prefsDao.getUserPreferences()
        .map { it ?: UserPreferences() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

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
            val prefs = UserPreferences(id = 0, isScheduledSummaryEnabled = enabled, scheduledHour = hour, scheduledMinute = minute)
            prefsDao.insertUserPreferences(prefs)
            
            val workManager = WorkManager.getInstance(getApplication())
            if (enabled) {
                scheduleWorker(hour, minute)
            } else {
                workManager.cancelUniqueWork("scheduled_summary")
                Log.d("SummarySettings", "Заплановане зведення скасовано")
            }
        }
    }

    fun testWorkerNow() {
        val request = OneTimeWorkRequestBuilder<SummaryWorker>()
            .build()
        WorkManager.getInstance(getApplication()).enqueue(request)
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
        if (initialDelay < 5000) { // Якщо менше 5 сек, додаємо 5 сек для надійності
            initialDelay = 5000
        }

        val targetDateStr = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(dueDate.time)
        Log.d("SummarySettings", "Заплановано на $targetDateStr (затримка ${initialDelay / 1000} сек)")
        
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
