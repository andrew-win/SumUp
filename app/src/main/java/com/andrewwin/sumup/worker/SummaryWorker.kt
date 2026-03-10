package com.andrewwin.sumup.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.AppDatabase
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.data.remote.AiService
import com.andrewwin.sumup.data.remote.RssParser
import com.andrewwin.sumup.data.remote.TelegramParser
import com.andrewwin.sumup.data.remote.YouTubeParser
import com.andrewwin.sumup.data.remote.datasource.RemoteArticleDataSource
import com.andrewwin.sumup.data.repository.AiRepositoryImpl
import com.andrewwin.sumup.data.repository.ArticleRepositoryImpl
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.ExtractiveSummarizer
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.first

class SummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val tag = "SummaryWorker"
        Log.d(tag, "Запуск воркера за розкладом")

        val db = AppDatabase.getDatabase(applicationContext)
        val articleDao = db.articleDao()
        val summaryDao = db.summaryDao()
        val prefsDao = db.userPreferencesDao()
        val remoteArticleDataSource = RemoteArticleDataSource(OkHttpClient(), RssParser(), TelegramParser(), YouTubeParser())
        val articleRepo = ArticleRepositoryImpl(articleDao, db.sourceDao(), remoteArticleDataSource)
        val aiRepo = AiRepositoryImpl(db.aiModelDao(), prefsDao, AiService(OkHttpClient()))

        try {
            val currentPrefs = prefsDao.getUserPreferences().first()
            if (currentPrefs != null) {
                prefsDao.insertUserPreferences(
                    currentPrefs.copy(lastWorkRunTimestamp = System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Не вдалося оновити мітку часу запуску")
        }

        return try {
            val prefs = prefsDao.getUserPreferences().first()
            val strategy = prefs?.aiStrategy ?: AiStrategy.ADAPTIVE

            Log.d(tag, "Оновлення новин...")
            articleRepo.refreshArticles()

            val articles = articleDao.getEnabledArticlesOnce()
            Log.d(tag, "Отримано статей: ${articles.size}")

            if (articles.isEmpty()) {
                summaryDao.insertSummary(
                    Summary(
                        content = applicationContext.getString(
                            R.string.summary_worker_no_articles_today
                        )
                    )
                )
                return Result.success()
            }

            val summaryText = if (strategy == AiStrategy.EXTRACTIVE) {
                val headlines = articles.map { it.title }
                val centralHeadlines = ExtractiveSummarizer.getCentralHeadlines(headlines, 3)
                val topArticles = articles
                    .filter { it.title in centralHeadlines }
                    .take(3)

                topArticles.joinToString("\n\n") { article ->
                    val summaryLines = ExtractiveSummarizer.summarize(article.content, 2)
                    "${article.title}:\n" +
                        summaryLines.joinToString("\n") { "- $it" }
                }
            } else {
                val content = articles.take(10).joinToString("\n\n") { article ->
                    val truncated =
                        if (article.content.length > 800) {
                            article.content.take(800) + "..."
                        } else {
                            article.content
                        }
                    "${article.title}: $truncated"
                }
                aiRepo.summarize(content)
            }

            if (summaryText.isBlank()) {
                val msg = applicationContext.getString(R.string.summary_worker_empty_response)
                throw IllegalStateException(msg)
            }

            summaryDao.insertSummary(Summary(content = summaryText))
            Log.d(tag, "Зведення успішно створено")
            Result.success()
        } catch (e: Exception) {
            val prefix = applicationContext.getString(R.string.summary_worker_error_prefix)
            val errorMsg = "$prefix: ${e.localizedMessage.orEmpty()}"
            Log.e(tag, errorMsg)
            summaryDao.insertSummary(Summary(content = errorMsg))
            return if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
