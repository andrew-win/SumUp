package com.andrewwin.sumup.data.remote

import android.net.Uri
import android.os.SystemClock
import android.util.Log

object NewsParsingLogger {
    private const val TAG = "NewsParsingDebug"
    private const val IS_ENABLED = true
    private const val MAX_SAFE_PATH_LENGTH = 80

    fun now(): Long = SystemClock.elapsedRealtime()

    fun elapsedMs(startedAt: Long): Long = now() - startedAt

    fun debug(message: () -> String) {
        if (IS_ENABLED) {
            Log.d(TAG, message())
        }
    }

    fun warning(message: () -> String) {
        if (IS_ENABLED) {
            Log.w(TAG, message())
        }
    }

    fun error(throwable: Throwable, message: () -> String) {
        if (IS_ENABLED) {
            Log.w(TAG, message(), throwable)
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
}
