package com.andrewwin.sumup.data.remote

import com.andrewwin.sumup.data.local.entities.Article
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.text.SimpleDateFormat
import java.util.Locale

class TelegramParser {

    fun parseChannelDisplayName(html: String): String? {
        return Jsoup.parse(html)
            .selectFirst("title")
            ?.text()
            ?.removeTelegramTitleSuffix()
            ?.takeIf { it.isNotBlank() }
            ?.takeUnless { it.equals(TELEGRAM_DEFAULT_TITLE, ignoreCase = true) }
    }

    fun parsePageMetadata(
        html: String,
        oldestAllowedPublishedAt: Long?,
        latestKnownMessageId: Long? = null
    ): TelegramPageMetadata {
        return parsePageMetadata(Jsoup.parse(html), oldestAllowedPublishedAt, latestKnownMessageId)
    }

    fun parsePageMetadata(
        document: Document,
        oldestAllowedPublishedAt: Long?,
        latestKnownMessageId: Long? = null
    ): TelegramPageMetadata {
        return buildPageMetadata(document, oldestAllowedPublishedAt, latestKnownMessageId)
    }

    fun parse(html: String, sourceId: Long, oldestAllowedPublishedAt: Long? = null): List<Article> {
        val document = Jsoup.parse(html)
        return parseDocument(document, sourceId, oldestAllowedPublishedAt)
    }

    fun parse(document: Document, sourceId: Long, oldestAllowedPublishedAt: Long? = null): List<Article> {
        return parseDocument(document, sourceId, oldestAllowedPublishedAt)
    }

    fun scanPage(
        document: Document,
        sourceId: Long,
        oldestAllowedPublishedAt: Long?,
        latestKnownMessageId: Long?
    ): TelegramPageScanResult {
        val startedAt = NewsParsingLogger.now()
        val partsByKey = linkedMapOf<String, MessageParts>()
        val messages = document.select(".tgme_widget_message")
        var skippedByCutoff = 0
        var skippedByBlankKey = 0
        var skippedByBlankText = 0
        var skippedByBlankUrl = 0
        var oldestPublishedAt: Long? = null
        var newestPublishedAt: Long? = null
        var oldestMessageId: Long? = null
        var newestMessageId: Long? = null
        var hasRelevantMessage = oldestAllowedPublishedAt == null
        var hasMessageNewerThanKnown = latestKnownMessageId == null

        messages.forEach { message ->
            val dateLink = message.selectFirst(".tgme_widget_message_date")
            val timeNode = dateLink?.selectFirst("time") ?: message.selectFirst("time")
            val publishedAt = extractPublishedAtFast(message, dateLink, timeNode)
            if (publishedAt != null) {
                oldestPublishedAt = minOf(oldestPublishedAt ?: publishedAt, publishedAt)
                newestPublishedAt = maxOf(newestPublishedAt ?: publishedAt, publishedAt)
                if (oldestAllowedPublishedAt != null && publishedAt >= oldestAllowedPublishedAt) {
                    hasRelevantMessage = true
                }
            }

            val key = buildKeyFast(message, dateLink)
            val messageId = extractMessageIdFast(key)
            if (messageId != null) {
                oldestMessageId = minOf(oldestMessageId ?: messageId, messageId)
                newestMessageId = maxOf(newestMessageId ?: messageId, messageId)
                if (latestKnownMessageId != null && messageId > latestKnownMessageId) {
                    hasMessageNewerThanKnown = true
                }
            }

            if (publishedAt == null) return@forEach
            if (oldestAllowedPublishedAt != null && publishedAt < oldestAllowedPublishedAt) {
                skippedByCutoff++
                return@forEach
            }
            if (latestKnownMessageId != null && messageId != null && messageId <= latestKnownMessageId) {
                return@forEach
            }
            if (key.isBlank()) {
                skippedByBlankKey++
                return@forEach
            }

            val text = extractMessageText(message)
            if (text.isBlank()) {
                skippedByBlankText++
                return@forEach
            }

            val normalizedUrl = normalizeMessageUrlFast(dateLink)
            if (normalizedUrl.isBlank()) {
                skippedByBlankUrl++
                return@forEach
            }

            val parts = partsByKey.getOrPut(key) {
                MessageParts(
                    key = key,
                    publishedAt = publishedAt,
                    url = normalizedUrl
                )
            }

            parts.publishedAt = minOf(parts.publishedAt ?: publishedAt, publishedAt)
            parts.url = parts.url?.takeIf { it.isNotBlank() } ?: normalizedUrl
            parts.text = mergeMessageText(parts.text, text)

            val viewCount = parseViewCount(
                message.selectFirst(".tgme_widget_message_views")?.text()
            )
            if (viewCount > 0L) {
                parts.viewCount = maxOf(parts.viewCount ?: 0L, viewCount)
            }

            if (parts.mediaUrl.isNullOrBlank()) {
                parts.mediaUrl = extractMediaUrl(message)
            }
        }

        val articles = buildArticlesFromParts(sourceId, partsByKey.values)
        val metadata = TelegramPageMetadata(
            messageCount = messages.size,
            oldestPublishedAt = oldestPublishedAt,
            newestPublishedAt = newestPublishedAt,
            oldestMessageId = oldestMessageId,
            newestMessageId = newestMessageId,
            nextBeforeCursor = findNextBeforeCursor(document),
            hasRelevantMessage = hasRelevantMessage,
            hasMessageNewerThanKnown = hasMessageNewerThanKnown
        )
        NewsParsingLogger.telegramParserProfile(
            messageCount = messages.size,
            skippedByCutoff = skippedByCutoff,
            skippedByBlankKey = skippedByBlankKey,
            skippedByBlankText = skippedByBlankText,
            skippedByBlankUrl = skippedByBlankUrl,
            articleCount = articles.size,
            durationMs = NewsParsingLogger.elapsedMs(startedAt)
        )
        return TelegramPageScanResult(
            articles = articles,
            metadata = metadata,
            nextPageCursor = metadata.nextBeforeCursor ?: metadata.oldestMessageId?.toString()
        )
    }

    private fun parseDocument(
        document: Document,
        sourceId: Long,
        oldestAllowedPublishedAt: Long?
    ): List<Article> {
        val startedAt = NewsParsingLogger.now()
        val partsByKey = linkedMapOf<String, MessageParts>()
        val messageCount = document.select(".tgme_widget_message").size
        var skippedByCutoff = 0
        var skippedByBlankKey = 0
        var skippedByBlankText = 0
        var skippedByBlankUrl = 0

        document.select(".tgme_widget_message").forEach { element ->
            val publishedAt = extractPublishedAt(element) ?: return@forEach
            if (oldestAllowedPublishedAt != null && publishedAt < oldestAllowedPublishedAt) {
                skippedByCutoff++
                return@forEach
            }

            val key = buildKey(element)
            if (key.isBlank()) {
                skippedByBlankKey++
                return@forEach
            }

            val text = extractMessageText(element)
            if (text.isBlank()) {
                skippedByBlankText++
                return@forEach
            }

            val normalizedUrl = normalizeMessageUrl(element)
            if (normalizedUrl.isBlank()) {
                skippedByBlankUrl++
                return@forEach
            }

            val parts = partsByKey.getOrPut(key) {
                MessageParts(
                    key = key,
                    publishedAt = publishedAt,
                    url = normalizedUrl
                )
            }

            parts.publishedAt = minOf(parts.publishedAt ?: publishedAt, publishedAt)
            parts.url = parts.url?.takeIf { it.isNotBlank() } ?: normalizedUrl
            parts.text = mergeMessageText(parts.text, text)

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

        val articles = buildArticlesFromParts(sourceId, partsByKey.values)
            .distinctBy { it.url }
            .sortedByDescending { it.publishedAt }
        NewsParsingLogger.telegramParserProfile(
            messageCount = messageCount,
            skippedByCutoff = skippedByCutoff,
            skippedByBlankKey = skippedByBlankKey,
            skippedByBlankText = skippedByBlankText,
            skippedByBlankUrl = skippedByBlankUrl,
            articleCount = articles.size,
            durationMs = NewsParsingLogger.elapsedMs(startedAt)
        )
        return articles
    }

    private fun buildPageMetadata(
        document: Document,
        oldestAllowedPublishedAt: Long?,
        latestKnownMessageId: Long?
    ): TelegramPageMetadata {
        val messages = document.select(".tgme_widget_message")
        if (messages.isEmpty()) {
            return TelegramPageMetadata(
                messageCount = 0,
                nextBeforeCursor = findNextBeforeCursor(document),
                hasMessageNewerThanKnown = latestKnownMessageId == null
            )
        }

        var oldestPublishedAt: Long? = null
        var newestPublishedAt: Long? = null
        var oldestMessageId: Long? = null
        var newestMessageId: Long? = null
        var hasRelevantMessage = oldestAllowedPublishedAt == null
        var hasMessageNewerThanKnown = latestKnownMessageId == null

        messages.forEach { message ->
            val publishedAt = extractPublishedAt(message)
            if (publishedAt != null) {
                oldestPublishedAt = minOf(oldestPublishedAt ?: publishedAt, publishedAt)
                newestPublishedAt = maxOf(newestPublishedAt ?: publishedAt, publishedAt)
                if (oldestAllowedPublishedAt != null && publishedAt >= oldestAllowedPublishedAt) {
                    hasRelevantMessage = true
                }
            }

            val messageId = extractMessageId(message)
            if (messageId != null) {
                oldestMessageId = minOf(oldestMessageId ?: messageId, messageId)
                newestMessageId = maxOf(newestMessageId ?: messageId, messageId)
                if (latestKnownMessageId != null && messageId > latestKnownMessageId) {
                    hasMessageNewerThanKnown = true
                }
            }
        }

        return TelegramPageMetadata(
            messageCount = messages.size,
            oldestPublishedAt = oldestPublishedAt,
            newestPublishedAt = newestPublishedAt,
            oldestMessageId = oldestMessageId,
            newestMessageId = newestMessageId,
            nextBeforeCursor = findNextBeforeCursor(document),
            hasRelevantMessage = hasRelevantMessage,
            hasMessageNewerThanKnown = hasMessageNewerThanKnown
        )
    }

    private fun buildKey(element: Element): String {
        val byPost = element.attr("data-post")
        if (byPost.isNotBlank()) return byPost
        val byId = element.id()
        if (byId.isNotBlank()) return byId
        val byMessageId = element.attr("data-message-id")
        if (byMessageId.isNotBlank()) return byMessageId
        return element.selectFirst(".tgme_widget_message_date")?.attr("href").orEmpty()
    }

    private fun buildKeyFast(element: Element, dateLink: Element?): String {
        val byPost = element.attr("data-post")
        if (byPost.isNotBlank()) return byPost
        val byId = element.id()
        if (byId.isNotBlank()) return byId
        val byMessageId = element.attr("data-message-id")
        if (byMessageId.isNotBlank()) return byMessageId
        return dateLink?.attr("href").orEmpty()
    }

    private fun extractPublishedAt(element: Element): Long? {
        val linkElement = element.selectFirst(".tgme_widget_message_date")
        val dateElement = linkElement?.selectFirst("time") ?: element.selectFirst("time")
        return parseDate(extractDateTime(element, dateElement))
            ?: parseEpoch(extractEpoch(element, dateElement, linkElement))
    }

    private fun extractPublishedAtFast(
        element: Element,
        linkElement: Element?,
        dateElement: Element?
    ): Long? {
        return parseDate(extractDateTime(element, dateElement))
            ?: parseEpoch(extractEpoch(element, dateElement, linkElement))
    }

    private fun parseDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val normalized = if (value.endsWith("Z")) value.dropLast(1) + "+00:00" else value
        for (format in dateFormattersThreadLocal.get().orEmpty()) {
            runCatching { format.parse(normalized)?.time }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun parseEpoch(value: String?): Long? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val number = raw.toLongOrNull() ?: return null
        return if (raw.length >= 13) number else number * 1000L
    }

    private fun extractDateTime(element: Element, dateElement: Element?): String? {
        return sequenceOf(
            dateElement?.attr("datetime"),
            element.selectFirst("[datetime]")?.attr("datetime")
        ).firstOrNull { !it.isNullOrBlank() }
    }

    private fun extractEpoch(
        element: Element,
        dateElement: Element?,
        linkElement: Element?
    ): String? {
        return sequenceOf(
            dateElement?.attr("data-time"),
            dateElement?.attr("data-timestamp"),
            dateElement?.attr("data-published"),
            linkElement?.attr("data-time"),
            linkElement?.attr("data-timestamp"),
            linkElement?.attr("data-date"),
            linkElement?.attr("data-published"),
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

    private fun normalizeMessageUrl(element: Element): String {
        val rawUrl = element.selectFirst(".tgme_widget_message_date")?.attr("href").orEmpty()
        return normalizeUrl(rawUrl)
    }

    private fun normalizeMessageUrlFast(dateLink: Element?): String {
        return normalizeUrl(dateLink?.attr("href").orEmpty())
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
                } else {
                    url
                }
            }
            else -> url
        }
    }

    private fun extractMessageText(element: Element): String {
        val blocks = linkedSetOf<String>()
        telegramPrimaryContentSelectors.forEach { selector ->
            element.select(selector)
                .filter(::isNonReplyDescendant)
                .map(::extractTextFromContentNode)
                .filter { it.isNotBlank() }
                .forEach(blocks::add)
        }
        if (blocks.isNotEmpty()) {
            return blocks.joinToString("\n\n").trim()
        }

        return element.select(".js-message_reply_text")
            .filter(::isNonReplyDescendant)
            .map(::extractTextFromContentNode)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .trim()
    }

    private fun extractTextFromContentNode(node: Element): String {
        if (node.hasClass(TELEGRAM_POLL_CLASS)) {
            return node.selectFirst(".tgme_widget_message_poll_question")
                ?.text()
                ?.trim()
                .orEmpty()
        }

        val parts = mutableListOf<String>()
        collectRelevantText(node, parts)
        return normalizeCollectedText(parts.joinToString(separator = ""))
    }

    private fun collectRelevantText(node: Node, parts: MutableList<String>) {
        when (node) {
            is TextNode -> parts += node.text()
            is Element -> {
                if (shouldSkipNode(node)) return
                if (node.normalName() == "br") {
                    parts += "\n"
                    return
                }
                node.childNodes().forEach { child -> collectRelevantText(child, parts) }
                if (node.normalName() == "div" || node.normalName() == "p") {
                    parts += "\n"
                }
            }
        }
    }

    private fun shouldSkipNode(node: Element): Boolean {
        return node.classNames().any { it in telegramIgnoredClasses }
    }

    private fun normalizeCollectedText(raw: String): String {
        return raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u00A0', ' ')
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n[ \\t]+"), "\n")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun mergeMessageText(currentText: String?, nextText: String): String {
        if (currentText.isNullOrBlank()) return nextText
        return listOf(currentText, nextText)
            .map { it.trim() }
            .distinct()
            .joinToString(separator = "\n")
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

    private fun dropLeadingQuotedLines(lines: List<String>): List<String> {
        var index = 0
        while (index < lines.size) {
            val line = lines[index].trimStart()
            if (line.startsWith("|") || line.startsWith("│") || line.startsWith("╎")) {
                index++
                continue
            }
            break
        }
        return lines.drop(index)
    }

    private fun extractMessageId(element: Element): Long? {
        return buildKey(element)
            .substringAfterLast("/")
            .toLongOrNull()
            ?.takeIf { it > 0L }
    }

    private fun extractMessageIdFast(key: String): Long? {
        return key
            .substringAfterLast("/")
            .toLongOrNull()
            ?.takeIf { it > 0L }
    }

    private fun buildArticlesFromParts(
        sourceId: Long,
        partsList: Collection<MessageParts>
    ): List<Article> {
        return partsList.mapNotNull { parts ->
            val fullText = parts.text?.trim().orEmpty()
            if (fullText.isBlank()) return@mapNotNull null

            val lines = fullText.split("\n").filter { it.isNotBlank() }
            val cleanedLines = dropLeadingQuotedLines(lines)
            val cleanedText = cleanedLines.joinToString("\n").trim()
            if (cleanedText.isBlank()) return@mapNotNull null

            val titleSource = if (cleanedLines.isNotEmpty()) cleanedLines else lines
            val title = titleSource.firstOrNull()?.trim()?.take(MAX_TITLE_LENGTH).orEmpty()
            if (title.isBlank()) return@mapNotNull null

            val publishedAt = parts.publishedAt ?: return@mapNotNull null
            val url = parts.url?.trim().orEmpty()
            if (url.isBlank()) return@mapNotNull null

            Article(
                stableArticleKey = ArticleStableKeyFactory.buildTelegramKey(
                    sourceId = sourceId,
                    messageKey = parts.key,
                    url = url
                ),
                sourceId = sourceId,
                title = title,
                content = cleanedText,
                mediaUrl = parts.mediaUrl,
                url = url,
                publishedAt = publishedAt,
                viewCount = parts.viewCount ?: 0L
            )
        }
    }

    private fun findNextBeforeCursor(document: Document): String? {
        val dataBefore = document
            .selectFirst(".js-messages_more[data-before], .tme_messages_more[data-before]")
            ?.attr("data-before")
            ?.trim()
        if (!dataBefore.isNullOrBlank()) return dataBefore

        val href = document
            .selectFirst(".js-messages_more[href], .tme_messages_more[href]")
            ?.attr("href")
            .orEmpty()
        return BEFORE_QUERY_REGEX.find(href)?.groupValues?.getOrNull(1)
    }

    private fun isNonReplyDescendant(candidate: Element): Boolean {
        return candidate.parents().none { parent ->
            parent.hasClass("tgme_widget_message_reply") || parent.hasClass("js-message_reply")
        }
    }

    data class TelegramPageMetadata(
        val messageCount: Int,
        val oldestPublishedAt: Long? = null,
        val newestPublishedAt: Long? = null,
        val oldestMessageId: Long? = null,
        val newestMessageId: Long? = null,
        val nextBeforeCursor: String? = null,
        val hasRelevantMessage: Boolean = false,
        val hasMessageNewerThanKnown: Boolean = true
    )

    data class TelegramPageScanResult(
        val articles: List<Article>,
        val metadata: TelegramPageMetadata,
        val nextPageCursor: String?
    )

    private data class MessageParts(
        val key: String,
        var text: String? = null,
        var url: String? = null,
        var publishedAt: Long? = null,
        var viewCount: Long? = null,
        var mediaUrl: String? = null
    )

    private companion object {
        private const val TELEGRAM_POLL_CLASS = "tgme_widget_message_poll"
        private val dateFormattersThreadLocal = ThreadLocal.withInitial {
            listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
            )
        }
        private val telegramPrimaryContentSelectors = listOf(
            ".media_supported_cont .js-message_text:not(.js-message_reply_text)",
            ".media_supported_cont .tgme_widget_message_text:not(.js-message_reply_text)",
            ".tgme_widget_message_poll",
            ".js-message_text:not(.js-message_reply_text)",
            ".tgme_widget_message_text:not(.js-message_reply_text)"
        )
        private val telegramIgnoredClasses = setOf(
            "tgme_widget_message_reply",
            "tgme_widget_message_forwarded_from",
            "tgme_widget_message_link_preview",
            "tgme_widget_message_game",
            "tgme_widget_message_invoice",
            "message_video_play",
            "message_video_duration",
            "message_photo_wrap",
            "tgme_widget_message_roundvideo",
            "tgme_widget_message_voice",
            "tgme_widget_message_metacontainer",
            "tgme_widget_message_footer",
            "tgme_widget_message_reactions",
            "media_not_supported_cont"
        )
        private val BEFORE_QUERY_REGEX = Regex("[?&]before=(\\d+)")
    }
}

private fun extractMediaUrl(element: Element): String? {
    val mediaElement = findFirstNonReplyDescendant(
        element,
        ".tgme_widget_message_photo_wrap, .tgme_widget_message_video_thumb, .tgme_widget_message_media"
    )
    val style = mediaElement?.attr("style").orEmpty()
    val styleUrl = STYLE_URL_REGEX.find(style)?.groups?.get(1)?.value
    if (!styleUrl.isNullOrBlank()) return styleUrl

    val img = findFirstNonReplyDescendant(
        element,
        ".tgme_widget_message_photo_wrap img, .tgme_widget_message_video_thumb img, .tgme_widget_message_media img"
    )
    val src = img?.attr("src").orEmpty()
    return src.ifBlank { null }
}

private fun findFirstNonReplyDescendant(
    element: Element,
    cssQuery: String
): Element? {
    return element.select(cssQuery).firstOrNull { candidate ->
        candidate.parents().none { it.hasClass("tgme_widget_message_reply") }
    }
}

private fun String.removeTelegramTitleSuffix(): String =
    replace(TELEGRAM_TITLE_SUFFIX_REGEX, "").trim()

private const val TELEGRAM_DEFAULT_TITLE = "Telegram Messenger"
private const val MAX_TITLE_LENGTH = 150
private val STYLE_URL_REGEX = Regex("url\\(['\\\"]?(.*?)['\\\"]?\\)")
private val TELEGRAM_TITLE_SUFFIX_REGEX = Regex("\\s+[–-]\\s*Telegram\\s*$", RegexOption.IGNORE_CASE)
