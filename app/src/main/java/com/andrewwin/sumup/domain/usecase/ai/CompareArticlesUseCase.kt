package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.service.ExtractiveSummarizer
import com.andrewwin.sumup.domain.support.DispatcherProvider
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.support.SummarySourceMeta
import com.andrewwin.sumup.ui.screen.feed.model.ArticleClusterUiModel
import com.andrewwin.sumup.ui.screen.feed.model.ArticleUiModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

class CompareArticlesUseCase @Inject constructor(
    private val aiRepository: AiRepository,
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
            
            val prompt = """
                Поверни ТІЛЬКИ валідний JSON і нічого більше.
                Без markdown, без коментарів, без пояснень.
                Формат:
                {
                  "headline": "короткий заголовок події",
                  "items": [
                    {
                      "source_id": "id джерела з вхідних даних",
                      "common": "2 короткі речення про спільне для цього джерела",
                      "different": "2 короткі речення про відмінне для цього джерела"
                    }
                  ]
                }
                Правила:
                1) У "common" рівно 2 речення.
                2) У "different" рівно 2 речення.
                3) Не вставляй URL у "headline", "common", "different".
                4) Для кожного джерела з вхідних даних має бути item.
                5) common/different ПОВИННІ бути масивами рядків:
                   "common": ["речення 1", "речення 2"]
                   "different": ["речення 1", "речення 2"]
            """.trimIndent()
            
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

    private fun buildCloudComparisonInput(
        allArticles: List<ArticleUiModel>,
        prefs: UserPreferences,
        strategy: AiStrategy
    ): String {
        val maxTotalChars = prefs.aiMaxCharsTotal.coerceAtLeast(1000)
        val perArticleLimit = prefs.aiMaxCharsPerFeedArticle.coerceAtLeast(200)
        val adaptiveExtractiveCompressAboveChars = prefs.adaptiveExtractiveCompressAboveChars.coerceAtLeast(1)
        val adaptiveExtractiveCompressionPercent = prefs.adaptiveExtractiveCompressionPercent.coerceIn(1, 100)
        var remainingTotal = maxTotalChars

        return allArticles.joinToString("\n\n") { article ->
            val base = article.article.content.take(perArticleLimit)
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
            buildString {
                append("source_id: ${article.article.id}\n")
                append("source_name: ${article.sourceName ?: "Невідоме"}\n")
                append("source_url: ${article.article.url}\n")
                append("title: ${article.displayTitle}\n")
                append("content: $content")
            }
        }
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
        val json = normalizeCloudJson(extractJson(raw))
        val obj = JSONObject(json)
        val headline = articles.first().displayTitle
        val items = obj.optJSONArray("items") ?: JSONArray()
        val articleById = articles.associateBy { it.article.id.toString() }

        val commonLines = mutableListOf<Pair<String, ArticleUiModel>>()
        val differentLines = mutableListOf<Pair<String, ArticleUiModel>>()

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val sourceId = item.opt("source_id")?.toString()?.trim().orEmpty()
            val article = articleById[sourceId] ?: continue

            val common = enforceTwoSentences(readFieldAsText(item, "common"))
            if (common.isNotBlank()) {
                commonLines += common to article
            }

            val different = enforceTwoSentences(readFieldAsText(item, "different"))
            if (different.isNotBlank()) {
                differentLines += different to article
            }
        }

        val builder = StringBuilder()
        builder.append("$headline:\n")
        if (commonLines.isNotEmpty()) {
            builder.append("Спільне:\n")
            commonLines.forEach { (text, article) ->
                builder.append("• $text\n")
                builder.append("${SummarySourceMeta.PREFIX}${article.sourceName ?: "Джерело"}|${article.article.url}\n")
            }
        }
        if (differentLines.isNotEmpty()) {
            if (commonLines.isNotEmpty()) builder.append("\n")
            builder.append("Відмінне:\n")
            differentLines.forEach { (text, article) ->
                builder.append("• $text\n")
                builder.append("${SummarySourceMeta.PREFIX}${article.sourceName ?: "Джерело"}|${article.article.url}\n")
            }
        }
        return builder.toString().trim()
    }

    private fun normalizeCloudJson(json: String): String {
        if (json.isBlank()) return "{}"
        // Fix invalid pattern: "common": "a", "b"  ->  "common": ["a","b"]
        // Same for "different".
        return json
            .replace(
                Regex("\"(common|different)\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"([^\"]*)\""),
                "\"$1\": [\"$2\", \"$3\"]"
            )
    }

    private fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val fenced = Regex("```(?:json)?\\s*(\\{[\\s\\S]*\\})\\s*```").find(trimmed)?.groupValues?.get(1)
        if (!fenced.isNullOrBlank()) return fenced.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1).trim()
        return "{}"
    }

    private fun enforceTwoSentences(value: String): String {
        val cleaned = value
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isBlank()) return ""
        val parts = Regex("(?<=[.!?…])\\s+")
            .split(cleaned)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { normalizeSentenceForCompare(it) }
        return parts.take(2).joinToString(" ").trim()
    }

    private fun readFieldAsText(item: JSONObject, key: String): String {
        val value = item.opt(key) ?: return ""
        return when (value) {
            is JSONArray -> {
                buildString {
                    for (idx in 0 until value.length()) {
                        val part = value.optString(idx).trim()
                        if (part.isBlank()) continue
                        if (isNotBlank()) append(' ')
                        append(part)
                    }
                }
            }
            else -> value.toString()
        }
    }
}









