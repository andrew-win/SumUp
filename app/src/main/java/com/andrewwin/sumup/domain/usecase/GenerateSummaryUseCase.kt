package com.andrewwin.sumup.domain.usecase

import android.util.Log
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

        val articlesToSummarize = articles.take(MAX_ARTICLES_FOR_SUMMARY)
        Log.d("SummaryUseCase", "Taking top ${articlesToSummarize.size} articles")
        
        return try {
            val perArticleLimit = userPreferencesRepository.preferences.first().aiMaxCharsPerArticle.coerceAtLeast(200)
            val content = articlesToSummarize.map { article ->
                val source = articleRepository.getSourceById(article.sourceId)
                val formatted = formatArticleHeadlineUseCase(article, source?.type ?: com.andrewwin.sumup.data.local.entities.SourceType.RSS)
                "${formatted.displayTitle}: ${formatted.displayContent.take(perArticleLimit)}"
            }.joinToString(separator = "\n\n")
            aiRepository.summarize(
                content = content,
                extractiveSentenceCount = userPreferencesRepository.preferences.first().extractiveSentencesInScheduled
            )
        } catch (e: com.andrewwin.sumup.domain.exception.NoActiveModelException) {
            Log.i("SummaryUseCase", "Adaptive Strategy fallback: No active model, using extractive template")
            val fullContentMap = articlesToSummarize.map { article ->
                val source = articleRepository.getSourceById(article.sourceId)
                val formatted = formatArticleHeadlineUseCase(article, source?.type ?: com.andrewwin.sumup.data.local.entities.SourceType.RSS)
                formatted.displayTitle to article.content
            }.toMap()
            buildExtractiveSummaryUseCase(fullContentMap.keys.toList(), fullContentMap)
        } catch (e: Exception) {
            Log.e("SummaryUseCase", "Failed to generate summary", e)
            throw e
        }
    }

    companion object {
        private const val MAX_ARTICLES_FOR_SUMMARY = 15
    }
}

class NoArticlesException : Exception()
