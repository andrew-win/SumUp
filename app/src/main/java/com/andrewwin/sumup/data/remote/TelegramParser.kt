package com.andrewwin.sumup.data.remote

import com.andrewwin.sumup.data.local.entities.Article
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

class TelegramParser {

    fun parse(html: String, sourceId: Long): List<Article> {
        return parseWithDebug(html, sourceId).articles
    }

    fun parseWithDebug(html: String, sourceId: Long): TelegramParseResult {
        val doc = Jsoup.parse(html)
        val messages = doc.select(".tgme_widget_message")
        val partsByKey = linkedMapOf<String, MessageParts>()

        messages.forEach { element ->
            val key = buildKey(element)
            val parts = partsByKey.getOrPut(key) { MessageParts() }

            val text = element.selectFirst(".tgme_widget_message_text")?.wholeText()?.trim().orEmpty()
            if (text.isNotBlank()) {
                parts.text = if (parts.text.isNullOrBlank()) text else parts.text + "\n" + text
            }

            val linkElement = element.selectFirst(".tgme_widget_message_date")
            val url = normalizeUrl(linkElement?.attr("href").orEmpty())
            if (url.isNotBlank() && parts.url.isNullOrBlank()) {
                parts.url = url
            }

            val dateStr = extractDateTime(element)
            if (!dateStr.isNullOrBlank() && parts.dateStr.isNullOrBlank()) {
                parts.dateStr = dateStr
            }

            val epochStr = extractEpoch(element)
            if (!epochStr.isNullOrBlank() && parts.epochStr.isNullOrBlank()) {
                parts.epochStr = epochStr
            }

            val viewCount = parseViewCount(
                element.selectFirst(".tgme_widget_message_views")?.text()
            )
            if (viewCount > 0L) {
                parts.viewCount = maxOf(parts.viewCount ?: 0L, viewCount)
            }

            if (parts.mediaUrl.isNullOrBlank()) {
                parts.mediaUrl = extractMediaUrl(element)
            }
        }

        val articles = partsByKey.values.mapNotNull { parts ->
            val fullText = parts.text?.trim().orEmpty()
            if (fullText.isBlank()) return@mapNotNull null

            val lines = fullText.split("\n").filter { it.isNotBlank() }
            val title = lines.firstOrNull()?.take(100) ?: ""

            val publishedAt = parseDate(parts.dateStr)
                ?: parseEpoch(parts.epochStr)
                ?: System.currentTimeMillis()

            Article(
                sourceId = sourceId,
                title = title,
                content = fullText,
                mediaUrl = parts.mediaUrl,
                url = parts.url.orEmpty(),
                publishedAt = publishedAt,
                viewCount = parts.viewCount ?: 0L
            )
        }

        val sortedArticles = articles
            .distinctBy { it.url }
            .sortedByDescending { it.publishedAt }

        val debug = partsByKey.map { (key, parts) ->
            TelegramParseDebug(
                key = key,
                url = parts.url,
                dateStr = parts.dateStr,
                epochStr = parts.epochStr,
                parsedAt = parseDate(parts.dateStr) ?: parseEpoch(parts.epochStr),
                textLength = parts.text?.length ?: 0
            )
        }

        return TelegramParseResult(sortedArticles, debug)
    }

    private fun buildKey(element: org.jsoup.nodes.Element): String {
        val byPost = element.attr("data-post")
        if (byPost.isNotBlank()) return byPost
        val byId = element.id()
        if (byId.isNotBlank()) return byId
        val byMsgId = element.attr("data-message-id")
        if (byMsgId.isNotBlank()) return byMsgId
        return element.selectFirst(".tgme_widget_message_date")?.attr("href").orEmpty()
    }

    private fun parseDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val normalized = if (value.endsWith("Z")) value.dropLast(1) + "+00:00" else value
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        )
        for (format in formats) {
            runCatching { format.parse(normalized)?.time }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun parseEpoch(value: String?): Long? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val number = raw.toLongOrNull() ?: return null
        return if (raw.length >= 13) number else number * 1000L
    }

    private fun extractDateTime(element: org.jsoup.nodes.Element): String? {
        return sequenceOf(
            element.selectFirst("time[datetime]")?.attr("datetime"),
            element.selectFirst(".tgme_widget_message_date time")?.attr("datetime"),
            element.selectFirst("[datetime]")?.attr("datetime"),
            element.parents().select("time[datetime]").firstOrNull()?.attr("datetime"),
            element.parents().select("[datetime]").firstOrNull()?.attr("datetime")
        ).firstOrNull { !it.isNullOrBlank() }
    }

    private fun extractEpoch(element: org.jsoup.nodes.Element): String? {
        return sequenceOf(
            element.selectFirst("time")?.attr("data-time"),
            element.selectFirst("time")?.attr("data-timestamp"),
            element.selectFirst("time")?.attr("data-published"),
            element.selectFirst(".tgme_widget_message_date")?.attr("data-time"),
            element.selectFirst(".tgme_widget_message_date")?.attr("data-timestamp"),
            element.selectFirst(".tgme_widget_message_date")?.attr("data-date"),
            element.selectFirst(".tgme_widget_message_date")?.attr("data-published"),
            element.selectFirst("[data-time]")?.attr("data-time"),
            element.selectFirst("[data-timestamp]")?.attr("data-timestamp"),
            element.selectFirst("[data-date]")?.attr("data-date"),
            element.selectFirst("[data-published]")?.attr("data-published"),
            element.attr("data-time"),
            element.attr("data-timestamp"),
            element.attr("data-date"),
            element.attr("data-published")
        ).firstOrNull { !it.isNullOrBlank() }
    }

    private fun normalizeUrl(url: String): String {
        return when {
            url.startsWith("tg:resolve") -> {
                val domain = url.substringAfter("domain=").substringBefore("&")
                val post = url.substringAfter("post=").substringBefore("&")
                "https://t.me/s/$domain/$post"
            }
            url.startsWith("https://t.me/") -> {
                val path = url.removePrefix("https://t.me/")
                if (!path.startsWith("s/") && !path.startsWith("c/")) {
                    "https://t.me/s/$path"
                } else url
            }
            else -> url
        }
    }

    private fun parseViewCount(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val cleaned = text.trim().uppercase()
        return when {
            cleaned.endsWith("K") -> (cleaned.dropLast(1).toDoubleOrNull() ?: 0.0).times(1_000).toLong()
            cleaned.endsWith("M") -> (cleaned.dropLast(1).toDoubleOrNull() ?: 0.0).times(1_000_000).toLong()
            else -> cleaned.toLongOrNull() ?: 0L
        }
    }

    private data class MessageParts(
        var text: String? = null,
        var url: String? = null,
        var dateStr: String? = null,
        var epochStr: String? = null,
        var viewCount: Long? = null,
        var mediaUrl: String? = null
    )
}

private fun extractMediaUrl(element: org.jsoup.nodes.Element): String? {
    val photo = element.selectFirst(".tgme_widget_message_photo_wrap")
    val style = photo?.attr("style").orEmpty()
    val styleUrl = Regex("url\\(['\\\"]?(.*?)['\\\"]?\\)").find(style)?.groups?.get(1)?.value
    if (!styleUrl.isNullOrBlank()) return styleUrl
    return element.selectFirst("img")?.attr("src")
}

data class TelegramParseDebug(
    val key: String,
    val url: String?,
    val dateStr: String?,
    val epochStr: String?,
    val parsedAt: Long?,
    val textLength: Int
)

data class TelegramParseResult(
    val articles: List<Article>,
    val debug: List<TelegramParseDebug>
)
