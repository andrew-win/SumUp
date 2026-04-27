package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.common.GetExtractiveSummaryUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SummarizeSingleArticleUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val articleRepository: ArticleRepository,
    private val getExtractiveSummaryUseCase: GetExtractiveSummaryUseCase,
    private val shrinkTextForAdaptiveStrategyUseCase: ShrinkTextForAdaptiveStrategyUseCase,
    private val sendCloudAiRequestUseCase: SendCloudAiRequestUseCase,
    private val parseAiJsonResponseUseCase: ParseAiJsonResponseUseCase
) {
    suspend operator fun invoke(article: Article): Result<SummaryResult> = runCatching {
        val source = articleRepository.getSourceById(article.sourceId)
        val sourceName = source?.name?.trim()?.ifBlank { "Джерело" } ?: "Джерело"
        val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
        
        val fullContent = articleRepository.fetchFullContent(article)
        val contentToProcess = fullContent.ifBlank { article.content }

        summarizeInternal(
            articleId = article.id,
            title = article.title,
            content = contentToProcess,
            sourceName = sourceName,
            sourceUrl = sourceUrl
        ).getOrThrow()
    }

    suspend operator fun invoke(title: String, content: String): Result<SummaryResult> = runCatching {
        summarizeInternal(
            articleId = -1,
            title = title,
            content = content,
            sourceName = "Текст",
            sourceUrl = ""
        ).getOrThrow()
    }

    private suspend fun summarizeInternal(
        articleId: Long,
        title: String,
        content: String,
        sourceName: String,
        sourceUrl: String
    ): Result<SummaryResult> = runCatching {
        val prefs = userPreferencesRepository.preferences.first()
        val strategy = prefs.aiStrategy
        val sourceRef = SummarySourceRef(sourceName, sourceUrl)

        // 1. Local Strategy OR Adaptive with < 1000 chars
        if (strategy == AiStrategy.LOCAL || (strategy == AiStrategy.ADAPTIVE && content.length < 1000)) {
            val sentences = getExtractiveSummaryUseCase(content, 5)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(5)
            
            val points = sentences.map { SummaryItem(text = it, sources = listOf(sourceRef)) }
            return@runCatching SummaryResult.Single(
                title = title,
                points = points,
                sources = listOf(sourceRef)
            )
        }

        // 2. Adaptive (Shrink text)
        val textForCloud = if (strategy == AiStrategy.ADAPTIVE) {
            shrinkTextForAdaptiveStrategyUseCase(content, prefs)
        } else {
            content.take(prefs.aiMaxCharsPerArticle.coerceAtLeast(1000))
        }

        // 3. Build Prompt & Cloud Input
        val prompt = AiPromptBuilder.buildSingleArticlePrompt(prefs.summaryLanguage)
        val cloudInput = buildString {
            append("source_id: $articleId\n")
            append("source_name: $sourceName\n")
            append("source_url: $sourceUrl\n")
            append("title: $title\n")
            append("content: $textForCloud")
        }

        // 4. Send Request
        val jsonResponse = sendCloudAiRequestUseCase(prompt, cloudInput)

        // 5. Parse JSON
        val parsedResult = parseAiJsonResponseUseCase.parseSingle(jsonResponse, cloudInput)
        
        // Ensure sources fallback
        if (parsedResult.sources.isEmpty()) {
            return@runCatching parsedResult.copy(
                points = parsedResult.points.map { if (it.sources.isEmpty()) it.copy(sources = listOf(sourceRef)) else it },
                sources = listOf(sourceRef)
            )
        }
        
        parsedResult
    }
}
