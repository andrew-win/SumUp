package com.andrewwin.sumup.data.remote.sources.rss

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.remote.sources.ArticleStableKeyFactory
import com.andrewwin.sumup.data.remote.sources.SourceRefreshBoundary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
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

    suspend fun parseArticlesXml(
        xml: String,
        sourceId: Long,
        refreshBoundary: SourceRefreshBoundary = SourceRefreshBoundary.Empty
    ): Result<List<Article>> {
        return runCatching {
            if (xml.isBlank()) return@runCatching emptyList()
            withContext(Dispatchers.Default) {
                val parser = newXmlPullParser(xml)
                val articles = mutableListOf<Article>()
                var currentItem: RssItemBuilder? = null
                var shouldStop = false

                while (!shouldStop && parser.next() != XmlPullParser.END_DOCUMENT) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> {
                            val tagName = parser.name.orEmpty()
                            if (tagName.isRssItemTag()) {
                                currentItem = RssItemBuilder()
                            } else {
                                currentItem?.readTag(parser, tagName)
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name.orEmpty().isRssItemTag()) {
                                currentItem?.toArticle(sourceId)?.let { article ->
                                    if (refreshBoundary.isKnown(article)) {
                                        shouldStop = true
                                    } else {
                                        articles.add(article)
                                    }
                                }
                                currentItem = null
                            }
                        }
                    }
                }

                articles
            }
        }
    }

    suspend fun parseChannelTitleXml(xml: String): String? {
        return runCatching {
            if (xml.isBlank()) return@runCatching null
            withContext(Dispatchers.Default) {
                val parser = newXmlPullParser(xml)
                var insideChannel = false
                var insideFeed = false
                var channelDepth = -1
                var feedDepth = -1

                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name.orEmpty().localTagName()) {
                                "channel" -> {
                                    insideChannel = true
                                    channelDepth = parser.depth
                                }
                                "feed" -> {
                                    insideFeed = true
                                    feedDepth = parser.depth
                                }
                                "title" -> {
                                    if (insideChannel || insideFeed) {
                                        return@withContext readText(parser).cleanChannelTitle()
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (insideChannel && parser.depth == channelDepth) {
                                insideChannel = false
                            }
                            if (insideFeed && parser.depth == feedDepth) {
                                insideFeed = false
                            }
                        }
                    }
                }

                null
            }
        }.getOrNull()
    }

    private fun RssItemBuilder.readTag(parser: XmlPullParser, rawTagName: String) {
        val tagName = rawTagName.localTagName()
        when (tagName) {
            "title" -> if (title.isBlank()) title = readText(parser)
            "link" -> {
                val href = parser.getAttributeValue(null, "href").orEmpty()
                val value = readText(parser)
                if (link.isBlank()) {
                    link = value.ifBlank { href }
                }
            }
            "guid", "id" -> if (guid.isBlank()) guid = readText(parser)
            "description", "summary" -> if (description.isBlank()) description = readText(parser)
            "encoded", "content" -> if (content.isBlank()) content = readText(parser)
            "pubDate", "published", "updated" -> if (publishedRaw.isBlank()) publishedRaw = readText(parser)
        }
    }

    private fun RssItemBuilder.toArticle(sourceId: Long): Article? {
        val rawUrl = if (guid.startsWith("http")) guid else link
        if (rawUrl.isBlank()) return null
        val cleanUrl = rawUrl.substringBefore("#")
        val pubDate = parseRssDate(publishedRaw)
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

    private fun newXmlPullParser(xml: String): XmlPullParser {
        return XmlPullParserFactory.newInstance().newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }
    }

    private fun readText(parser: XmlPullParser): String {
        val text = StringBuilder()
        var depth = 1
        while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.TEXT,
                XmlPullParser.CDSECT -> text.append(parser.text.orEmpty())
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
            }
        }
        return text.toString().trim()
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

    private fun String.cleanChannelTitle(): String? {
        val cleaned = trim()
            .removeSurrounding("<![CDATA[", "]]>")
            .trim()
        return cleaned.ifBlank { null }
    }

    private fun String.isRssItemTag(): Boolean {
        val tagName = localTagName()
        return tagName == "item" || tagName == "entry"
    }

    private fun String.localTagName(): String = substringAfter(':')

    private data class RssItemBuilder(
        var title: String = "",
        var link: String = "",
        var guid: String = "",
        var description: String = "",
        var content: String = "",
        var publishedRaw: String = ""
    )

    companion object {
        private const val MIN_REASONABLE_TIMESTAMP = 946684800000L
        private val IMAGE_SRC_REGEX = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    }
}
