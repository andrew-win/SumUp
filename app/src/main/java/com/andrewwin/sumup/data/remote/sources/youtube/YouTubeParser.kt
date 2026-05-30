package com.andrewwin.sumup.data.remote.sources.youtube

import android.util.Xml
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.remote.sources.ArticleStableKeyFactory
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.collections.orEmpty

class YouTubeParser {
    fun parseFeed(
        inputStream: InputStream,
        sourceId: Long,
        oldestAllowedPublishedAt: Long? = null,
        latestKnownVideoId: String? = null
    ): YouTubeFeedParseResult {
        inputStream.use {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)
            parser.nextTag()
            return readFeedParseResult(parser, sourceId, oldestAllowedPublishedAt, latestKnownVideoId)
        }
    }

    fun parse(
        inputStream: InputStream,
        sourceId: Long,
        oldestAllowedPublishedAt: Long? = null
    ): List<Article> {
        inputStream.use {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)
            parser.nextTag()
            return readFeed(parser, sourceId, oldestAllowedPublishedAt)
        }
    }

    fun parseFeedMetadata(
        inputStream: InputStream,
        oldestAllowedPublishedAt: Long?,
        latestKnownVideoId: String?
    ): YouTubeFeedMetadata {
        inputStream.use {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)
            parser.nextTag()
            return readFeedMetadata(parser, oldestAllowedPublishedAt, latestKnownVideoId)
        }
    }

    fun parseChannelDisplayName(inputStream: InputStream): String? {
        inputStream.use {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)
            parser.nextTag()
            return readFeedDisplayName(parser)
        }
    }

    private fun readFeed(
        parser: XmlPullParser,
        sourceId: Long,
        oldestAllowedPublishedAt: Long?
    ): List<Article> {
        val articles = mutableListOf<Article>()
        parser.require(XmlPullParser.START_TAG, null, "feed")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "entry") {
                readEntry(parser, sourceId, oldestAllowedPublishedAt)?.let(articles::add)
            } else {
                skip(parser)
            }
        }
        return articles
    }

    private fun readFeedParseResult(
        parser: XmlPullParser,
        sourceId: Long,
        oldestAllowedPublishedAt: Long?,
        latestKnownVideoId: String?
    ): YouTubeFeedParseResult {
        val articles = mutableListOf<Article>()
        var entryCount = 0
        var oldestPublishedAt: Long? = null
        var newestPublishedAt: Long? = null
        var newestVideoId: String? = null
        var hasRelevantEntry = oldestAllowedPublishedAt == null
        var hasNewerThanKnownEntry = latestKnownVideoId.isNullOrBlank()

        parser.require(XmlPullParser.START_TAG, null, "feed")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "entry") {
                entryCount++
                val entryData = readEntryData(parser, sourceId, oldestAllowedPublishedAt)
                val publishedAt = entryData.publishedAt
                if (publishedAt != null) {
                    oldestPublishedAt = minOf(oldestPublishedAt ?: publishedAt, publishedAt)
                    newestPublishedAt = maxOf(newestPublishedAt ?: publishedAt, publishedAt)
                    if (oldestAllowedPublishedAt != null && publishedAt >= oldestAllowedPublishedAt) {
                        hasRelevantEntry = true
                    }
                }
                if (newestVideoId.isNullOrBlank() && !entryData.videoId.isNullOrBlank()) {
                    newestVideoId = entryData.videoId
                }
                if (!latestKnownVideoId.isNullOrBlank() && !entryData.videoId.isNullOrBlank() && entryData.videoId != latestKnownVideoId) {
                    hasNewerThanKnownEntry = true
                }
                entryData.article?.let(articles::add)
            } else {
                skip(parser)
            }
        }

        return YouTubeFeedParseResult(
            articles = articles,
            metadata = YouTubeFeedMetadata(
                entryCount = entryCount,
                oldestPublishedAt = oldestPublishedAt,
                newestPublishedAt = newestPublishedAt,
                newestVideoId = newestVideoId,
                hasRelevantEntry = hasRelevantEntry,
                hasNewerThanKnownEntry = hasNewerThanKnownEntry
            )
        )
    }

    private fun readFeedMetadata(
        parser: XmlPullParser,
        oldestAllowedPublishedAt: Long?,
        latestKnownVideoId: String?
    ): YouTubeFeedMetadata {
        var entryCount = 0
        var oldestPublishedAt: Long? = null
        var newestPublishedAt: Long? = null
        var newestVideoId: String? = null
        var hasRelevantEntry = oldestAllowedPublishedAt == null
        var hasNewerThanKnownEntry = latestKnownVideoId.isNullOrBlank()

        parser.require(XmlPullParser.START_TAG, null, "feed")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "entry") {
                entryCount++
                val metadata = readEntryMetadata(parser)
                val publishedAt = metadata.publishedAt
                if (publishedAt != null) {
                    oldestPublishedAt = minOf(oldestPublishedAt ?: publishedAt, publishedAt)
                    newestPublishedAt = maxOf(newestPublishedAt ?: publishedAt, publishedAt)
                    if (oldestAllowedPublishedAt != null && publishedAt >= oldestAllowedPublishedAt) {
                        hasRelevantEntry = true
                    }
                }
                if (newestVideoId.isNullOrBlank() && !metadata.videoId.isNullOrBlank()) {
                    newestVideoId = metadata.videoId
                }
                if (!latestKnownVideoId.isNullOrBlank() && !metadata.videoId.isNullOrBlank() && metadata.videoId != latestKnownVideoId) {
                    hasNewerThanKnownEntry = true
                }
            } else {
                skip(parser)
            }
        }

        return YouTubeFeedMetadata(
            entryCount = entryCount,
            oldestPublishedAt = oldestPublishedAt,
            newestPublishedAt = newestPublishedAt,
            newestVideoId = newestVideoId,
            hasRelevantEntry = hasRelevantEntry,
            hasNewerThanKnownEntry = hasNewerThanKnownEntry
        )
    }

    private fun readFeedDisplayName(parser: XmlPullParser): String? {
        var title: String? = null
        var authorName: String? = null
        parser.require(XmlPullParser.START_TAG, null, "feed")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name.localTagName()) {
                "entry" -> skip(parser)
                "title" -> {
                    if (title.isNullOrBlank()) {
                        title = readText(parser).trim().ifBlank { null }
                    } else {
                        skip(parser)
                    }
                }
                "author" -> authorName = readAuthorName(parser) ?: authorName
                else -> skip(parser)
            }
        }
        return title?.takeIf { it.isNotBlank() } ?: authorName?.takeIf { it.isNotBlank() }
    }

    private fun readEntry(
        parser: XmlPullParser,
        sourceId: Long,
        oldestAllowedPublishedAt: Long?
    ): Article? {
        var title = ""
        var link = ""
        var description = ""
        var published = 0L
        var viewCount = 0L
        var videoId = ""
        var thumbnailUrl: String? = null
        var skipHeavyContent = false

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
                "published" -> {
                    published = parseDate(readText(parser))
                    if (oldestAllowedPublishedAt != null && published in 1 until oldestAllowedPublishedAt) {
                        skipHeavyContent = true
                    }
                }
                "media:group" -> {
                    val result = readMediaGroup(parser, skipHeavyContent)
                    if (!skipHeavyContent) {
                        description = result.description
                        viewCount = result.viewCount
                        thumbnailUrl = result.thumbnailUrl ?: thumbnailUrl
                    }
                    if (videoId.isBlank() && !result.videoId.isNullOrBlank()) {
                        videoId = result.videoId
                    }
                }
                else -> skip(parser)
            }
        }

        if (published == 0L || (oldestAllowedPublishedAt != null && published < oldestAllowedPublishedAt)) {
            return null
        }
        if (videoId.isBlank()) {
            videoId = extractVideoIdFromUrl(link).orEmpty()
        }
        videoId = extractVideoIdFromText(videoId).orEmpty()
        if (videoId.isBlank()) return null
        if (link.isBlank()) return null
        if (title.isBlank()) return null

        val article = Article(
            stableArticleKey = ArticleStableKeyFactory.buildYouTubeKey(
                sourceId = sourceId,
                videoId = videoId,
                url = link
            ),
            sourceId = sourceId,
            title = title,
            content = description,
            mediaUrl = thumbnailUrl,
            videoId = videoId,
            url = link,
            publishedAt = published,
            viewCount = viewCount
        )
        return article.takeUnless(::isShortsArticle)
    }

    private fun readEntryMetadata(parser: XmlPullParser): EntryMetadata {
        var videoId: String? = null
        var publishedAt: Long? = null
        var link: String? = null

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "yt:videoId", "videoId" -> videoId = extractVideoIdFromText(readText(parser))
                "id" -> {
                    val value = readText(parser).trim()
                    if (videoId.isNullOrBlank() && value.startsWith("yt:video:")) {
                        videoId = extractVideoIdFromText(value.removePrefix("yt:video:"))
                    }
                }
                "published" -> {
                    val parsed = parseDate(readText(parser))
                    publishedAt = parsed.takeIf { it > 0L }
                }
                "link" -> {
                    link = parser.getAttributeValue(null, "href")
                    skip(parser)
                }
                "media:group" -> {
                    val mediaGroupData = readMediaGroupMetadata(parser)
                    if (videoId.isNullOrBlank()) {
                        videoId = mediaGroupData.videoId
                    }
                }
                else -> skip(parser)
            }
        }

        if (videoId.isNullOrBlank()) {
            videoId = extractVideoIdFromUrl(link)
        }

        return EntryMetadata(
            videoId = videoId,
            publishedAt = publishedAt
        )
    }

    private fun readEntryData(
        parser: XmlPullParser,
        sourceId: Long,
        oldestAllowedPublishedAt: Long?
    ): EntryData {
        var title = ""
        var link = ""
        var description = ""
        var published = 0L
        var viewCount = 0L
        var videoId = ""
        var thumbnailUrl: String? = null
        var skipHeavyContent = false

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
                "published" -> {
                    published = parseDate(readText(parser))
                    if (oldestAllowedPublishedAt != null && published in 1 until oldestAllowedPublishedAt) {
                        skipHeavyContent = true
                    }
                }
                "media:group" -> {
                    val result = readMediaGroup(parser, skipHeavyContent)
                    if (!skipHeavyContent) {
                        description = result.description
                        viewCount = result.viewCount
                        thumbnailUrl = result.thumbnailUrl ?: thumbnailUrl
                    }
                    if (videoId.isBlank() && !result.videoId.isNullOrBlank()) {
                        videoId = result.videoId
                    }
                }
                else -> skip(parser)
            }
        }

        if (published == 0L || (oldestAllowedPublishedAt != null && published < oldestAllowedPublishedAt)) {
            return EntryData(videoId = videoId.ifBlank { null }, publishedAt = published.takeIf { it > 0L })
        }
        if (videoId.isBlank()) {
            videoId = extractVideoIdFromUrl(link).orEmpty()
        }
        videoId = extractVideoIdFromText(videoId).orEmpty()
        if (videoId.isBlank() || link.isBlank() || title.isBlank()) {
            return EntryData(videoId = videoId.ifBlank { null }, publishedAt = published)
        }

        val article = Article(
            stableArticleKey = ArticleStableKeyFactory.buildYouTubeKey(
                sourceId = sourceId,
                videoId = videoId,
                url = link
            ),
            sourceId = sourceId,
            title = title,
            content = description,
            mediaUrl = thumbnailUrl,
            videoId = videoId,
            url = link,
            publishedAt = published,
            viewCount = viewCount
        ).takeUnless(::isShortsArticle)

        return EntryData(
            article = article,
            videoId = videoId,
            publishedAt = published
        )
    }

    private fun readMediaGroup(skipHeavyContent: Boolean): MediaGroupData {
        error("Unused overload")
    }

    private fun readMediaGroup(parser: XmlPullParser, skipHeavyContent: Boolean): MediaGroupData {
        var description = ""
        var viewCount = 0L
        var thumbnailUrl: String? = null
        var videoId: String? = null
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name.localTagName()) {
                "description" -> {
                    if (skipHeavyContent) {
                        skip(parser)
                    } else {
                        description = readText(parser)
                    }
                }
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
                        if (!skipHeavyContent) {
                            thumbnailUrl = url
                        }
                        if (videoId.isNullOrBlank()) {
                            videoId = extractVideoIdFromUrl(url)
                        }
                    }
                    skip(parser)
                }
                "statistics" -> {
                    if (!skipHeavyContent) {
                        viewCount = parser.getAttributeValue(null, "views")?.toLongOrNull() ?: 0L
                    }
                    skip(parser)
                }
                "community" -> {
                    viewCount = if (skipHeavyContent) {
                        skip(parser)
                        viewCount
                    } else {
                        readMediaCommunity(parser, viewCount)
                    }
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

    private fun readMediaGroupMetadata(parser: XmlPullParser): MediaGroupMetadata {
        var videoId: String? = null
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name.localTagName()) {
                "content", "thumbnail" -> {
                    val url = parser.getAttributeValue(null, "url")
                    if (!url.isNullOrBlank() && videoId.isNullOrBlank()) {
                        videoId = extractVideoIdFromUrl(url)
                    }
                    skip(parser)
                }
                else -> skip(parser)
            }
        }
        return MediaGroupMetadata(videoId = videoId)
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    private fun readAuthorName(parser: XmlPullParser): String? {
        var name: String? = null
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name.localTagName()) {
                "name" -> name = readText(parser).trim().ifBlank { null }
                else -> skip(parser)
            }
        }
        return name
    }

    private fun parseDate(dateString: String): Long {
        val formatters = formattersThreadLocal.get().orEmpty()
        for (formatter in formatters) {
            val date = runCatching { formatter.parse(dateString) }.getOrNull()
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
        val vParam = VIDEO_QUERY_REGEX.find(url)?.groupValues?.get(1)
        if (!vParam.isNullOrBlank()) return extractVideoIdFromText(vParam)
        val shorts = SHORTS_URL_REGEX.find(url)?.groupValues?.get(1)
        if (!shorts.isNullOrBlank()) return extractVideoIdFromText(shorts)
        val embed = EMBED_URL_REGEX.find(url)?.groupValues?.get(1)
        if (!embed.isNullOrBlank()) return extractVideoIdFromText(embed)
        val legacy = LEGACY_VIDEO_URL_REGEX.find(url)?.groupValues?.get(1)
        if (!legacy.isNullOrBlank()) return extractVideoIdFromText(legacy)
        val thumb = THUMBNAIL_URL_REGEX.find(url)?.groupValues?.get(1)
        if (!thumb.isNullOrBlank()) return extractVideoIdFromText(thumb)
        val short = SHORT_URL_REGEX.find(url)?.groupValues?.get(1)
        return extractVideoIdFromText(short)
    }

    private fun extractVideoIdFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return VIDEO_ID_REGEX.find(text)?.value
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

    data class YouTubeFeedMetadata(
        val entryCount: Int,
        val oldestPublishedAt: Long? = null,
        val newestPublishedAt: Long? = null,
        val newestVideoId: String? = null,
        val hasRelevantEntry: Boolean = false,
        val hasNewerThanKnownEntry: Boolean = false
    )

    data class YouTubeFeedParseResult(
        val articles: List<Article>,
        val metadata: YouTubeFeedMetadata
    )

    private data class EntryMetadata(
        val videoId: String?,
        val publishedAt: Long?
    )

    private data class EntryData(
        val article: Article? = null,
        val videoId: String?,
        val publishedAt: Long?
    )

    private data class MediaGroupMetadata(
        val videoId: String?
    )

    private data class MediaGroupData(
        val description: String,
        val viewCount: Long,
        val thumbnailUrl: String?,
        val videoId: String?
    )

    private val formattersThreadLocal = ThreadLocal.withInitial {
        listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        )
    }

    private companion object {
        private val VIDEO_QUERY_REGEX = Regex("[?&]v=([^?&#]+)")
        private val SHORTS_URL_REGEX = Regex("youtube\\.com/shorts/([^?&#]+)")
        private val EMBED_URL_REGEX = Regex("youtube\\.com/embed/([^?&#]+)")
        private val LEGACY_VIDEO_URL_REGEX = Regex("youtube\\.com/v/([^?&#]+)")
        private val THUMBNAIL_URL_REGEX = Regex("ytimg\\.com/vi/([^/]+)/")
        private val SHORT_URL_REGEX = Regex("youtu\\.be/([^?&#]+)")
        private val VIDEO_ID_REGEX = Regex("[A-Za-z0-9_-]{11}")
    }
}