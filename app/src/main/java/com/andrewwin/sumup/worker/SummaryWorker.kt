package com.andrewwin.sumup.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andrewwin.sumup.data.local.AppDatabase
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.data.repository.AiRepository
import com.andrewwin.sumup.data.repository.ArticleRepository
import kotlinx.coroutines.flow.first

class SummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val tag = "SummaryWorker"
        Log.d(tag, "Запуск воркера за розкладом")
        
        val db = AppDatabase.getDatabase(applicationContext)
        val articleDao = db.articleDao()
        val summaryDao = db.summaryDao()
        val prefsDao = db.userPreferencesDao()
        val articleRepo = ArticleRepository(articleDao, db.sourceDao())
        val aiRepo = AiRepository(db.aiModelDao())

        // Оновлюємо мітку часу останнього запуску для діагностики
        try {
            val currentPrefs = prefsDao.getUserPreferences().first()
            if (currentPrefs != null) {
                prefsDao.insertUserPreferences(currentPrefs.copy(lastWorkRunTimestamp = System.currentTimeMillis()))
            }
        } catch (e: Exception) {
            Log.e(tag, "Не вдалося оновити мітку часу запуску")
        }

        return try {
            val aiConfig = db.aiModelDao().getActiveConfig()
            if (aiConfig == null) {
                summaryDao.insertSummary(Summary(content = "Помилка: Немає активної ШІ моделі."))
                return Result.failure()
            }

            Log.d(tag, "Оновлення новин...")
            articleRepo.refreshArticles()
            
            val articles = articleDao.getEnabledArticlesOnce()
            Log.d(tag, "Отримано статей: ${articles.size}")
            
            if (articles.isEmpty()) {
                summaryDao.insertSummary(Summary(content = "Немає нових статей для зведення за сьогодні."))
                return Result.success()
            }

            val content = articles.take(10).joinToString("\n\n") { article ->
                val truncated = if (article.content.length > 800) article.content.take(800) + "..." else article.content
                "${article.title}: $truncated"
            }
            
            Log.d(tag, "Запит до ШІ...")
            val summaryText = aiRepo.summarize(content)
            
            if (summaryText.isBlank()) {
                throw Exception("ШІ повернув порожню відповідь")
            }

            summaryDao.insertSummary(Summary(content = summaryText))
            Log.d(tag, "Зведення успішно створено")
            Result.success()
        } catch (e: Exception) {
            val errorMsg = "Помилка воркера: ${e.localizedMessage}"
            Log.e(tag, errorMsg)
            summaryDao.insertSummary(Summary(content = errorMsg))
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
