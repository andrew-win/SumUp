package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.local.entities.SummaryLanguage
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.common.BuildExtractiveSummaryUseCase
import com.andrewwin.sumup.domain.usecase.common.FormatArticleHeadlineUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SummarizationEngineUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val formatArticleHeadlineUseCase: FormatArticleHeadlineUseCase,
    private val buildExtractiveSummaryUseCase: BuildExtractiveSummaryUseCase,
    private val preprocessPolicy: SummaryPreprocessPolicy,
    private val cloudCallPolicy: SummaryCloudCallPolicy,
    private val parsePolicy: SummaryParsePolicy,
    private val renderPolicy: SummaryRenderPolicy,
    private val fallbackPolicy: SummaryFallbackPolicy
) {

    suspend fun summarizeSingleArticle(article: Article): Result<String> = runCatching {
        summarizeSingleArticle(article, emptyList()).getOrThrow()
    }

    suspend fun summarizeSingleArticle(
        representative: Article,
        duplicates: List<Article>
    ): Result<String> = runCatching {
        val prefs = userPreferencesRepository.preferences.first()
        val source = articleRepository.getSourceById(representative.sourceId)
        val sourceType = source?.type ?: SourceType.RSS
        val formatted = formatArticleHeadlineUseCase(representative, sourceType)
        val fullContent = articleRepository.fetchFullContent(representative)
        val hasDuplicates = duplicates.isNotEmpty()
        val context = SummaryContext.SingleArticle(hasClusterDuplicates = hasDuplicates)
        val allArticles = listOf(representative) + duplicates

        if (hasDuplicates) {
            return@runCatching if (prefs.aiStrategy == AiStrategy.LOCAL) {
                renderPolicy.renderLocalCompare(allArticles)
            } else {
                runCatching { buildCloudComparisonSummary(allArticles, context, prefs) }
                    .getOrElse { renderPolicy.renderLocalCompare(allArticles) }
            }
        }

        if (prefs.aiStrategy == AiStrategy.LOCAL) {
            val local = fallbackPolicy.singleArticleFallback(
                title = formatted.displayTitle,
                fullContent = fullContent,
                sentenceLimit = context.extractiveSentencesLimit(prefs)
            )
            return@runCatching renderPolicy.moveSourceToEndForSingleArticle(
                renderPolicy.appendSourceMetadata(local, listOf(representative))
            )
        }

        val contentForAi = when {
            sourceType == SourceType.YOUTUBE && fullContent.isNotBlank() -> fullContent
            formatted.displayContent.isNotBlank() -> formatted.displayContent
            else -> fullContent
        }
        val prep = preprocessPolicy.preprocess(
            rawText = contentForAi,
            prefs = prefs,
            context = context
        )
        prep.finalExtractiveText?.let {
            val local = fallbackPolicy.singleArticleFallback(
                title = formatted.displayTitle,
                fullContent = it,
                sentenceLimit = context.extractiveSentencesLimit(prefs)
            )
            return@runCatching renderPolicy.moveSourceToEndForSingleArticle(
                renderPolicy.appendSourceMetadata(local, listOf(representative))
            )
        }

        val cloudInput = "${formatted.displayTitle}: ${prep.textForCloud}"
        val cloud = try {
            cloudCallPolicy.summarize(content = cloudInput, pointsPerNews = context.pointsPerNews(prefs))
        } catch (_: Exception) {
            fallbackPolicy.singleArticleFallback(
                title = formatted.displayTitle,
                fullContent = fullContent,
                sentenceLimit = context.extractiveSentencesLimit(prefs)
            )
        }
        renderPolicy.moveSourceToEndForSingleArticle(
            renderPolicy.appendSourceMetadata(cloud, listOf(representative))
        )
    }

    suspend fun summarizeArticles(articles: List<Article>, context: SummaryContext): Result<String> = runCatching {
        if (articles.isEmpty()) return@runCatching ""
        val prefs = userPreferencesRepository.preferences.first()

        if (prefs.aiStrategy == AiStrategy.LOCAL) {
            val extractiveArticles = articles.take(context.extractiveNewsLimit(prefs))
            return@runCatching renderPolicy.appendSourceMetadata(
                buildExtractiveSummary(extractiveArticles, context, prefs),
                extractiveArticles
            )
        }

        val cloudArticles = articles.take(context.cloudNewsLimit(prefs))
        val cloudInput = buildCloudInput(cloudArticles, context, prefs)

        val raw = try {
            cloudCallPolicy.summarize(content = cloudInput, pointsPerNews = context.pointsPerNews(prefs))
        } catch (_: Exception) {
            val fallbackArticles = articles.take(context.extractiveNewsLimit(prefs))
            return@runCatching renderPolicy.appendSourceMetadata(
                buildExtractiveSummary(fallbackArticles, context, prefs),
                fallbackArticles
            )
        }

        renderPolicy.appendSourceMetadata(raw, cloudArticles)
    }

    suspend fun summarizeRawContent(content: String): Result<String> = runCatching {
        val prefs = userPreferencesRepository.preferences.first()
        cloudCallPolicy.summarize(content = content, pointsPerNews = prefs.summaryItemsPerNewsInFeed)
    }

    private suspend fun buildCloudInput(
        articles: List<Article>,
        context: SummaryContext,
        prefs: UserPreferences
    ): String {
        val totalLimit = prefs.aiMaxCharsTotal.coerceAtLeast(1000)
        var remaining = totalLimit
        val chunks = mutableListOf<String>()

        for (article in articles) {
            if (remaining <= 0) break
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceType = source?.type ?: SourceType.RSS
            val formatted = formatArticleHeadlineUseCase(article, sourceType)
            val fullContent = articleRepository.fetchFullContent(article)
            val prepared = preprocessPolicy.preprocess(fullContent, prefs, context)
            val content = prepared.textForCloud.take(remaining)
            remaining -= content.length
            chunks += "${formatted.displayTitle}: $content"
        }

        return chunks.joinToString("\n\n")
    }

    private suspend fun buildExtractiveSummary(
        articles: List<Article>,
        context: SummaryContext,
        prefs: UserPreferences
    ): String {
        val contentMap = linkedMapOf<String, String>()
        for (article in articles) {
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceType = source?.type ?: SourceType.RSS
            val formatted = formatArticleHeadlineUseCase(article, sourceType)
            contentMap[formatted.displayTitle] = articleRepository.fetchFullContent(article)
        }
        return buildExtractiveSummaryUseCase(
            headlines = contentMap.keys.toList(),
            contentMap = contentMap,
            topCount = articles.size.coerceAtLeast(1),
            sentencesPerArticle = context.extractiveSentencesLimit(prefs)
        )
    }

    private suspend fun buildCloudComparisonSummary(
        articles: List<Article>,
        context: SummaryContext,
        prefs: UserPreferences
    ): String {
        val input = buildComparisonCloudInput(articles, context, prefs)
        val languageRule = when (prefs.summaryLanguage) {
            SummaryLanguage.ORIGINAL -> "use the same language as the source content."
            SummaryLanguage.UK -> "use Ukrainian language for all text."
            SummaryLanguage.EN -> "use English language for all text."
        }
        val raw = cloudCallPolicy.askQuestion(
            input,
            AiPromptRules.compareJsonPrompt(languageRule).trimIndent()
        )
        val parsed = parsePolicy.parseCompare(raw)
        return renderPolicy.renderCloudCompare(parsed, articles)
    }

    private suspend fun buildComparisonCloudInput(
        articles: List<Article>,
        context: SummaryContext,
        prefs: UserPreferences
    ): String {
        val totalLimit = prefs.aiMaxCharsTotal.coerceAtLeast(1000)
        var remaining = totalLimit
        val blocks = mutableListOf<String>()
        for (article in articles) {
            if (remaining <= 0) break
            val source = articleRepository.getSourceById(article.sourceId)
            val prepared = preprocessPolicy.preprocess(
                rawText = articleRepository.fetchFullContent(article),
                prefs = prefs,
                context = context
            )
            val content = prepared.textForCloud.take(remaining)
            remaining -= content.length
            blocks += buildString {
                append("source_id: ${article.id}\n")
                append("source_name: ${source?.name ?: "Невідоме"}\n")
                append("source_url: ${article.url}\n")
                append("title: ${article.title}\n")
                append("content: $content")
            }
        }
        return blocks.joinToString("\n\n")
    }

}









