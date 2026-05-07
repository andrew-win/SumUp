package com.andrewwin.sumup.data.remote

object ArticleStableKeyFactory {

    fun buildRssKey(sourceId: Long, guid: String, url: String): String =
        buildKey(
            prefix = "rss",
            sourceId = sourceId,
            rawIdentity = guid.trim().ifBlank { url.trim() }
        )

    fun buildTelegramKey(sourceId: Long, messageKey: String, url: String): String =
        buildKey(
            prefix = "telegram",
            sourceId = sourceId,
            rawIdentity = messageKey.trim().ifBlank { url.trim() }
        )

    fun buildYouTubeKey(sourceId: Long, videoId: String, url: String): String =
        buildKey(
            prefix = "youtube",
            sourceId = sourceId,
            rawIdentity = videoId.trim().ifBlank { url.trim() }
        )

    fun buildSavedKey(url: String): String =
        "saved:${url.trim()}"

    private fun buildKey(prefix: String, sourceId: Long, rawIdentity: String): String =
        "$prefix:$sourceId:${rawIdentity.trim()}"
}
