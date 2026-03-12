package com.andrewwin.sumup.data.remote

import android.util.Xml
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.TextCleaner
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class YouTubeParser {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

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
                articles.add(readEntry(parser, sourceId))
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

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "title" -> title = TextCleaner.clean(readText(parser))
                "link" -> {
                    link = parser.getAttributeValue(null, "href")
                    skip(parser)
                }
                "published" -> published = parseDate(readText(parser))
                "media:group" -> {
                    val result = readMediaGroup(parser)
                    description = result.first
                    viewCount = result.second
                }
                else -> skip(parser)
            }
        }
        return Article(
            sourceId = sourceId,
            title = title,
            content = description,
            url = link,
            publishedAt = if (published == 0L) System.currentTimeMillis() else published,
            viewCount = viewCount
        )
    }

    private fun readMediaGroup(parser: XmlPullParser): Pair<String, Long> {
        var description = ""
        var viewCount = 0L
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "media:description" -> description = TextCleaner.clean(readText(parser))
                "media:statistics" -> {
                    viewCount = parser.getAttributeValue(null, "views")?.toLongOrNull() ?: 0L
                    skip(parser)
                }
                else -> skip(parser)
            }
        }
        return description to viewCount
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    private fun parseDate(dateString: String): Long {
        return try { dateFormat.parse(dateString)?.time ?: 0L } catch (e: Exception) { 0L }
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
}
