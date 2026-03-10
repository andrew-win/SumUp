package com.andrewwin.sumup.ui.screens.summary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.andrewwin.sumup.data.local.AppDatabase
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.data.repository.AiRepository
import com.andrewwin.sumup.data.repository.ArticleRepository
import com.andrewwin.sumup.worker.SummaryWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SummaryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val summaryDao = db.summaryDao()
    private val prefsDao = db.userPreferencesDao()
    private val aiRepo = AiRepository(db.aiModelDao(), prefsDao)
    private val articleRepo = ArticleRepository(db.articleDao(), db.sourceDao())
    private val workManager = WorkManager.getInstance(application)

    val summaries: StateFlow<List<Summary>> = summaryDao.getAllSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPreferences: StateFlow<UserPreferences> = prefsDao.getUserPreferences()
        .map { it ?: UserPreferences() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val workInfo: StateFlow<List<WorkInfo>> = workManager.getWorkInfosForUniqueWorkFlow("scheduled_summary")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    fun generateSummaryNow() {
        viewModelScope.launch {
            _isGenerating.value = true
            try {
                articleRepo.refreshArticles()
                val articles = db.articleDao().getEnabledArticlesOnce()
                if (articles.isEmpty()) {
                    summaryDao.insertSummary(Summary(content = "Немає нових статей для сумаризації."))
                } else {
                    val content = articles.take(10).joinToString("\n\n") { "${it.title}: ${it.content}" }
                    val summaryText = aiRepo.summarize(content)
                    summaryDao.insertSummary(Summary(content = summaryText))
                }
            } catch (e: Exception) {
                summaryDao.insertSummary(Summary(content = "Помилка: ${e.localizedMessage}"))
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun testWorkerNow() {
        val request = OneTimeWorkRequestBuilder<SummaryWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueue(request)
    }
}
