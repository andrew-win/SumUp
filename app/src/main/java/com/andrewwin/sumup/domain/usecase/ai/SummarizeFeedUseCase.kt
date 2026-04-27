package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SummarizeFeedUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val articleRepository: ArticleRepository,
    private val shrinkTextForAdaptiveStrategyUseCase: ShrinkTextForAdaptiveStrategyUseCase,
    private val sendCloudAiRequestUseCase: SendCloudAiRequestUseCase,
    private val parseAiJsonResponseUseCase: ParseAiJsonResponseUseCase
) {
    suspend operator fun invoke(articles: List<Article>): Result<SummaryResult> = runCatching {
        if (articles.isEmpty()) return@runCatching SummaryResult.Digest(emptyList())

        val prefs = userPreferencesRepository.preferences.first()
        val strategy = prefs.aiStrategy

        // 1. Local Strategy
        if (strategy == AiStrategy.LOCAL) {
            val topArticles = articles
                .sortedByDescending { it.importanceScore }
                .take(7)

            val items = topArticles.map { article ->
                val source = articleRepository.getSourceById(article.sourceId)
                val sourceName = source?.name?.trim()?.ifBlank { "Джерело" } ?: "Джерело"
                val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
                SummaryItem(
                    text = article.title,
                    sources = listOf(SummarySourceRef(sourceName, sourceUrl))
                )
            }

            return@runCatching SummaryResult.Digest(
                themes = listOf(
                    DigestTheme(
                        title = "📰📌🧭 Головні новини",
                        items = items
                    )
                )
            )
        }

        // 2. Cloud or Adaptive Strategy
        val maxTotalChars = prefs.aiMaxCharsTotal.coerceAtLeast(1000)
        var remainingTotal = maxTotalChars
        val cloudInput = buildString {
            for (article in articles) {
                if (remainingTotal <= 0) break
                val source = articleRepository.getSourceById(article.sourceId)
                val sourceName = source?.name?.trim()?.ifBlank { "Джерело" } ?: "Джерело"
                val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
                
                val fullContent = articleRepository.fetchFullContent(article)
                val contentToProcess = fullContent.ifBlank { article.content }
                
                val textForCloud = if (strategy == AiStrategy.ADAPTIVE) {
                    shrinkTextForAdaptiveStrategyUseCase(contentToProcess, prefs)
                } else {
                    contentToProcess.take(prefs.aiMaxCharsPerFeedArticle.coerceAtLeast(200))
                }

                val block = buildString {
                    append("source_id: ${article.id}\n")
                    append("source_name: $sourceName\n")
                    append("source_url: $sourceUrl\n")
                    append("title: ${article.title}\n")
                    append("content: $textForCloud\n\n")
                }

                if (block.length <= remainingTotal) {
                    append(block)
                    remainingTotal -= block.length
                } else {
                    append(block.take(remainingTotal))
                    remainingTotal = 0
                }
            }
        }

        val prompt = AiPromptBuilder.buildFeedDigestPrompt(prefs.summaryLanguage)

        // 3. Send Request
        val jsonResponse = sendCloudAiRequestUseCase(prompt, cloudInput)

        // 4. Parse JSON
        parseAiJsonResponseUseCase.parseFeed(jsonResponse, cloudInput)
    }
}
