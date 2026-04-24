package com.andrewwin.sumup.domain.usecase.ai

import org.json.JSONArray
import org.json.JSONObject

object AiJsonResponseParser {

    fun extractJson(raw: String): String {
        val trimmed = raw.trim().removePrefix("\uFEFF")
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val fenced = Regex("```(?:json)?\\s*([\\[{][\\s\\S]*[\\]}])\\s*```").find(trimmed)?.groupValues?.get(1)
        if (!fenced.isNullOrBlank()) return fenced.trim()
        extractBalancedJsonFragment(trimmed)?.let { return it }
        return "{}"
    }

    fun parseSummary(raw: String): SummaryResponseJson {
        val obj = JSONObject(extractJson(raw))
        val itemsArray = obj.optJSONArray(AiJsonContract.ITEMS) ?: JSONArray()
        val items = buildList {
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(i) ?: continue
                add(
                    SummaryItemJson(
                        title = item.optString(AiJsonContract.TITLE).ifBlank { null },
                        bullets = readStringArray(item.opt(AiJsonContract.BULLETS)),
                        source = item.optString(AiJsonContract.SOURCE).ifBlank { null },
                        sourceId = item.opt(AiJsonContract.SOURCE_ID)?.toString()?.trim()?.ifBlank { null }
                    )
                )
            }
        }
        val themesArray = obj.optJSONArray(AiJsonContract.THEMES) ?: JSONArray()
        val themes = buildList {
            for (i in 0 until themesArray.length()) {
                val theme = themesArray.optJSONObject(i) ?: continue
                val themeItemsArray = theme.optJSONArray(AiJsonContract.ITEMS) ?: JSONArray()
                val themeItems = buildList {
                    for (j in 0 until themeItemsArray.length()) {
                        val item = themeItemsArray.optJSONObject(j) ?: continue
                        add(
                            SummaryThemeItemJson(
                                title = item.optString(AiJsonContract.TITLE).ifBlank { null },
                                sourceId = item.opt(AiJsonContract.SOURCE_ID)?.toString()?.trim()?.ifBlank { null }
                            )
                        )
                    }
                }
                add(
                    SummaryThemeJson(
                        title = theme.optString(AiJsonContract.TITLE).ifBlank { null },
                        summary = theme.optString(AiJsonContract.SUMMARY).ifBlank { null },
                        emojis = readStringArray(theme.opt(AiJsonContract.EMOJIS)),
                        items = themeItems
                    )
                )
            }
        }
        return SummaryResponseJson(
            headline = obj.optString(AiJsonContract.HEADLINE).ifBlank { null },
            items = items,
            themes = themes
        )
    }

    fun parseQa(raw: String): QaResponseJson {
        val obj = JSONObject(extractJson(raw))
        fun parseQaStatements(value: Any?): List<QaStatementJson> {
            return when (value) {
                is JSONArray -> buildList {
                    for (i in 0 until value.length()) {
                        when (val item = value.opt(i)) {
                            is JSONObject -> {
                                val text = item.optString(AiJsonContract.TEXT).trim()
                                if (text.isBlank()) continue
                                add(
                                    QaStatementJson(
                                        text = text,
                                        sources = readStringArray(item.opt(AiJsonContract.SOURCES))
                                    )
                                )
                            }
                            else -> {
                                val text = readJsonValueAsText(item)
                                if (text.isBlank()) continue
                                add(
                                    QaStatementJson(
                                        text = text,
                                        sources = emptyList()
                                    )
                                )
                            }
                        }
                    }
                }
                is JSONObject -> {
                    val text = value.optString(AiJsonContract.TEXT).trim()
                    if (text.isBlank()) emptyList()
                    else listOf(
                        QaStatementJson(
                            text = text,
                            sources = readStringArray(value.opt(AiJsonContract.SOURCES))
                        )
                    )
                }
                else -> {
                    val text = readJsonValueAsText(value)
                    if (text.isBlank()) emptyList() else listOf(QaStatementJson(text = text))
                }
            }
        }

        val details = parseQaStatements(obj.opt(AiJsonContract.DETAILS))
        val statements = parseQaStatements(obj.opt(AiJsonContract.STATEMENTS))
        return QaResponseJson(
            question = obj.optString(AiJsonContract.QUESTION).ifBlank { null },
            shortAnswer = obj.optString(AiJsonContract.SHORT_ANSWER).ifBlank { null },
            answer = obj.optString(AiJsonContract.ANSWER).ifBlank { null },
            sources = readStringArray(obj.opt(AiJsonContract.SOURCES)),
            details = details,
            statements = statements
        )
    }

    fun parseCompare(raw: String): CompareResponseJson {
        val obj = JSONObject(extractJson(raw))
        val commonFactsArray = obj.optJSONArray(AiJsonContract.COMMON_FACTS) ?: JSONArray()
        val commonFacts = buildList {
            for (i in 0 until commonFactsArray.length()) {
                when (val item = commonFactsArray.opt(i)) {
                    is JSONObject -> {
                        val text = item.optString(AiJsonContract.TEXT).trim()
                        if (text.isBlank()) continue
                        val sourcesOpt = item.opt(AiJsonContract.SOURCES) ?: item.opt("source_ids") ?: item.opt("source_id")
                        add(
                            CompareCommonFactJson(
                                text = text,
                                sourceIds = readStringArray(sourcesOpt)
                            )
                        )
                    }
                    else -> {
                        val text = readJsonValueAsText(item)
                        if (text.isNotBlank()) add(CompareCommonFactJson(text = text))
                    }
                }
            }
        }
        val itemsArray = obj.optJSONArray(AiJsonContract.ITEMS) ?: JSONArray()
        val items = buildList {
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(i) ?: continue
                add(
                    CompareItemJson(
                        sourceId = item.opt(AiJsonContract.SOURCE_ID)?.toString()?.trim()?.ifBlank { null },
                        uniqueDetails = readStringArray(item.opt(AiJsonContract.UNIQUE_DETAILS))
                    )
                )
            }
        }
        return CompareResponseJson(
            commonFacts = commonFacts,
            items = items,
            commonTopic = obj.optString(AiJsonContract.COMMON_TOPIC).ifBlank { null },
            fallbackMessage = obj.optString(AiJsonContract.FALLBACK_MESSAGE).ifBlank { null }
        )
    }

    private fun readStringArray(value: Any?): List<String> {
        return when (value) {
            is JSONArray -> buildList {
                for (i in 0 until value.length()) {
                    val v = readJsonValueAsText(value.opt(i))
                        if (v.isNotBlank()) add(v)
                }
            }
            is String -> listOf(value.trim()).filter { it.isNotBlank() }
            is JSONObject -> listOf(readJsonObjectAsText(value)).filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun extractBalancedJsonFragment(text: String): String? {
        val startIndex = text.indexOfFirst { it == '{' || it == '[' }
        if (startIndex == -1) return null
        val opening = text[startIndex]
        val closing = if (opening == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escaped = false

        for (index in startIndex until text.length) {
            val ch = text[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }

            when (ch) {
                '"' -> inString = true
                opening -> depth++
                closing -> {
                    depth--
                    if (depth == 0) {
                        return text.substring(startIndex, index + 1).trim()
                    }
                }
            }
        }
        return null
    }

    private fun readJsonValueAsText(value: Any?): String {
        return when (value) {
            is String -> value.trim()
            is JSONObject -> readJsonObjectAsText(value)
            is JSONArray -> buildList {
                for (index in 0 until value.length()) {
                    val itemText = readJsonValueAsText(value.opt(index))
                    if (itemText.isNotBlank()) add(itemText)
                }
            }.joinToString(" ").trim()
            null -> ""
            else -> value.toString().trim()
        }
    }

    private fun readJsonObjectAsText(value: JSONObject): String {
        val candidateKeys = listOf(
            AiJsonContract.TEXT,
            "point",
            "bullet",
            AiJsonContract.TITLE,
            AiJsonContract.ANSWER,
            AiJsonContract.COMMON,
            AiJsonContract.DIFFERENT
        )
        candidateKeys.forEach { key ->
            val text = readJsonValueAsText(value.opt(key))
            if (text.isNotBlank()) return text
        }

        val parts = buildList {
            val iterator = value.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                val text = readJsonValueAsText(value.opt(key))
                if (text.isNotBlank()) add(text)
            }
        }
        return parts.joinToString(" ").trim()
    }
}









