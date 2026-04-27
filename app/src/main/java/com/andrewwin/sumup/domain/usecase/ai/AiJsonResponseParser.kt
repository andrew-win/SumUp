package com.andrewwin.sumup.domain.usecase.ai

import kotlinx.serialization.json.Json

object AiJsonResponseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun extractJson(text: String): String {
        val trimmed = text.trim().removePrefix("\uFEFF")

        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return trimmed
        }

        val startIndex = trimmed.indexOfFirst { it == '{' || it == '[' }.takeIf { it != -1 } ?: return "{}"
        val opening = trimmed[startIndex]
        val closing = if (opening == '{') '}' else ']'

        var depth = 0
        var inString = false
        var escaped = false

        for (index in startIndex until trimmed.length) {
            val ch = trimmed[index]
            when {
                escaped -> escaped = false
                ch == '\\' && inString -> escaped = true
                ch == '"' -> inString = !inString
                !inString && ch == opening -> depth++
                !inString && ch == closing -> if (--depth == 0) return trimmed.substring(startIndex, index + 1)
            }
        }
        return "{}"
    }

    private inline fun <reified T> parseJson(raw: String, default: T): T {
        return runCatching {
            json.decodeFromString<T>(extractJson(raw))
        }.getOrDefault(default)
    }

    fun parseSummary(raw: String) = parseJson(raw, SummaryResponseJson())
    fun parseQa(raw: String) = parseJson(raw, QaResponseJson())
    fun parseCompare(raw: String) = parseJson(raw, CompareResponseJson())

}









