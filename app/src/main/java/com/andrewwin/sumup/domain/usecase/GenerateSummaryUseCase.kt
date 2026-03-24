package com.andrewwin.sumup.domain.usecase

import android.util.Log
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

interface GenerateSummaryUseCase {
    suspend operator fun invoke(): String
}

class GenerateSummaryUseCaseImpl @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val aiRepository: AiRepository,
    private val formatArticleHeadlineUseCase: FormatArticleHeadlineUseCase,
    private val buildExtractiveSummaryUseCase: BuildExtractiveSummaryUseCase,
    private val userPreferencesRepository: com.andrewwin.sumup.domain.repository.UserPreferencesRepository
) : GenerateSummaryUseCase {

    override suspend fun invoke(): String {
        Log.d("SummaryUseCase", "Starting generate summary use case")
        val articles = articleRepository.getEnabledArticlesOnce()
        Log.d("SummaryUseCase", "Found ${articles.size} enabled articles")

        if (articles.isEmpty()) {
            Log.w("SummaryUseCase", "No articles available for summary")
            throw NoArticlesException()
        }

        val prefs = userPreferencesRepository.preferences.first()
        val cloudTopCount = prefs.summaryNewsInScheduledCloud.coerceAtLeast(1)
        val extractiveTopCount = prefs.summaryNewsInScheduledExtractive.coerceAtLeast(1)
        val articlesToSummarize = when (prefs.aiStrategy) {
            AiStrategy.LOCAL -> articles.take(extractiveTopCount)
            AiStrategy.CLOUD, AiStrategy.ADAPTIVE -> articles.take(cloudTopCount)
        }
        Log.d("SummaryUseCase", "Taking top ${articlesToSummarize.size} articles")

        if (prefs.aiStrategy == AiStrategy.LOCAL) {
            val fullContentMap = mutableMapOf<String, String>()
            for (article in articlesToSummarize) {
                val source = articleRepository.getSourceById(article.sourceId)
                val formatted = formatArticleHeadlineUseCase(
                    article,
                    source?.type ?: com.andrewwin.sumup.data.local.entities.SourceType.RSS
                )
                fullContentMap[formatted.displayTitle] = article.content
            }
            return buildExtractiveSummaryUseCase(
                headlines = fullContentMap.keys.toList(),
                contentMap = fullContentMap,
                topCount = extractiveTopCount,
                sentencesPerArticle = prefs.summaryItemsPerNewsInScheduled
            )
        }
        
        return try {
            val perArticleLimit = prefs.aiMaxCharsPerArticle.coerceAtLeast(200)
            val contentBuilder = StringBuilder()
            for (article in articlesToSummarize) {
                val source = articleRepository.getSourceById(article.sourceId)
                val formatted = formatArticleHeadlineUseCase(article, source?.type ?: com.andrewwin.sumup.data.local.entities.SourceType.RSS)
                if (contentBuilder.isNotEmpty()) contentBuilder.append("\n\n")
                contentBuilder.append("${formatted.displayTitle}: ${formatted.displayContent.take(perArticleLimit)}")
            }
            aiRepository.summarize(
                content = contentBuilder.toString(),
                pointsPerNews = prefs.summaryItemsPerNewsInScheduled
            )
        } catch (e: com.andrewwin.sumup.domain.exception.NoActiveModelException) {
            Log.i("SummaryUseCase", "Adaptive Strategy fallback: No active model, using extractive template")
            val fullContentMap = mutableMapOf<String, String>()
            for (article in articlesToSummarize) {
                val source = articleRepository.getSourceById(article.sourceId)
                val formatted = formatArticleHeadlineUseCase(article, source?.type ?: com.andrewwin.sumup.data.local.entities.SourceType.RSS)
                fullContentMap[formatted.displayTitle] = article.content
            }
            buildExtractiveSummaryUseCase(
                headlines = fullContentMap.keys.toList(),
                contentMap = fullContentMap,
                topCount = extractiveTopCount,
                sentencesPerArticle = prefs.summaryItemsPerNewsInScheduled
            )
        } catch (e: Exception) {
            Log.e("SummaryUseCase", "Failed to generate summary", e)
            throw e
        }
    }
}

class NoArticlesException : Exception()
