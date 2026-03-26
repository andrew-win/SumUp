package com.andrewwin.sumup.data.remote

import android.util.Xml
import com.andrewwin.sumup.data.local.entities.Article
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

class YouTubeParser {
    private val tag = "YouTubeParser"

    fun parse(inputStream: InputStream, sourceId: Long): List<Article> {
        inputStream.use {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)
            parser.nextTag()
            return readFeed(parser, sourceId)
        }
    }

    private fun readFeed(parser: XmlPullParser, sourceId: Long): List<Article> {
        val articles = mutableListOf<Article>()
        parser.require(XmlPullParser.START_TAG, null, "feed")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "entry") {
                val article = readEntry(parser, sourceId)
                if (!isShortsArticle(article)) {
                    articles.add(article)
                }
            } else {
                skip(parser)
            }
        }
        return articles
    }

    private fun readEntry(parser: XmlPullParser, sourceId: Long): Article {
        var title = ""
        var link = ""
        var description = ""
        var published = 0L
        var viewCount = 0L
        var videoId = ""
        var thumbnailUrl: String? = null

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "title" -> title = readText(parser)
                "link" -> {
                    link = parser.getAttributeValue(null, "href")
                    skip(parser)
                }
                "yt:videoId", "videoId" -> videoId = extractVideoIdFromText(readText(parser)).orEmpty()
                "id" -> {
                    val value = readText(parser).trim()
                    if (value.startsWith("yt:video:")) {
                        videoId = extractVideoIdFromText(value.removePrefix("yt:video:")).orEmpty()
                    }
                }
                "published" -> published = parseDate(readText(parser))
                "media:group" -> {
                    val result = readMediaGroup(parser)
                    description = result.description
                    viewCount = result.viewCount
                    thumbnailUrl = result.thumbnailUrl ?: thumbnailUrl
                    if (videoId.isBlank() && !result.videoId.isNullOrBlank()) {
                        videoId = result.videoId
                    }
                }
                else -> skip(parser)
            }
        }
        if (videoId.isBlank()) {
            videoId = extractVideoIdFromUrl(link).orEmpty()
        }
        videoId = extractVideoIdFromText(videoId).orEmpty()

        return Article(
            sourceId = sourceId,
            title = title,
            content = description,
            mediaUrl = thumbnailUrl,
            videoId = videoId.ifBlank { null },
            url = link,
            publishedAt = if (published == 0L) System.currentTimeMillis() else published,
            viewCount = viewCount
        )
    }

    private fun readMediaGroup(parser: XmlPullParser): MediaGroupData {
        var description = ""
        var viewCount = 0L
        var thumbnailUrl: String? = null
        var videoId: String? = null
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name.localTagName()) {
                "description" -> description = readText(parser)
                "content" -> {
                    val url = parser.getAttributeValue(null, "url")
                    if (!url.isNullOrBlank() && videoId.isNullOrBlank()) {
                        videoId = extractVideoIdFromUrl(url)
                    }
                    skip(parser)
                }
                "thumbnail" -> {
                    val url = parser.getAttributeValue(null, "url")
                    if (!url.isNullOrBlank()) {
                        thumbnailUrl = url
                        if (videoId.isNullOrBlank()) {
                            videoId = extractVideoIdFromUrl(url)
                        }
                    }
                    skip(parser)
                }
                "statistics" -> {
                    viewCount = parser.getAttributeValue(null, "views")?.toLongOrNull() ?: 0L
                    skip(parser)
                }
                "community" -> {
                    viewCount = readMediaCommunity(parser, viewCount)
                }
                else -> skip(parser)
            }
        }
        return MediaGroupData(
            description = description,
            viewCount = viewCount,
            thumbnailUrl = thumbnailUrl,
            videoId = videoId
        )
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    private val formattersThreadLocal = ThreadLocal.withInitial {
        listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        )
    }

    private fun parseDate(dateString: String): Long {
        val formatters = formattersThreadLocal.get()
        for (f in formatters) {
            val date = runCatching { f.parse(dateString) }.getOrNull()
            if (date != null) return date.time
        }
        return 0L
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) throw IllegalStateException()
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    private fun extractVideoIdFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val vParam = Regex("[?&]v=([^?&#]+)").find(url)?.groupValues?.get(1)
        if (!vParam.isNullOrBlank()) return extractVideoIdFromText(vParam)
        val shorts = Regex("youtube\\.com/shorts/([^?&#]+)").find(url)?.groupValues?.get(1)
        if (!shorts.isNullOrBlank()) return extractVideoIdFromText(shorts)
        val embed = Regex("youtube\\.com/embed/([^?&#]+)").find(url)?.groupValues?.get(1)
        if (!embed.isNullOrBlank()) return extractVideoIdFromText(embed)
        val legacy = Regex("youtube\\.com/v/([^?&#]+)").find(url)?.groupValues?.get(1)
        if (!legacy.isNullOrBlank()) return extractVideoIdFromText(legacy)
        val thumb = Regex("ytimg\\.com/vi/([^/]+)/").find(url)?.groupValues?.get(1)
        if (!thumb.isNullOrBlank()) return extractVideoIdFromText(thumb)
        val short = Regex("youtu\\.be/([^?&#]+)").find(url)?.groupValues?.get(1)
        return extractVideoIdFromText(short)
    }

    private fun extractVideoIdFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = Regex("[A-Za-z0-9_-]{11}").find(text)
        return match?.value
    }

    private fun readMediaCommunity(parser: XmlPullParser, currentViewCount: Long): Long {
        var viewCount = currentViewCount
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name.localTagName()) {
                "statistics" -> {
                    viewCount = parser.getAttributeValue(null, "views")?.toLongOrNull() ?: viewCount
                    skip(parser)
                }
                else -> skip(parser)
            }
        }
        return viewCount
    }

    private fun String.localTagName(): String = substringAfter(':')

    private fun isShortsArticle(article: Article): Boolean {
        val url = article.url.lowercase()
        if (url.contains("/shorts/")) return true

        val title = article.title.lowercase()
        if (title.contains("#shorts")) return true

        val content = article.content.lowercase()
        if (content.contains("#shorts")) return true

        return false
    }

    private data class MediaGroupData(
        val description: String,
        val viewCount: Long,
        val thumbnailUrl: String?,
        val videoId: String?
    )
}






