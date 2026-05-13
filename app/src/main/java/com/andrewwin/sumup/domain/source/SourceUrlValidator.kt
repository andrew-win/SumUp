package com.andrewwin.sumup.domain.source

import com.andrewwin.sumup.data.local.entities.SourceType
import java.net.URI

object SourceUrlValidator {

    fun isValid(url: String, type: SourceType): Boolean {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return false

        return when (type) {
            SourceType.RSS -> isValidRssUrl(trimmed)
            SourceType.TELEGRAM -> isValidTelegramUrl(trimmed)
            SourceType.YOUTUBE -> isValidYouTubeChannelId(trimmed)
        }
    }

    private fun isValidRssUrl(url: String): Boolean {
        if (url.startsWith("@") || url.startsWith("UC")) return false
        val parsed = parseUri(url.withDefaultScheme()) ?: return false
        val host = parsed.host.orEmpty().removePrefix("www.").lowercase()
        if (host == TELEGRAM_SHORT_HOST || host == TELEGRAM_LEGACY_HOST) return false
        return HOST_WITH_DOMAIN_REGEX.matches(host)
    }

    private fun isValidTelegramUrl(url: String): Boolean {
        val channelName = extractTelegramChannelName(url) ?: return false
        return isValidTelegramName(channelName)
    }

    private fun extractTelegramChannelName(url: String): String? {
        val trimmed = url.trim().trimEnd('/').substringBefore('#')

        if (trimmed.startsWith("tg://resolve", ignoreCase = true) || trimmed.startsWith("tg:resolve", ignoreCase = true)) {
            return trimmed
                .substringAfter("domain=", "")
                .substringBefore("&")
                .trim()
                .removePrefix("@")
                .takeUnless { it.startsWith(YOUTUBE_CHANNEL_ID_PREFIX) }
        }

        if (trimmed.startsWith("@")) {
            return trimmed
                .removePrefix("@")
                .takeUnless { it.startsWith(YOUTUBE_CHANNEL_ID_PREFIX) }
        }
        if (isPlainTelegramName(trimmed)) return trimmed

        val parsed = parseUri(trimmed.withDefaultSchemeForTelegram()) ?: return null
        val host = parsed.host.orEmpty().removePrefix("www.").lowercase()
        if (host == TELEGRAM_SHORT_HOST || host == TELEGRAM_LEGACY_HOST) {
            return extractTelegramChannelNameFromPath(parsed.path.orEmpty())
        }
        return null
    }

    private fun extractTelegramChannelNameFromPath(path: String): String? {
        val segments = path.split("/").filter { it.isNotBlank() }
        return when {
            segments.firstOrNull().equals("s", ignoreCase = true) -> segments.getOrNull(1)
            segments.firstOrNull().equals("c", ignoreCase = true) -> segments.getOrNull(1)
            else -> segments.firstOrNull()
        }?.removePrefix("@")?.takeUnless { it.startsWith(YOUTUBE_CHANNEL_ID_PREFIX) }
    }

    private fun isValidYouTubeChannelId(url: String): Boolean =
        url.startsWith(YOUTUBE_CHANNEL_ID_PREFIX) && YOUTUBE_CHANNEL_ID_REGEX.matches(url)

    private fun String.withDefaultScheme(): String = when {
        startsWith("https://", ignoreCase = true) -> this
        startsWith("http://", ignoreCase = true) -> this
        startsWith("//") -> "https:$this"
        else -> "https://$this"
    }

    private fun String.withDefaultSchemeForTelegram(): String = when {
        startsWith("https://", ignoreCase = true) -> this
        startsWith("http://", ignoreCase = true) -> this
        startsWith("//") -> "https:$this"
        startsWith("https.", ignoreCase = true) -> "https://${drop("https.".length)}"
        startsWith("http.", ignoreCase = true) -> "https://${drop("http.".length)}"
        else -> "https://$this"
    }

    private fun parseUri(url: String): URI? =
        runCatching { URI(url) }.getOrNull()

    private fun isPlainTelegramName(name: String): Boolean =
        TELEGRAM_NAME_REGEX.matches(name) && !name.startsWith(YOUTUBE_CHANNEL_ID_PREFIX)

    private fun isValidTelegramName(name: String): Boolean =
        TELEGRAM_NAME_REGEX.matches(name)

    private const val TELEGRAM_SHORT_HOST = "t.me"
    private const val TELEGRAM_LEGACY_HOST = "telegram.me"
    private const val YOUTUBE_CHANNEL_ID_PREFIX = "UC"

    private val HOST_WITH_DOMAIN_REGEX = Regex("^[a-z0-9][a-z0-9-]*(\\.[a-z0-9][a-z0-9-]*)+$")
    private val TELEGRAM_NAME_REGEX = Regex("^[A-Za-z0-9_]{1,32}$")
    private val YOUTUBE_CHANNEL_ID_REGEX = Regex("^UC[A-Za-z0-9_-]{20,}$")
}
