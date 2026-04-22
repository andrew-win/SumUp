package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.service.ExtractiveSummarizer
import com.andrewwin.sumup.domain.support.DispatcherProvider
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.support.SummarySourceMeta
import com.andrewwin.sumup.ui.screen.feed.model.ArticleClusterUiModel
import com.andrewwin.sumup.ui.screen.feed.model.ArticleUiModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONException
import javax.inject.Inject

class CompareArticlesUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val articleRepository: ArticleRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(cluster: ArticleClusterUiModel): Result<String> = withContext(dispatcherProvider.default) {
        val representative = cluster.representative
        val duplicates = cluster.duplicates.map { it.first }
        val allArticles = listOf(representative) + duplicates
        
        if (allArticles.size < 2) {
            return@withContext Result.success("Недостатньо джерел для порівняння.")
        }

        val prefs = userPreferencesRepository.preferences.first()
        val strategy = prefs.aiStrategy

        if (strategy == AiStrategy.ADAPTIVE) {
            val perArticleLimit = prefs.aiMaxCharsPerFeedArticle.coerceAtLeast(200)
            val longestArticleLength = allArticles.maxOf { it.article.content.take(perArticleLimit).length }
            val adaptiveExtractiveOnlyBelowChars = prefs.adaptiveExtractiveOnlyBelowChars.coerceAtLeast(1)
            if (longestArticleLength < adaptiveExtractiveOnlyBelowChars) {
                return@withContext runCatching { performLocalComparison(allArticles) }
            }
        }

        if (strategy == AiStrategy.CLOUD || strategy == AiStrategy.ADAPTIVE) {
            val combinedContent = buildCloudComparisonInput(allArticles, prefs, strategy)
            
            val prompt = AiPromptCatalog.buildComparePrompt(
                summaryLanguage = prefs.summaryLanguage
            )
            
            return@withContext runCatching {
                val raw = aiRepository.askQuestion(combinedContent, prompt)
                formatCloudComparison(raw, allArticles)
            }.recoverCatching { error ->
                if (strategy == AiStrategy.CLOUD && error !is JSONException) throw error
                performLocalComparison(allArticles)
            }
        }

        return@withContext runCatching { performLocalComparison(allArticles) }
    }

    private suspend fun buildCloudComparisonInput(
        allArticles: List<ArticleUiModel>,
        prefs: UserPreferences,
        strategy: AiStrategy
    ): String {
        val maxTotalChars = prefs.aiMaxCharsTotal.coerceAtLeast(1000)
        val perArticleLimit = prefs.aiMaxCharsPerFeedArticle.coerceAtLeast(200)
        val adaptiveExtractiveCompressAboveChars = prefs.adaptiveExtractiveCompressAboveChars.coerceAtLeast(1)
        val adaptiveExtractiveCompressionPercent = prefs.adaptiveExtractiveCompressionPercent.coerceIn(1, 100)
        var remainingTotal = maxTotalChars
        val blocks = mutableListOf<String>()

        allArticles.forEach { article ->
            val fullContent = articleRepository.fetchFullContent(article.article)
            val baseSource = when {
                fullContent.isNotBlank() -> fullContent
                article.article.content.isNotBlank() -> article.article.content
                else -> article.displayTitle
            }
            val base = baseSource.take(perArticleLimit)
            val prepared = if (
                strategy == AiStrategy.ADAPTIVE &&
                base.length > adaptiveExtractiveCompressAboveChars
            ) {
                val extractiveChars =
                    (base.length * (adaptiveExtractiveCompressionPercent.toFloat() / 100f)).toInt().coerceAtLeast(1)
                val extractiveSentenceEstimate = estimateSentenceCountByTargetLength(base, extractiveChars)
                ExtractiveSummarizer.summarize(base, extractiveSentenceEstimate).joinToString(" ")
            } else {
                base
            }
            val content = prepared.take(remainingTotal.coerceAtLeast(0))
            remainingTotal = (remainingTotal - content.length).coerceAtLeast(0)
            blocks += buildString {
                append("source_id: ${article.article.id}\n")
                append("title: ${article.displayTitle}\n")
                append("content: $content")
            }
        }
        return blocks.joinToString("\n\n")
    }

    private fun estimateSentenceCountByTargetLength(content: String, targetChars: Int): Int {
        if (content.isBlank()) return 1
        val sentenceCount = content
            .split(Regex("(?<=[.!?…])\\s+"))
            .map { it.trim() }
            .count { it.isNotBlank() }
            .coerceAtLeast(1)
        val ratio = (targetChars.toFloat() / content.length.coerceAtLeast(1)).coerceIn(0.05f, 1f)
        return (sentenceCount * ratio).toInt().coerceAtLeast(1)
    }

    private suspend fun performLocalComparison(articles: List<ArticleUiModel>): String {
        val mainArticle = articles.first()
        val itemLines = mutableListOf<String>()

        articles.forEach { art ->
            val summarySentences = ExtractiveSummarizer
                .summarize(art.article.content, 2)
                .map { takeFirstSentence(it) }
                .filter { it.isNotBlank() }
                .distinctBy { normalizeSentenceForCompare(it) }

            val normalized = summarySentences.take(2)
            if (normalized.isEmpty()) return@forEach

            normalized.forEach { sentence ->
                val cleanedSentence = sentence.replace(Regex("https?://\\S+"), "").trim()
                if (cleanedSentence.isBlank()) return@forEach
                itemLines += "• $cleanedSentence"
                itemLines += "${SummarySourceMeta.PREFIX}${art.sourceName ?: "Джерело"}|${art.article.url}"
            }
        }

        if (itemLines.isEmpty()) {
            return "${mainArticle.displayTitle}:\n• Немає достатньо даних для порівняння."
        }

        return "${mainArticle.displayTitle}:\n${itemLines.joinToString("\n")}"
    }

    private fun normalizeSentenceForCompare(value: String): String {
        return value
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), "")
            .trim()
    }

    private fun takeFirstSentence(value: String): String {
        val normalized = value.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return ""
        val split = Regex("(?<=[.!?…])\\s+").split(normalized)
        return split.firstOrNull()?.trim().orEmpty()
    }

    private fun formatCloudComparison(raw: String, articles: List<ArticleUiModel>): String {
        val parsed = AiJsonResponseParser.parseCompare(raw)
        val articleById = articles.associateBy { it.article.id.toString() }
        val validCommonFacts = parsed.commonFacts
            .mapNotNull { fact ->
                val resolvedArticles = fact.sourceIds
                    .mapNotNull { articleById[it] }
                    .distinctBy { it.article.id }
                val distinctUrls = resolvedArticles
                    .map { it.article.url.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                val isValidCommon = fact.text.isNotBlank() &&
                    resolvedArticles.size >= 2 &&
                    distinctUrls.size >= 2
                if (isValidCommon) {
                    fact to resolvedArticles
                } else {
                    null
                }
            }
        val differentLines = mutableListOf<Pair<String, ArticleUiModel>>()

        parsed.items.forEach { item ->
            val sourceId = item.sourceId?.trim().orEmpty()
            val article = articleById[sourceId] ?: return@forEach
            item.uniqueDetails
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { different ->
                    differentLines += different to article
                }
        }

        val builder = StringBuilder()
        builder.append("Спільне:\n")
        if (validCommonFacts.isNotEmpty()) {
            validCommonFacts.forEach { (commonFact, resolvedArticles) ->
                builder.append("— ${commonFact.text.trim()}\n")
                resolvedArticles
                    .distinctBy { it.article.url.trim() }
                    .forEach { article ->
                    builder.append("${SummarySourceMeta.PREFIX}${article.sourceName ?: "Джерело"}|${article.article.url}\n")
                }
            }
        } else {
            val fallbackText = parsed.commonTopic
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { "Хоча новини стосуються теми $it, спільних фрагментів знайдено не було" }
                ?: parsed.fallbackMessage?.ifBlank { null }
                ?: "Спільних фрагментів знайдено не було"
            builder.append("— $fallbackText\n")
        }
        
        if (differentLines.isNotEmpty()) {
            builder.append("\n")
            builder.append("Відмінне:\n")
            differentLines.forEach { (text, article) ->
                builder.append("— $text\n")
                builder.append("${SummarySourceMeta.PREFIX}${article.sourceName ?: "Джерело"}|${article.article.url}\n")
            }
        }
        return builder.toString().trim()
    }
}









