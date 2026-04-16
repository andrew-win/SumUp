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
            val withoutDuplicatedTitle = stripLeadingTitleFromSingleArticleSummary(local, formatted.displayTitle)
            return@runCatching renderPolicy.moveSourceToEndForSingleArticle(
                renderPolicy.appendSourceMetadata(withoutDuplicatedTitle, listOf(representative))
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
            val withoutDuplicatedTitle = stripLeadingTitleFromSingleArticleSummary(local, formatted.displayTitle)
            return@runCatching renderPolicy.moveSourceToEndForSingleArticle(
                renderPolicy.appendSourceMetadata(withoutDuplicatedTitle, listOf(representative))
            )
        }

        val cloudInput = "${formatted.displayTitle}: ${prep.textForCloud}"
        val cloud = try {
            cloudCallPolicy.summarize(content = cloudInput)
        } catch (_: Exception) {
            fallbackPolicy.singleArticleFallback(
                title = formatted.displayTitle,
                fullContent = fullContent,
                sentenceLimit = context.extractiveSentencesLimit(prefs)
            )
        }
        val withoutDuplicatedTitle = stripLeadingTitleFromSingleArticleSummary(cloud, formatted.displayTitle)
        renderPolicy.moveSourceToEndForSingleArticle(
            renderPolicy.appendSourceMetadata(withoutDuplicatedTitle, listOf(representative))
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

        val cloudArticles = articles
        val cloudInput = buildCloudInput(cloudArticles, context, prefs)

        val raw = try {
            cloudCallPolicy.summarize(content = cloudInput)
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
        cloudCallPolicy.summarize(content = content)
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
            val contentForSummary = resolveArticleContentForSummary(article, context, prefs)
            val prepared = preprocessPolicy.preprocess(contentForSummary, prefs, context)
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
            contentMap[formatted.displayTitle] = resolveArticleContentForSummary(article, context, prefs)
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
        if (parsed.items.isEmpty()) {
            throw IllegalStateException("Cloud compare JSON has empty items")
        }
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

    private suspend fun resolveArticleContentForSummary(
        article: Article,
        context: SummaryContext,
        prefs: UserPreferences
    ): String {
        return if (context is SummaryContext.Feed && !prefs.isFeedSummaryUseFullTextEnabled) {
            article.content.ifBlank { articleRepository.fetchFullContent(article) }
        } else {
            articleRepository.fetchFullContent(article)
        }
    }

    private fun stripLeadingTitleFromSingleArticleSummary(summary: String, title: String): String {
        if (summary.isBlank() || title.isBlank()) return summary
        val trimmed = summary.trim()
        val normalizedTitle = normalizeForCompare(title)
        if (normalizedTitle.isBlank()) return trimmed

        val lines = trimmed.lines()
        if (lines.isEmpty()) return trimmed

        val firstLine = lines.first().trim()
        val firstLineNormalized = normalizeForCompare(
            firstLine
                .removePrefix("*")
                .removeSuffix("*")
                .removePrefix("**")
                .removeSuffix("**")
                .replace(Regex("^\\d+[.)]\\s*"), "")
        )

        if (firstLineNormalized == normalizedTitle) {
            return lines.drop(1).joinToString("\n").trim()
        }

        val titlePrefixPattern = Regex(
            "^\\s*(?:\\*\\*?)?\\Q${title.trim().removeSuffix(":")}\\E(?:\\*\\*?)?\\s*:\\s*",
            RegexOption.IGNORE_CASE
        )
        return trimmed.replaceFirst(titlePrefixPattern, "").trim()
    }

    private fun normalizeForCompare(value: String): String {
        return value.lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
            .removeSuffix(":")
            .removeSuffix(".")
    }

}









