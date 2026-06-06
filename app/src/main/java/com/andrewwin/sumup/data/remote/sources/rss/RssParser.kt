package com.andrewwin.sumup.data.remote.sources.rss

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.remote.sources.ArticleStableKeyFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class RssParser {
    private val formattersThreadLocal = ThreadLocal.withInitial {
        listOf(
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("EEE, dd MMM yy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        ).onEach { it.timeZone = TimeZone.getTimeZone("UTC") }
    }

    suspend fun parseArticlesXml(xml: String, sourceId: Long): Result<List<Article>> {
        return runCatching {
            if (xml.isBlank()) return@runCatching emptyList()
            withContext(Dispatchers.Default) {
                val document = Jsoup.parse(xml, "", Parser.xmlParser())
                document.select("item, entry").mapNotNull { item ->
                    mapItem(item, sourceId)
                }
            }
        }
    }

    suspend fun parseChannelTitleXml(xml: String): String? {
        return runCatching {
            withContext(Dispatchers.Default) {
                val document = Jsoup.parse(xml, "", Parser.xmlParser())
                document.selectFirst("channel > title, feed > title")
                    ?.text()
                    .orEmpty()
                    .cleanChannelTitle()
            }
        }.getOrNull()
    }

    private fun mapItem(item: Element, sourceId: Long): Article? {
        val title = item.firstText("title")
        val link = item.firstText("link")
            .ifBlank { item.selectFirst("link[href]")?.attr("href").orEmpty() }
        val guid = item.firstText("guid, id")
        val description = item.firstText("description, summary")
        val content = item.firstText("content|encoded, encoded, content")
        val pubDate = parseRssDate(item.firstText("pubDate, published, updated"))
        val rawUrl = if (guid.startsWith("http")) guid else link
        if (rawUrl.isBlank()) return null
        val cleanUrl = rawUrl.substringBefore("#")

        val mediaUrl = extractImageFromHtml(content.ifBlank { description })

        return Article(
            stableArticleKey = ArticleStableKeyFactory.buildRssKey(
                sourceId = sourceId,
                guid = guid,
                url = cleanUrl
            ),
            sourceId = sourceId,
            title = title,
            content = if (description.isNotBlank()) description else content,
            mediaUrl = mediaUrl,
            url = cleanUrl,
            publishedAt = if (pubDate == 0L) System.currentTimeMillis() else pubDate
        )
    }

    private fun parseRssDate(dateString: String): Long {
        val trimmed = dateString.trim()
        val formatters = formattersThreadLocal.get().orEmpty()
        for (formatter in formatters) {
            val date = runCatching { formatter.parse(trimmed) }.getOrNull()
            if (date != null) {
                val time = date.time
                if (time >= MIN_REASONABLE_TIMESTAMP) return time
            }
        }
        return 0L
    }

    private fun extractImageFromHtml(html: String): String? =
        IMAGE_SRC_REGEX.find(html)?.groups?.get(1)?.value

    private fun Element.firstText(cssQuery: String): String =
        selectFirst(cssQuery)?.text().orEmpty()

    private fun String.cleanChannelTitle(): String? {
        val cleaned = trim()
            .removeSurrounding("<![CDATA[", "]]>")
            .trim()
        return cleaned.ifBlank { null }
    }

    companion object {
        private const val MIN_REASONABLE_TIMESTAMP = 946684800000L
        private val IMAGE_SRC_REGEX = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    }
}
