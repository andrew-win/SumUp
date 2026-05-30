package com.andrewwin.sumup.data.remote.sources

import android.net.Uri

object ArticleStableKeyFactory {

    fun buildRssKey(sourceId: Long, guid: String, url: String): String =
        buildKey(
            prefix = "rss",
            sourceId = sourceId,
            rawIdentity = normalizedRssIdentity(url).ifBlank {
                guid.trim().ifBlank { normalizedUrlIdentity(url) }
            }
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
            rawIdentity = videoId.trim().ifBlank { normalizedUrlIdentity(url) }
        )

    fun buildSavedKey(url: String): String =
        "saved:${normalizedUrlIdentity(url)}"

    private fun buildKey(prefix: String, sourceId: Long, rawIdentity: String): String =
        "$prefix:$sourceId:${rawIdentity.trim()}"

    private fun normalizedRssIdentity(url: String): String {
        val value = url.trim()
        if (value.isBlank()) return ""

        return runCatching {
            val uri = Uri.parse(value)
            val host = uri.host?.lowercase().orEmpty()
            val normalizedPath = uri.path.orEmpty().trimEnd('/').ifBlank { "/" }
            val filteredQuery = uri.queryParameterNames
                .asSequence()
                .filterNot { key ->
                    key.startsWith("utm_") ||
                        key == "fbclid" ||
                        key == "gclid"
                }
                .sorted()
                .flatMap { key ->
                    uri.getQueryParameters(key)
                        .asSequence()
                        .map { queryValue -> key to queryValue.trim() }
                        .filter { (_, queryValue) -> queryValue.isNotEmpty() }
                }
                .joinToString("&") { (key, queryValue) -> "$key=$queryValue" }

            buildString {
                append(host)
                append(normalizedPath)
                if (filteredQuery.isNotBlank()) {
                    append("?")
                    append(filteredQuery.lowercase())
                }
            }
        }.getOrDefault(normalizedUrlIdentity(url))
    }

    private fun normalizedUrlIdentity(url: String): String =
        url.trim()
            .substringBefore("#")
            .substringBefore("?")
            .removeSuffix("/")
            .lowercase()
}
