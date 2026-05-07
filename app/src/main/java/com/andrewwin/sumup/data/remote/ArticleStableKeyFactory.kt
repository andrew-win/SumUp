package com.andrewwin.sumup.data.remote

object ArticleStableKeyFactory {

    fun buildRssKey(sourceId: Long, guid: String, url: String): String =
        buildKey(
            prefix = "rss",
            sourceId = sourceId,
            rawIdentity = normalizedUrlIdentity(url).ifBlank { guid.trim() }
        )

    fun buildTelegramKey(sourceId: Long, messageKey: String, url: String): String =
        buildKey(
            prefix = "telegram",
            sourceId = sourceId,
            rawIdentity = normalizedUrlIdentity(url).ifBlank { messageKey.trim() }
        )

    fun buildYouTubeKey(sourceId: Long, videoId: String, url: String): String =
        buildKey(
            prefix = "youtube",
            sourceId = sourceId,
            rawIdentity = normalizedUrlIdentity(url).ifBlank { videoId.trim() }
        )

    fun buildSavedKey(url: String): String =
        "saved:${normalizedUrlIdentity(url)}"

    private fun buildKey(prefix: String, sourceId: Long, rawIdentity: String): String =
        "$prefix:$sourceId:${rawIdentity.trim()}"

    private fun normalizedUrlIdentity(url: String): String =
        url.trim()
            .substringBefore("#")
            .substringBefore("?")
            .removeSuffix("/")
            .lowercase()
}
