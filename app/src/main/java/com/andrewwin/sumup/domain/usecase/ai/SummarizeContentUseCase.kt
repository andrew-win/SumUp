package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.exception.NoActiveModelException
import com.andrewwin.sumup.domain.ExtractiveSummarizer
import com.andrewwin.sumup.domain.usecase.FormatArticleHeadlineUseCase
import com.andrewwin.sumup.domain.usecase.BuildExtractiveSummaryUseCase
import com.andrewwin.sumup.domain.usecase.ai.FormatExtractiveSummaryUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SummarizeContentUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val articleRepository: ArticleRepository,
    private val formatExtractiveSummaryUseCase: FormatExtractiveSummaryUseCase,
    private val formatArticleHeadlineUseCase: FormatArticleHeadlineUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val buildExtractiveSummaryUseCase: BuildExtractiveSummaryUseCase
) {
    suspend operator fun invoke(article: Article): Result<String> {
        return try {
            val prefs = userPreferencesRepository.preferences.first()
            val fullContent = articleRepository.fetchFullContent(article)
            
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceType = source?.type ?: SourceType.RSS
            val formattedHeadline = formatArticleHeadlineUseCase(article, sourceType)

            if (prefs.aiStrategy == AiStrategy.EXTRACTIVE) {
                val sentences = ExtractiveSummarizer.summarize(fullContent, prefs.extractiveSentencesInFeed)
                val formatted = formatExtractiveSummaryUseCase.formatItem(
                    title = formattedHeadline.displayTitle,
                    sentences = sentences,
                    isScheduledReport = false
                )
                return Result.success(formatted)
            }

            try {
                Result.success(aiRepository.summarize(fullContent))
            } catch (e: NoActiveModelException) {
                // Adaptive Fallback for single article
                val sentences = ExtractiveSummarizer.summarize(fullContent, prefs.extractiveSentencesInFeed)
                val formatted = formatExtractiveSummaryUseCase.formatItem(
                    title = formattedHeadline.displayTitle,
                    sentences = sentences,
                    isScheduledReport = false
                )
                Result.success(formatted)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Для сумаризації списку новин (напр. вручну зі стрічки)
    suspend operator fun invoke(articles: List<Article>): Result<String> {
        return try {
            val prefs = userPreferencesRepository.preferences.first()
            
            if (prefs.aiStrategy == AiStrategy.EXTRACTIVE) {
                val fullContentMap = mutableMapOf<String, String>()
                for (article in articles) {
                    val source = articleRepository.getSourceById(article.sourceId)
                    val sourceType = source?.type ?: SourceType.RSS
                    val formatted = formatArticleHeadlineUseCase(article, sourceType)
                    fullContentMap[formatted.displayTitle] = articleRepository.fetchFullContent(article)
                }
                
                val summary = buildExtractiveSummaryUseCase(
                    headlines = fullContentMap.keys.toList(),
                    contentMap = fullContentMap,
                    topCount = prefs.extractiveNewsInScheduled,
                    sentencesPerArticle = prefs.extractiveSentencesInScheduled
                )
                return Result.success(summary)
            }

            // Cloud/Adaptive strategy for multiple articles
            val contentBuilder = StringBuilder()
            for (article in articles) {
                val source = articleRepository.getSourceById(article.sourceId)
                val sourceType = source?.type ?: SourceType.RSS
                val formatted = formatArticleHeadlineUseCase(article, sourceType)
                if (contentBuilder.isNotEmpty()) contentBuilder.append("\n\n")
                contentBuilder.append("${formatted.displayTitle}: ${article.content.take(1000)}")
            }
            val content = contentBuilder.toString()

            try {
                Result.success(aiRepository.summarize(content))
            } catch (e: NoActiveModelException) {
                // Adaptive Fallback for multiple articles
                val fullContentMap = mutableMapOf<String, String>()
                for (article in articles) {
                    val source = articleRepository.getSourceById(article.sourceId)
                    val sourceType = source?.type ?: SourceType.RSS
                    val formatted = formatArticleHeadlineUseCase(article, sourceType)
                    fullContentMap[formatted.displayTitle] = articleRepository.fetchFullContent(article)
                }
                
                val summary = buildExtractiveSummaryUseCase(
                    headlines = fullContentMap.keys.toList(),
                    contentMap = fullContentMap,
                    topCount = prefs.extractiveNewsInScheduled,
                    sentencesPerArticle = prefs.extractiveSentencesInScheduled
                )
                Result.success(summary)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Для сумаризації довільного тексту (залишаємо як базовий варіант)
    suspend operator fun invoke(content: String): Result<String> {
        return try {
            try {
                Result.success(aiRepository.summarize(content))
            } catch (e: NoActiveModelException) {
                // Adaptive Fallback for custom content (e.g. general text)
                val sentences = ExtractiveSummarizer.summarize(content, 15)
                if (sentences.isEmpty()) return Result.success("")
                
                val result = sentences.joinToString("\n") { "- $it" }
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
