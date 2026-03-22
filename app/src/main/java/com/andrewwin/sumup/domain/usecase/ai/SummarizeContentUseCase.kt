package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.ExtractiveSummarizer
import com.andrewwin.sumup.domain.usecase.FormatArticleHeadlineUseCase
import com.andrewwin.sumup.domain.usecase.BuildExtractiveSummaryUseCase
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

            if (prefs.aiStrategy == AiStrategy.LOCAL) {
                val sentences = ExtractiveSummarizer.summarize(fullContent, prefs.extractiveSentencesInFeed)
                val formatted = formatExtractiveSummaryUseCase.formatItem(
                    title = formattedHeadline.displayTitle,
                    sentences = sentences,
                    isScheduledReport = false
                )
                return Result.success(formatted)
            }

            // Cloud/Adaptive strategies are handled inside aiRepository.summarize with fallback to local
            Result.success(
                aiRepository.summarize(
                    content = fullContent,
                    extractiveSentenceCount = prefs.extractiveSentencesInFeed
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend operator fun invoke(articles: List<Article>): Result<String> {
        return try {
            val prefs = userPreferencesRepository.preferences.first()
            
            if (prefs.aiStrategy == AiStrategy.LOCAL) {
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

            val contentBuilder = StringBuilder()
            val perArticleLimit = prefs.aiMaxCharsPerFeedArticle.coerceAtLeast(200)
            for (article in articles) {
                val source = articleRepository.getSourceById(article.sourceId)
                val sourceType = source?.type ?: SourceType.RSS
                val formatted = formatArticleHeadlineUseCase(article, sourceType)
                if (contentBuilder.isNotEmpty()) contentBuilder.append("\n\n")
                
                val fullContent = articleRepository.fetchFullContent(article)
                val rawContent = if (prefs.aiStrategy == AiStrategy.ADAPTIVE && prefs.isAdaptiveExtractivePreprocessingEnabled) {
                    ExtractiveSummarizer.summarize(fullContent, prefs.extractiveSentencesInFeed).joinToString(" ")
                } else {
                    fullContent
                }
                contentBuilder.append("${formatted.displayTitle}: ${rawContent.take(perArticleLimit)}")
            }
            
            Result.success(
                aiRepository.summarize(
                    content = contentBuilder.toString(),
                    extractiveSentenceCount = prefs.extractiveSentencesInFeed
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend operator fun invoke(content: String): Result<String> {
        return try {
            val prefs = userPreferencesRepository.preferences.first()
            Result.success(
                aiRepository.summarize(
                    content = content,
                    extractiveSentenceCount = prefs.extractiveSentencesInFeed
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
