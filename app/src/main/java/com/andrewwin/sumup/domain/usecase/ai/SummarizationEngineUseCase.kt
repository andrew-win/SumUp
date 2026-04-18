package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.local.entities.SummaryLanguage
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.support.DebugTrace
import com.andrewwin.sumup.domain.support.SummarySourceMeta
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
    private data class FallbackThemeDefinition(
        val key: String,
        val title: String,
        val emojis: List<String>,
        val keywords: List<String>
    )

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
        DebugTrace.d(
            "summary_engine",
            "summarizeSingleArticle articleId=${representative.id} dup=${duplicates.size} strategy=${prefs.aiStrategy} sourceType=$sourceType articleChars=${representative.content.length} fullChars=${fullContent.length} title=${DebugTrace.preview(formatted.displayTitle, 140)}"
        )

        if (hasDuplicates) {
            return@runCatching if (prefs.aiStrategy == AiStrategy.LOCAL) {
                DebugTrace.d("summary_engine", "singleArticle branch=localCompare")
                renderPolicy.renderLocalCompare(allArticles)
            } else {
                DebugTrace.d("summary_engine", "singleArticle branch=cloudCompare")
                runCatching { buildCloudComparisonSummary(allArticles, context, prefs) }
                    .getOrElse {
                        DebugTrace.e("summary_engine", "singleArticle cloudCompare failed, fallback=localCompare", it)
                        renderPolicy.renderLocalCompare(allArticles)
                    }
            }
        }

        if (prefs.aiStrategy == AiStrategy.LOCAL) {
            DebugTrace.d(
                "summary_engine",
                "singleArticle branch=localSummary sentenceLimit=${context.extractiveSentencesLimit(prefs)}"
            )
            val local = fallbackPolicy.singleArticleFallback(
                title = formatted.displayTitle,
                fullContent = fullContent,
                sentenceLimit = context.extractiveSentencesLimit(prefs)
            )
            val withoutDuplicatedTitle = stripLeadingTitleFromSingleArticleSummary(local, formatted.displayTitle)
            return@runCatching renderPolicy.moveSourceToEndForSingleArticle(
                renderPolicy.appendSourceMetadata(withoutDuplicatedTitle, listOf(representative))
            ).also {
                DebugTrace.d("summary_engine", "singleArticle final branch=localSummary preview=${DebugTrace.preview(it)}")
            }
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
        DebugTrace.d(
            "summary_engine",
            "singleArticle preprocess textForCloudChars=${prep.textForCloud.length} finalExtractive=${prep.finalExtractiveText != null} fullTextUsed=${sourceType == SourceType.YOUTUBE || formatted.displayContent.isBlank()} contentPreview=${DebugTrace.preview(prep.textForCloud, 220)}"
        )
        prep.finalExtractiveText?.let {
            DebugTrace.d(
                "summary_engine",
                "singleArticle branch=preprocessExtractiveFallback extractiveChars=${it.length} sentenceLimit=${context.extractiveSentencesLimit(prefs)}"
            )
            val local = fallbackPolicy.singleArticleFallback(
                title = formatted.displayTitle,
                fullContent = it,
                sentenceLimit = context.extractiveSentencesLimit(prefs)
            )
            val withoutDuplicatedTitle = stripLeadingTitleFromSingleArticleSummary(local, formatted.displayTitle)
            return@runCatching renderPolicy.moveSourceToEndForSingleArticle(
                renderPolicy.appendSourceMetadata(withoutDuplicatedTitle, listOf(representative))
            ).also {
                DebugTrace.d("summary_engine", "singleArticle final branch=preprocessExtractiveFallback preview=${DebugTrace.preview(it)}")
            }
        }

        val cloudInput = "${formatted.displayTitle}: ${prep.textForCloud}"
        DebugTrace.d(
            "summary_engine",
            "singleArticle branch=cloud inputChars=${cloudInput.length} inputPreview=${DebugTrace.preview(cloudInput, 260)}"
        )
        val cloud = try {
            cloudCallPolicy.summarize(content = cloudInput)
        } catch (e: Exception) {
            DebugTrace.e("summary_engine", "singleArticle cloudSummary failed, fallback=singleArticleFallback", e)
            fallbackPolicy.singleArticleFallback(
                title = formatted.displayTitle,
                fullContent = fullContent,
                sentenceLimit = context.extractiveSentencesLimit(prefs)
            )
        }
        val withoutDuplicatedTitle = stripLeadingTitleFromSingleArticleSummary(cloud, formatted.displayTitle)
        renderPolicy.moveSourceToEndForSingleArticle(
            renderPolicy.appendSourceMetadata(withoutDuplicatedTitle, listOf(representative))
        ).also {
            DebugTrace.d("summary_engine", "singleArticle final branch=cloudOrFallback preview=${DebugTrace.preview(it)}")
        }
    }

    suspend fun summarizeArticles(articles: List<Article>, context: SummaryContext): Result<String> = runCatching {
        if (articles.isEmpty()) return@runCatching ""
        val prefs = userPreferencesRepository.preferences.first()
        DebugTrace.d(
            "summary_engine",
            "summarizeArticles context=${context::class.simpleName} articles=${articles.size} strategy=${prefs.aiStrategy}"
        )

        if (prefs.aiStrategy == AiStrategy.LOCAL) {
            val extractiveArticles = articles.take(context.extractiveNewsLimit(prefs))
            DebugTrace.d("summary_engine", "branch=local extractiveArticles=${extractiveArticles.size}")
            return@runCatching buildMultiArticleFallbackSummary(extractiveArticles, context, prefs)
        }

        val cloudArticles = articles
        val cloudInput = buildCloudInput(cloudArticles, context, prefs)
        DebugTrace.d("summary_engine", "branch=cloud cloudInputPreview=${DebugTrace.preview(cloudInput, 350)}")

        val raw = try {
            cloudCallPolicy.summarize(content = cloudInput)
        } catch (e: Exception) {
            DebugTrace.e("summary_engine", "cloud summarize failed, falling back to local themed summary", e)
            val fallbackArticles = articles.take(context.extractiveNewsLimit(prefs))
            return@runCatching buildMultiArticleFallbackSummary(fallbackArticles, context, prefs)
        }

        renderPolicy.appendSourceMetadata(raw, cloudArticles).also {
            DebugTrace.d("summary_engine", "finalSummaryPreview=${DebugTrace.preview(it)}")
        }
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
            val content = prepared.textForCloud
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(remaining)
            remaining -= content.length
            chunks += buildString {
                append("source_id: ${article.id}\n")
                append("source_name: ${source?.name?.ifBlank { "Джерело" } ?: "Джерело"}\n")
                append("source_url: ${article.url.ifBlank { source?.url.orEmpty() }}\n")
                append("title: ${formatted.displayTitle}\n")
                append("content: $content")
            }
        }

        return chunks.joinToString("\n\n").also {
            DebugTrace.d("summary_engine", "buildCloudInput chunks=${chunks.size} totalLimit=$totalLimit remaining=$remaining")
        }
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

    private suspend fun buildMultiArticleFallbackSummary(
        articles: List<Article>,
        context: SummaryContext,
        prefs: UserPreferences
    ): String {
        if (articles.size <= 1) {
            return renderPolicy.appendSourceMetadata(
                buildExtractiveSummary(articles, context, prefs),
                articles
            )
        }

        data class FallbackItem(
            val article: Article,
            val sourceName: String,
            val sourceUrl: String,
            val title: String,
            val content: String
        )

        val prepared = articles.map { article ->
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceType = source?.type ?: SourceType.RSS
            val formatted = formatArticleHeadlineUseCase(article, sourceType)
            FallbackItem(
                article = article,
                sourceName = source?.name?.ifBlank { "Джерело" } ?: "Джерело",
                sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty(),
                title = formatted.displayTitle.ifBlank { article.title.ifBlank { "Новина" } },
                content = resolveArticleContentForSummary(article, context, prefs)
            )
        }

        val grouped = prepared.groupBy { item -> classifyFallbackTheme(item.title, item.content).key }
        val selectedGroups = grouped.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<FallbackItem>>> { it.value.size }
                    .thenBy { fallbackThemeDefinition(it.key).title }
            )
            .take(MAX_FALLBACK_THEMES)

        val groupsToRender: List<Pair<String, List<FallbackItem>>> = if (selectedGroups.any { it.value.size >= MIN_FALLBACK_THEME_ITEMS }) {
            selectedGroups
                .filter { it.value.size >= MIN_FALLBACK_THEME_ITEMS }
                .map { it.key to it.value }
        } else {
            listOf("generic" to prepared.take(MAX_FALLBACK_ITEMS_PER_THEME))
        }

        return groupsToRender.joinToString("\n\n") { (themeKey, items) ->
            val definition = fallbackThemeDefinition(themeKey)
            buildString {
                append(definition.emojis.joinToString(separator = ""))
                append(' ')
                append(definition.title)
                items.take(MAX_FALLBACK_ITEMS_PER_THEME).forEach { item ->
                    append("\n")
                    append(FALLBACK_ITEM_MARKER)
                    append(' ')
                    append(compactFallbackTitle(item.title))
                    if (item.sourceUrl.isNotBlank()) {
                        append("\n")
                        append(SummarySourceMeta.PREFIX)
                        append(item.sourceName)
                        append('|')
                        append(item.sourceUrl)
                    }
                }
            }
        }.also {
            DebugTrace.d(
                "summary_engine",
                "buildMultiArticleFallbackSummary themes=${groupsToRender.size} preview=${DebugTrace.preview(it)}"
            )
        }
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

    private fun classifyFallbackTheme(title: String, content: String): FallbackThemeDefinition {
        val haystack = normalizeThemeText("$title $content")
        val best = FALLBACK_THEME_DEFINITIONS
            .map { definition ->
                definition to definition.keywords.count { keyword -> haystack.contains(keyword) }
            }
            .maxWithOrNull(compareBy<Pair<FallbackThemeDefinition, Int>> { it.second }.thenByDescending { it.first.keywords.size })
            ?.takeIf { it.second > 0 }
            ?.first
        return best ?: GENERIC_FALLBACK_THEME
    }

    private fun fallbackThemeDefinition(key: String): FallbackThemeDefinition {
        return FALLBACK_THEME_DEFINITIONS.firstOrNull { it.key == key } ?: GENERIC_FALLBACK_THEME
    }

    private fun compactFallbackTitle(title: String): String {
        val clean = title
            .replace(Regex("\\s+"), " ")
            .trim()
            .removePrefix(FALLBACK_ITEM_MARKER)
            .trim()
            .removeSuffix(".")
            .removeSuffix(":")
        if (clean.length <= MAX_FALLBACK_TITLE_CHARS) return clean
        val clipped = clean.take(MAX_FALLBACK_TITLE_CHARS).trimEnd()
        val lastSpace = clipped.lastIndexOf(' ')
        val base = if (lastSpace > MAX_FALLBACK_TITLE_CHARS / 2) {
            clipped.substring(0, lastSpace)
        } else {
            clipped
        }
        return base.trimEnd(' ', ',', ';', ':') + "…"
    }

    private fun normalizeThemeText(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private companion object {
        const val MIN_FALLBACK_THEME_ITEMS = 2
        const val MAX_FALLBACK_ITEMS_PER_THEME = 6
        const val MAX_FALLBACK_THEMES = 5
        const val MAX_FALLBACK_TITLE_CHARS = 90
        const val FALLBACK_ITEM_MARKER = "—"

        val GENERIC_FALLBACK_THEME = FallbackThemeDefinition(
            key = "generic",
            title = "Ключові новини",
            emojis = listOf("📰", "📌", "🧭"),
            keywords = emptyList()
        )

        val FALLBACK_THEME_DEFINITIONS = listOf(
            FallbackThemeDefinition(
                key = "ukraine_war",
                title = "Війна Росії проти України",
                emojis = listOf("🇺🇦", "⚔️", "🛡️"),
                keywords = listOf("зсу", "сили оборони", "україн", "росі", "окуп", "обстр", "удар", "дрон", "ракет", "військ", "фронт")
            ),
            FallbackThemeDefinition(
                key = "odesa_region",
                title = "Одещина та регіон",
                emojis = listOf("🌊", "🏖️", "📍"),
                keywords = listOf("одес", "odesa", "дністров", "білгород", "лиман", "чорномор", "порт")
            ),
            FallbackThemeDefinition(
                key = "world_politics",
                title = "Світова політика",
                emojis = listOf("🌍", "🏛️", "🗳️"),
                keywords = listOf("трамп", "сша", "єс", "нато", "китай", "іран", "ізраїл", "санкц", "вибор")
            ),
            FallbackThemeDefinition(
                key = "technology",
                title = "Технології",
                emojis = listOf("💻", "🤖", "🚀"),
                keywords = listOf("технолог", "ai", "штучн", "openai", "google", "apple", "microsoft", "чип", "робот", "стартап")
            ),
            FallbackThemeDefinition(
                key = "economy_business",
                title = "Економіка та бізнес",
                emojis = listOf("💰", "📈", "🏭"),
                keywords = listOf("економ", "бізнес", "компан", "ринок", "ціна", "експорт", "інвест", "банк", "нафт", "газ")
            ),
            FallbackThemeDefinition(
                key = "incidents_society",
                title = "Інциденти та суспільство",
                emojis = listOf("🚨", "⚖️", "🏥"),
                keywords = listOf("затрим", "незакон", "поліц", "суд", "авар", "пожеж", "лікар", "музей", "освіт", "культур")
            )
        )
    }

}









