package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.Article
import javax.inject.Inject

class SummarizeContentUseCase @Inject constructor(
    private val summarizationEngineUseCase: SummarizationEngineUseCase
) {
    suspend operator fun invoke(article: Article): Result<String> =
        summarizationEngineUseCase.summarizeSingleArticle(article)

    suspend operator fun invoke(article: Article, duplicates: List<Article>): Result<String> =
        summarizationEngineUseCase.summarizeSingleArticle(article, duplicates)

    suspend operator fun invoke(articles: List<Article>): Result<String> =
        summarizationEngineUseCase.summarizeArticles(
            articles = articles,
            context = SummaryContext.Feed(articleCount = articles.size)
        )

    suspend operator fun invoke(content: String): Result<String> =
        summarizationEngineUseCase.summarizeRawContent(content)
}









