package com.andrewwin.sumup.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.domain.ExtractiveSummarizer
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.SummaryRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.NoArticlesException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class SummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val articleRepository: ArticleRepository,
    private val aiRepository: AiRepository,
    private val summaryRepository: SummaryRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = userPreferencesRepository.preferences.first()

        userPreferencesRepository.updatePreferences(
            prefs.copy(lastWorkRunTimestamp = System.currentTimeMillis())
        )

        return try {
            articleRepository.refreshArticles()
            val articles = articleRepository.getEnabledArticlesOnce()

            if (articles.isEmpty()) {
                val message = applicationContext.getString(R.string.summary_worker_no_articles_today)
                summaryRepository.insertSummary(Summary(content = message))
                return Result.success()
            }

            val summaryText = when (prefs.aiStrategy) {
                AiStrategy.EXTRACTIVE -> buildExtractiveSummary(articles.map { it.title }, articles.associate { it.title to it.content })
                else -> buildCloudSummary(articles.take(MAX_ARTICLES_FOR_SUMMARIZATION), aiRepository)
            }

            if (summaryText.isBlank()) {
                val message = applicationContext.getString(R.string.summary_worker_empty_response)
                throw IllegalStateException(message)
            }

            summaryRepository.insertSummary(Summary(content = summaryText))
            Result.success()
        } catch (e: NoArticlesException) {
            val message = applicationContext.getString(R.string.summary_worker_no_articles_today)
            summaryRepository.insertSummary(Summary(content = message))
            Result.success()
        } catch (e: Exception) {
            val prefix = applicationContext.getString(R.string.summary_worker_error_prefix)
            summaryRepository.insertSummary(Summary(content = "$prefix: ${e.localizedMessage.orEmpty()}"))
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private fun buildExtractiveSummary(
        headlines: List<String>,
        contentMap: Map<String, String>
    ): String {
        val centralHeadlines = ExtractiveSummarizer.getCentralHeadlines(headlines, EXTRACTIVE_TOP_COUNT)
        return centralHeadlines.joinToString("\n\n") { title ->
            val content = contentMap[title].orEmpty()
            val lines = ExtractiveSummarizer.summarize(content, EXTRACTIVE_SENTENCES_PER_ARTICLE)
            "$title:\n" + lines.joinToString("\n") { "- $it" }
        }
    }

    private suspend fun buildCloudSummary(
        articles: List<com.andrewwin.sumup.data.local.entities.Article>,
        aiRepo: AiRepository
    ): String {
        val content = articles.joinToString("\n\n") { article ->
            val truncated = article.content.take(MAX_ARTICLE_CONTENT_CHARS)
            "${article.title}: $truncated"
        }
        return aiRepo.summarize(content)
    }

    companion object {
        private const val MAX_ARTICLES_FOR_SUMMARIZATION = 10
        private const val MAX_ARTICLE_CONTENT_CHARS = 800
        private const val EXTRACTIVE_TOP_COUNT = 3
        private const val EXTRACTIVE_SENTENCES_PER_ARTICLE = 2
        private const val MAX_RETRY_ATTEMPTS = 2
    }
}
