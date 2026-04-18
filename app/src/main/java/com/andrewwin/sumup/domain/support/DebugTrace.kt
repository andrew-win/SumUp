package com.andrewwin.sumup.domain.support

import android.util.Log

object DebugTrace {
    private const val TAG = "SumUpTrace"
    private const val MAX_PREVIEW = 700
    private const val ENABLED = true

    fun d(event: String, message: String) {
        if (!ENABLED) return
        Log.d(TAG, "[$event] $message")
    }

    fun w(event: String, message: String) {
        if (!ENABLED) return
        Log.w(TAG, "[$event] $message")
    }

    fun e(event: String, message: String, throwable: Throwable? = null) {
        if (!ENABLED) return
        Log.e(TAG, "[$event] $message", throwable)
    }

    fun preview(value: String?, max: Int = MAX_PREVIEW): String {
        val normalized = value
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
        if (normalized.length <= max) return normalized
        return normalized.take(max).trimEnd() + "…"
    }
}
