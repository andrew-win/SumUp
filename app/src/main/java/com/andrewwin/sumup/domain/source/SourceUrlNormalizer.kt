package com.andrewwin.sumup.domain.source

import com.andrewwin.sumup.data.local.entities.SourceType
import java.net.URI

object SourceUrlNormalizer {

    fun normalize(url: String, type: SourceType): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return trimmed

        return when (type) {
            SourceType.RSS -> normalizeRssUrl(trimmed)
            SourceType.TELEGRAM -> normalizeTelegramUrl(trimmed)
            SourceType.YOUTUBE -> normalizeYouTubeUrl(trimmed)
        }
    }

    private fun normalizeRssUrl(url: String): String {
        val withScheme = when {
            url.startsWith("https://", ignoreCase = true) -> url
            url.startsWith("http://", ignoreCase = true) -> "https://${url.removePrefix("http://")}"
            url.startsWith("//") -> "https:$url"
            else -> "https://$url"
        }

        val parsed = parseUri(withScheme) ?: return withScheme
        val host = parsed.host.orEmpty().removePrefix("www.").lowercase()
        val path = parsed.path.orEmpty().trimEnd('/')
        val query = parsed.rawQuery?.takeIf { it.isNotBlank() }
        return buildString {
            append("https://")
            append(host)
            if (path.isNotBlank()) append(path)
            query?.let { append('?').append(it) }
        }.ifBlank { withScheme }
    }

    private fun normalizeTelegramUrl(url: String): String {
        val canonical = when {
            url.startsWith("@") -> buildTelegramCanonicalUrl(url.removePrefix("@"), "")
            isPlainTelegramName(url) -> buildTelegramCanonicalUrl(url, "")
            url.startsWith("tg://resolve", ignoreCase = true) || url.startsWith("tg:resolve", ignoreCase = true) -> {
                val domain = url.substringAfter("domain=", "").substringBefore("&").trim().removePrefix("@")
                val post = url.substringAfter("post=", "").substringBefore("&").trim()
                buildTelegramCanonicalUrl(domain, post)
            }
            else -> {
                val parsed = parseUri(url.withDefaultSchemeForTelegram())
                val host = parsed?.host.orEmpty().removePrefix("www.").lowercase()
                val segments = parsed?.pathSegments().orEmpty()
                if (host == TELEGRAM_SHORT_HOST || host == TELEGRAM_LEGACY_HOST) {
                    when {
                        segments.firstOrNull().equals("s", ignoreCase = true) -> {
                            val domain = segments.getOrNull(1).orEmpty().removePrefix("@")
                            val post = segments.getOrNull(2).orEmpty()
                            buildTelegramCanonicalUrl(domain, post)
                        }
                        segments.firstOrNull().equals("c", ignoreCase = true) -> {
                            val channel = segments.getOrNull(1).orEmpty()
                            val post = segments.getOrNull(2).orEmpty()
                            buildTelegramCanonicalUrl("c/$channel", post)
                        }
                        else -> {
                            val domain = segments.firstOrNull().orEmpty().removePrefix("@")
                            val post = segments.getOrNull(1).orEmpty()
                            buildTelegramCanonicalUrl(domain, post)
                        }
                    }
                } else {
                    url.trimEnd('/').substringBefore('#').substringBefore('?')
                }
            }
        }
        return canonical.trimEnd('/').lowercase()
    }

    private fun buildTelegramCanonicalUrl(domain: String, post: String): String {
        val cleanDomain = domain.trim().removePrefix("@").trim('/')
        val cleanPost = post.trim().trim('/')
        if (cleanDomain.isBlank()) return ""
        return if (cleanPost.isBlank()) "https://t.me/s/$cleanDomain" else "https://t.me/s/$cleanDomain/$cleanPost"
    }

    private fun isPlainTelegramName(value: String): Boolean =
        TELEGRAM_NAME_REGEX.matches(value)

    private fun normalizeYouTubeUrl(url: String): String {
        val normalized = url
            .replace("http://", "https://", ignoreCase = true)
            .replace("https://m.youtube.com/", "https://www.youtube.com/", ignoreCase = true)
            .replace("https://youtube.com/", "https://www.youtube.com/", ignoreCase = true)
            .replace("https://youtu.be/", "https://www.youtube.com/watch?v=", ignoreCase = true)

        val parsed = parseUri(normalized) ?: return normalized.trimEnd('/')
        val host = parsed.host.orEmpty().lowercase()
        val segments = parsed.pathSegments().filter { it.isNotBlank() }
        val videoId = parsed.getQueryParameter("v")?.trim().orEmpty()

        val canonical = when {
            videoId.isNotBlank() -> "https://www.youtube.com/watch?v=$videoId"
            host.contains("youtube.com") && segments == listOf("feeds", "videos.xml") -> {
                val channelId = parsed.getQueryParameter("channel_id").orEmpty()
                if (channelId.isNotBlank()) {
                    "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
                } else {
                    normalized.substringBefore('#').trimEnd('/')
                }
            }
            host.contains("youtube.com") && segments.firstOrNull().equals("channel", ignoreCase = true) && segments.size >= 2 ->
                "https://www.youtube.com/channel/${segments[1]}"
            host.contains("youtube.com") && segments.firstOrNull().equals("playlist", ignoreCase = true) -> {
                val listId = parsed.getQueryParameter("list").orEmpty()
                if (listId.isNotBlank()) "https://www.youtube.com/playlist?list=$listId" else normalized
            }
            host.contains("youtube.com") && segments.firstOrNull()?.startsWith("@") == true ->
                "https://www.youtube.com/${segments.first()}"
            host.contains("youtube.com") && segments.firstOrNull().equals("c", ignoreCase = true) && segments.size >= 2 ->
                "https://www.youtube.com/c/${segments[1]}"
            host.contains("youtube.com") && segments.firstOrNull().equals("user", ignoreCase = true) && segments.size >= 2 ->
                "https://www.youtube.com/user/${segments[1]}"
            host.contains("youtube.com") && segments.firstOrNull().equals("shorts", ignoreCase = true) && segments.size >= 2 ->
                "https://www.youtube.com/shorts/${segments[1]}"
            else -> normalized.substringBefore('#').substringBefore('?').trimEnd('/')
        }

        return canonical.trimEnd('/')
    }

    private fun parseUri(url: String): URI? =
        runCatching { URI(url) }.getOrNull()

    private fun String.withDefaultSchemeForTelegram(): String = when {
        startsWith("https://", ignoreCase = true) -> this
        startsWith("http://", ignoreCase = true) -> this
        startsWith("//") -> "https:$this"
        startsWith("https.", ignoreCase = true) -> "https://${drop("https.".length)}"
        startsWith("http.", ignoreCase = true) -> "https://${drop("http.".length)}"
        else -> "https://$this"
    }

    private fun URI.pathSegments(): List<String> =
        path.orEmpty()
            .split("/")
            .filter { it.isNotBlank() }

    private fun URI.getQueryParameter(name: String): String? =
        rawQuery
            ?.split("&")
            ?.asSequence()
            ?.map { part -> part.substringBefore("=") to part.substringAfter("=", "") }
            ?.firstOrNull { (key, _) -> key == name }
            ?.second

    private const val TELEGRAM_SHORT_HOST = "t.me"
    private const val TELEGRAM_LEGACY_HOST = "telegram.me"
    private val TELEGRAM_NAME_REGEX = Regex("^[A-Za-z0-9_]{1,32}$")
}
