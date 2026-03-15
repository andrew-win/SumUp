package com.andrewwin.sumup.domain.usecase

import android.content.Context
import com.andrewwin.sumup.R
import com.andrewwin.sumup.domain.ExtractiveSummarizer
import com.andrewwin.sumup.domain.usecase.ai.FormatExtractiveSummaryUseCase
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.ArticleImportanceScorer
import com.andrewwin.sumup.domain.repository.ArticleRepository
import javax.inject.Inject

class BuildExtractiveSummaryUseCase @Inject constructor(
    private val context: Context,
    private val formatExtractiveSummaryUseCase: FormatExtractiveSummaryUseCase,
    private val importanceScorer: ArticleImportanceScorer,
    private val articleRepository: ArticleRepository
) {
    suspend operator fun invoke(
        articles: List<Article>,
        topCount: Int = 5,
        sentencesPerArticle: Int = 5
    ): String {
        val articlesWithSources = articles.map { article ->
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceType = source?.type ?: SourceType.RSS
            article to sourceType
        }

        val topArticles = articlesWithSources
            .sortedByDescending { (article, sourceType) ->
                importanceScorer.score(article, sourceType, articles)
            }
            .take(topCount)
            .map { it.first }

        val intro = context.getString(R.string.summary_extractive_intro)
        
        val summaryBody = topArticles.mapIndexed { index, article ->
            val sentences = ExtractiveSummarizer.summarize(article.content, sentencesPerArticle)
            
            formatExtractiveSummaryUseCase.formatItem(
                title = article.title,
                sentences = sentences,
                isScheduledReport = true,
                index = index + 1
            )
        }.joinToString("\n\n")

        return "$intro\n\n$summaryBody"
    }
}
