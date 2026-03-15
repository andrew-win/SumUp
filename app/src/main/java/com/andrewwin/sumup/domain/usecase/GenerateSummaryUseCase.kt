package com.andrewwin.sumup.domain.usecase

import android.util.Log
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import javax.inject.Inject

interface GenerateSummaryUseCase {
    suspend operator fun invoke(): String
}

class GenerateSummaryUseCaseImpl @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val aiRepository: AiRepository,
    private val formatArticleHeadlineUseCase: FormatArticleHeadlineUseCase,
    private val buildExtractiveSummaryUseCase: BuildExtractiveSummaryUseCase
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
            val content = articlesToSummarize.map { article ->
                val source = articleRepository.getSourceById(article.sourceId)
                val formatted = formatArticleHeadlineUseCase(article, source?.type ?: com.andrewwin.sumup.data.local.entities.SourceType.RSS)
                "${formatted.displayTitle}: ${formatted.displayContent}"
            }.joinToString(separator = "\n\n")
            aiRepository.summarize(content)
        } catch (e: com.andrewwin.sumup.domain.exception.NoActiveModelException) {
            Log.i("SummaryUseCase", "Adaptive Strategy fallback: No active model, using extractive template")
            val articlesWithContent = articlesToSummarize.map { article ->
                article.copy(content = articleRepository.fetchFullContent(article))
            }
            buildExtractiveSummaryUseCase(articlesWithContent)
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
