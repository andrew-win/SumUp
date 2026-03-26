package com.andrewwin.sumup.domain.usecase.ai

import org.json.JSONArray
import org.json.JSONObject

object AiJsonResponseParser {

    fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val fenced = Regex("```(?:json)?\\s*(\\{[\\s\\S]*\\})\\s*```").find(trimmed)?.groupValues?.get(1)
        if (!fenced.isNullOrBlank()) return fenced.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1).trim()
        return "{}"
    }

    fun parseSummary(raw: String): SummaryResponseJson {
        val obj = JSONObject(extractJson(raw))
        val itemsArray = obj.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(i) ?: continue
                add(
                    SummaryItemJson(
                        title = item.optString("title").ifBlank { null },
                        bullets = readStringArray(item.opt("bullets")),
                        source = item.optString("source").ifBlank { null }
                    )
                )
            }
        }
        return SummaryResponseJson(
            headline = obj.optString("headline").ifBlank { null },
            items = items
        )
    }

    fun parseQa(raw: String): QaResponseJson {
        val obj = JSONObject(extractJson(raw))
        val statementsArray = obj.optJSONArray("statements") ?: JSONArray()
        val statements = buildList {
            for (i in 0 until statementsArray.length()) {
                val item = statementsArray.optJSONObject(i) ?: continue
                val text = item.optString("text").trim()
                if (text.isBlank()) continue
                add(
                    QaStatementJson(
                        text = text,
                        sources = readStringArray(item.opt("sources"))
                    )
                )
            }
        }
        return QaResponseJson(
            answer = obj.optString("answer").ifBlank { null },
            sources = readStringArray(obj.opt("sources")),
            statements = statements
        )
    }

    fun parseCompare(raw: String): CompareResponseJson {
        val obj = JSONObject(extractJson(raw))
        val itemsArray = obj.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(i) ?: continue
                add(
                    CompareItemJson(
                        sourceId = item.opt("source_id")?.toString()?.trim()?.ifBlank { null },
                        common = readStringArray(item.opt("common")),
                        different = readStringArray(item.opt("different"))
                    )
                )
            }
        }
        return CompareResponseJson(
            headline = obj.optString("headline").ifBlank { null },
            items = items
        )
    }

    private fun readStringArray(value: Any?): List<String> {
        return when (value) {
            is JSONArray -> buildList {
                for (i in 0 until value.length()) {
                    val v = value.optString(i).trim()
                    if (v.isNotBlank()) add(v)
                }
            }
            is String -> listOf(value.trim()).filter { it.isNotBlank() }
            else -> emptyList()
        }
    }
}
