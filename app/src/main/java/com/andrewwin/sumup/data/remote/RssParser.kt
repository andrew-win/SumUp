package com.andrewwin.sumup.data.remote

import android.util.Xml
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.TextCleaner
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class RssParser {
    private val rssDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
    private val atomDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    fun parse(inputStream: InputStream, sourceId: Long): List<Article> {
        inputStream.use {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)
            try {
                parser.nextTag()
            } catch (e: Exception) {
                return emptyList()
            }
            
            return when (parser.name) {
                "rss" -> readRss(parser, sourceId)
                "feed" -> readAtom(parser, sourceId)
                else -> readGeneric(parser, sourceId)
            }
        }
    }

    private fun readGeneric(parser: XmlPullParser, sourceId: Long): List<Article> {
        val articles = mutableListOf<Article>()
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "item" -> articles.add(readItem(parser, sourceId))
                    "entry" -> articles.add(readAtomEntry(parser, sourceId))
                }
            }
        } catch (e: Exception) { }
        return articles
    }

    private fun readRss(parser: XmlPullParser, sourceId: Long): List<Article> {
        val articles = mutableListOf<Article>()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "channel") {
                articles.addAll(readChannel(parser, sourceId))
            } else {
                skip(parser)
            }
        }
        return articles
    }

    private fun readChannel(parser: XmlPullParser, sourceId: Long): List<Article> {
        val articles = mutableListOf<Article>()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "item") {
                articles.add(readItem(parser, sourceId))
            } else {
                skip(parser)
            }
        }
        return articles
    }

    private fun readItem(parser: XmlPullParser, sourceId: Long): Article {
        var title = ""
        var link = ""
        var description = ""
        var pubDate = 0L

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "title" -> title = TextCleaner.clean(readText(parser))
                "link" -> link = readText(parser)
                "description" -> description = TextCleaner.clean(readText(parser))
                "pubDate" -> pubDate = parseRssDate(readText(parser))
                else -> skip(parser)
            }
        }
        return Article(
            sourceId = sourceId,
            title = title,
            content = description,
            url = link,
            publishedAt = if (pubDate == 0L) System.currentTimeMillis() else pubDate
        )
    }

    private fun readAtom(parser: XmlPullParser, sourceId: Long): List<Article> {
        val articles = mutableListOf<Article>()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "entry") {
                articles.add(readAtomEntry(parser, sourceId))
            } else {
                skip(parser)
            }
        }
        return articles
    }

    private fun readAtomEntry(parser: XmlPullParser, sourceId: Long): Article {
        var title = ""
        var link = ""
        var summary = ""
        var published = 0L

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "title" -> title = TextCleaner.clean(readText(parser))
                "link" -> {
                    link = parser.getAttributeValue(null, "href") ?: ""
                    parser.nextTag()
                }
                "summary", "content" -> summary = TextCleaner.clean(readText(parser))
                "published", "updated" -> published = parseAtomDate(readText(parser))
                else -> skip(parser)
            }
        }
        return Article(
            sourceId = sourceId,
            title = title,
            content = summary,
            url = link,
            publishedAt = if (published == 0L) System.currentTimeMillis() else published
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

    private fun parseRssDate(dateString: String): Long {
        return try { rssDateFormat.parse(dateString)?.time ?: 0L } catch (e: Exception) { 0L }
    }

    private fun parseAtomDate(dateString: String): Long {
        return try { atomDateFormat.parse(dateString.take(19))?.time ?: 0L } catch (e: Exception) { 0L }
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
