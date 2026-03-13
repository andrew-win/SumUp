package com.andrewwin.sumup.data.remote

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.TextCleaner
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class RssParser {
    private val rssDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
    private val atomDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    fun parse(inputStream: InputStream, sourceId: Long): List<Article> {
        inputStream.use {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
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
        var guid = ""
        var description = ""
        var pubDate = 0L

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "title" -> title = TextCleaner.clean(parser.nextText())
                "link" -> link = parser.nextText().trim()
                "guid" -> guid = parser.nextText().trim()
                "description" -> description = TextCleaner.clean(parser.nextText())
                "pubDate" -> pubDate = parseRssDate(parser.nextText())
                else -> skip(parser)
            }
        }

        val rawUrl = if (guid.startsWith("http")) guid else link
        val cleanUrl = rawUrl.substringBefore("#").substringBefore("?")

        return Article(
            sourceId = sourceId,
            title = title,
            content = description,
            url = cleanUrl,
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
                "title" -> title = TextCleaner.clean(parser.nextText())
                "link" -> {
                    val href = parser.getAttributeValue(null, "href") ?: ""
                    val rel = parser.getAttributeValue(null, "rel") ?: ""
                    if (rel == "alternate" || rel.isEmpty() || link.isEmpty()) {
                        link = href
                    }
                    if (!parser.isEmptyElementTag) parser.next()
                }
                "summary", "content" -> summary = TextCleaner.clean(parser.nextText())
                "published", "updated" -> published = parseAtomDate(parser.nextText())
                else -> skip(parser)
            }
        }
        return Article(
            sourceId = sourceId,
            title = title,
            content = summary,
            url = link.substringBefore("#").substringBefore("?"),
            publishedAt = if (published == 0L) System.currentTimeMillis() else published
        )
    }

    private fun parseRssDate(dateString: String): Long {
        return try { rssDateFormat.parse(dateString.trim())?.time ?: 0L } catch (e: Exception) { 0L }
    }

    private fun parseAtomDate(dateString: String): Long {
        return try { atomDateFormat.parse(dateString.trim().take(19))?.time ?: 0L } catch (e: Exception) { 0L }
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
