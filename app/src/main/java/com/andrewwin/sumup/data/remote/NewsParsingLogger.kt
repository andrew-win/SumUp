package com.andrewwin.sumup.data.remote

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.andrewwin.sumup.data.local.entities.SourceType

object NewsParsingLogger {
    private const val TAG = "NewsParsingDebug"
    private const val IS_ENABLED = true
    private const val MAX_SAFE_PATH_LENGTH = 80

    fun now(): Long = runCatching {
        SystemClock.elapsedRealtime()
    }.getOrElse {
        System.nanoTime() / NANOS_PER_MILLISECOND
    }

    fun elapsedMs(startedAt: Long): Long = now() - startedAt

    fun debug(message: () -> String) {
        if (IS_ENABLED) {
            runCatching { Log.d(TAG, message()) }
        }
    }

    fun warning(message: () -> String) {
        if (IS_ENABLED) {
            runCatching { Log.w(TAG, message()) }
        }
    }

    fun error(throwable: Throwable, message: () -> String) {
        if (IS_ENABLED) {
            runCatching { Log.w(TAG, message(), throwable) }
        }
    }

    fun parseSummary(sourceType: SourceType, channelCount: Int, durationMs: Long) {
        debug {
            "parse_${sourceType.logToken()}: $channelCount channels, ${durationMs}ms"
        }
    }

    fun cleaningSummary(sourceType: SourceType, durationMs: Long) {
        debug {
            "cleaning_content_${sourceType.logToken()}: ${durationMs}ms"
        }
    }

    fun telegramChannelProfile(
        safeUrl: String,
        pageCount: Int,
        totalArticles: Int,
        stopReason: String,
        requestMs: Long,
        bodyReadMs: Long,
        metadataParseMs: Long,
        articleParseMs: Long,
        totalMs: Long
    ) {
        debug {
            "tg_channel_profile: url=$safeUrl pages=$pageCount articles=$totalArticles " +
                "stop=$stopReason request_ms=${requestMs} body_read_ms=${bodyReadMs} " +
                "metadata_parse_ms=${metadataParseMs} article_parse_ms=${articleParseMs} total_ms=${totalMs}"
        }
    }

    fun telegramPageProfile(
        safeUrl: String,
        pageIndex: Int,
        statusCode: Int,
        htmlChars: Int,
        messageCount: Int,
        relevantMessages: Int,
        articlesCount: Int,
        requestMs: Long,
        bodyReadMs: Long,
        documentParseMs: Long,
        metadataParseMs: Long,
        articleParseMs: Long,
        totalMs: Long
    ) {
        debug {
            "tg_page_profile: url=$safeUrl page=$pageIndex status=$statusCode html_chars=$htmlChars " +
            "messages=$messageCount relevant_messages=$relevantMessages articles=$articlesCount " +
                "request_ms=${requestMs} body_read_ms=${bodyReadMs} document_parse_ms=${documentParseMs} " +
                "metadata_parse_ms=${metadataParseMs} " +
                "article_parse_ms=${articleParseMs} total_ms=${totalMs}"
        }
    }

    fun telegramBeforeFlow(
        safeUrl: String,
        pageIndex: Int,
        currentBeforeCursor: String?,
        nextBeforeCursor: String?,
        oldestMessageId: Long?,
        newestMessageId: Long?,
        latestKnownMessageId: Long?,
        nextCursorSource: String,
        shouldFetchOlderPage: Boolean,
        shouldFetchBeforeKnownMessage: Boolean
    ) {
        debug {
            "tg_before_flow: url=$safeUrl page=$pageIndex current_before=${currentBeforeCursor ?: "-"} " +
                "next_before=${nextBeforeCursor ?: "-"} cursor_source=$nextCursorSource " +
                "oldest_message_id=${oldestMessageId ?: 0L} newest_message_id=${newestMessageId ?: 0L} " +
                "latest_known_message_id=${latestKnownMessageId ?: 0L} " +
                "should_fetch_older=$shouldFetchOlderPage should_fetch_before_known=$shouldFetchBeforeKnownMessage"
        }
    }

    fun telegramBeforeStop(
        safeUrl: String,
        pageIndex: Int,
        reason: String,
        currentBeforeCursor: String?,
        nextBeforeCursor: String?,
        oldestMessageId: Long?,
        newestMessageId: Long?,
        latestKnownMessageId: Long?
    ) {
        debug {
            "tg_before_stop: url=$safeUrl page=$pageIndex reason=$reason " +
                "current_before=${currentBeforeCursor ?: "-"} next_before=${nextBeforeCursor ?: "-"} " +
                "oldest_message_id=${oldestMessageId ?: 0L} newest_message_id=${newestMessageId ?: 0L} " +
                "latest_known_message_id=${latestKnownMessageId ?: 0L}"
        }
    }

    fun telegramParserProfile(
        messageCount: Int,
        skippedByCutoff: Int,
        skippedByBlankKey: Int,
        skippedByBlankText: Int,
        skippedByBlankUrl: Int,
        articleCount: Int,
        durationMs: Long
    ) {
        debug {
            "tg_parser_profile: messages=$messageCount skipped_cutoff=$skippedByCutoff " +
                "skipped_blank_key=$skippedByBlankKey skipped_blank_text=$skippedByBlankText " +
                "skipped_blank_url=$skippedByBlankUrl articles=$articleCount duration_ms=${durationMs}"
        }
    }

    fun safeUrl(rawUrl: String): String {
        return runCatching {
            val uri = Uri.parse(rawUrl)
            val host = uri.host.orEmpty().ifBlank { "unknown-host" }
            val path = uri.path.orEmpty()
                .trimEnd('/')
                .ifBlank { "/" }
                .take(MAX_SAFE_PATH_LENGTH)
            val queryKeys = uri.queryParameterNames
                .sorted()
                .joinToString(prefix = "?", separator = "&") { key -> "$key=..." }
                .takeIf { uri.queryParameterNames.isNotEmpty() }
                .orEmpty()
            "$host$path$queryKeys"
        }.getOrDefault("invalid-url")
    }

    private fun SourceType.logToken(): String {
        return when (this) {
            SourceType.TELEGRAM -> "tg"
            SourceType.RSS -> "rss"
            SourceType.YOUTUBE -> "yt"
        }
    }

    private const val NANOS_PER_MILLISECOND = 1_000_000L
}
