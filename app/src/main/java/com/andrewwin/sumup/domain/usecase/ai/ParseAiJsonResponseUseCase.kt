package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.domain.support.InvalidAiResponseException
import javax.inject.Inject

class ParseAiJsonResponseUseCase @Inject constructor() {

    fun parseSingle(jsonResponse: String, content: String): SummaryResult.Single {
        val parsed = AiJsonResponseParser.parseSummary(jsonResponse)
        val sourceRefs = extractStructuredSourceRefs(content)
        val result = toSummaryResult(parsed, sourceRefs)
        if (result !is SummaryResult.Single || (result.main.isNullOrBlank() && result.points.isEmpty())) {
            throw InvalidAiResponseException()
        }
        return result
    }

    fun parseCompare(jsonResponse: String, content: String): SummaryResult.Compare {
        val parsed = AiJsonResponseParser.parseCompare(jsonResponse)
        val sourceRefs = extractStructuredSourceRefs(content)
        val result = toSummaryResult(parsed, sourceRefs)
        if (result.main.isNullOrBlank() && result.points.isEmpty()) {
            throw InvalidAiResponseException()
        }
        return result
    }

    fun parseFeed(jsonResponse: String, content: String): SummaryResult.Digest {
        val parsed = AiJsonResponseParser.parseSummary(jsonResponse)
        val sourceRefs = extractStructuredSourceRefs(content)
        val result = toSummaryResult(parsed, sourceRefs)
        if (result !is SummaryResult.Digest || result.themes.isEmpty()) {
            throw InvalidAiResponseException()
        }
        return result
    }

    fun parseQuestion(jsonResponse: String, content: String, question: String): SummaryResult.QA {
        val parsed = AiJsonResponseParser.parseQa(jsonResponse)
        val sourceRefs = extractStructuredSourceRefs(content)
        val result = toSummaryResult(parsed, sourceRefs, question)
        if (result.shortAnswer.isBlank() && result.details.isEmpty()) {
            throw InvalidAiResponseException()
        }
        return result
    }

    private fun extractStructuredSourceRefs(content: String): Map<String, SummarySourceRef> {
        val refs = extractCompactSourceRefs(content).toMutableMap()
        val regex = Regex("source_id:\\s*(\\d+)\\s*source_name:\\s*(.+?)\\s*source_url:\\s*(.+?)\\s*title:", RegexOption.DOT_MATCHES_ALL)
        regex.findAll(content).forEach { match ->
            val id = match.groupValues[1]
            val name = match.groupValues[2].trim()
            val url = match.groupValues[3].trim()
            refs[id] = SummarySourceRef(name, url)
        }
        return refs
    }

    private fun extractCompactSourceRefs(content: String): Map<String, SummarySourceRef> {
        return content.lineSequence()
            .filterNot { line -> line.startsWith(COMPACT_PAYLOAD_HEADER_PREFIX) }
            .mapNotNull { line ->
                val columns = line.split(COMPACT_PAYLOAD_SEPARATOR, limit = COMPACT_PAYLOAD_COLUMN_COUNT)
                if (columns.size < COMPACT_PAYLOAD_COLUMN_COUNT) return@mapNotNull null
                val id = columns[COMPACT_ID_INDEX].trim()
                val name = columns[COMPACT_SOURCE_NAME_INDEX].trim()
                val url = columns[COMPACT_SOURCE_URL_INDEX].trim()
                if (id.isBlank() || name.isBlank()) return@mapNotNull null
                id to SummarySourceRef(name = name, url = url)
            }
            .toMap()
    }

    private fun toSummaryResult(json: SummaryResponseJson, sourceRefs: Map<String, SummarySourceRef>): SummaryResult {
        return if (json.themes.isNotEmpty()) {
            SummaryResult.Digest(
                themes = json.themes.map { theme ->
                    DigestTheme(
                        title = theme.title ?: "Тема",
                        summary = theme.summary,
                        items = theme.items.map { item ->
                            SummaryItem(
                                text = item.title ?: "",
                                sources = (item.sourceIds + listOfNotNull(item.sourceId)).mapNotNull { sourceRefs[it] }.distinct()
                            )
                        }
                    )
                }
            )
        } else {
            val main = json.main?.trim().takeUnless { it.isNullOrBlank() }
            val details = json.details.mapNotNull { detail ->
                detail.toSummaryItem(sourceRefs)
            }.withoutMainDuplicate(main)
            val legacyPoints = json.items.flatMap { item ->
                val sources = (item.sourceIds + listOfNotNull(item.sourceId)).mapNotNull { sourceRefs[it] }.distinct()
                item.bullets.map { SummaryItem(it, sources) }.ifEmpty {
                    listOf(SummaryItem(item.title ?: "", sources))
                }
            }.mapNotNull { item ->
                item.takeUnless { it.text.isBlank() }
            }
            val points = details.ifEmpty {
                main?.let { legacyPoints.withoutMainDuplicate(it) } ?: legacyPoints
            }
            SummaryResult.Single(
                main = main ?: legacyPoints.firstOrNull()?.text,
                points = points,
                sources = json.items.flatMap { item ->
                    val sources = (item.sourceIds + listOfNotNull(item.sourceId)).mapNotNull { sourceRefs[it] }.distinct()
                    sources
                }.distinct()
            )
        }
    }

    private fun toSummaryResult(json: CompareResponseJson, sourceRefs: Map<String, SummarySourceRef>): SummaryResult.Compare {
        val fallback = json.fallback?.trim().takeUnless { it.isNullOrBlank() }
        if (fallback != null) return SummaryResult.Compare(main = fallback, points = emptyList())

        val main = json.main?.trim().takeUnless { it.isNullOrBlank() }
        val details = json.details.mapNotNull { fact -> fact.toSummaryItem(sourceRefs) }
            .withoutMainDuplicate(main)
        if (main != null || details.isNotEmpty()) return SummaryResult.Compare(main = main, points = details)

        val currentItems = json.items.mapNotNull { fact -> fact.toSummaryItem(sourceRefs) }
        if (currentItems.isNotEmpty()) return SummaryResult.Compare(
            main = currentItems.firstOrNull()?.text,
            points = currentItems.drop(SummaryLimits.Compare.mainSentences)
        )

        val legacyCommonFallback = json.commonFallback?.trim().takeUnless { it.isNullOrBlank() }
        val legacyUniqueFallback = json.uniqueFallback?.trim().takeUnless { it.isNullOrBlank() }
        val legacyFallbacks = listOfNotNull(legacyCommonFallback, legacyUniqueFallback)
            .map { SummaryItem(text = it) }
        if (legacyFallbacks.isNotEmpty()) return SummaryResult.Compare(
            main = legacyFallbacks.firstOrNull()?.text,
            points = legacyFallbacks.drop(1)
        )

        val legacyItems = (json.commonFacts + json.uniqueFacts)
            .mapNotNull { fact -> fact.toSummaryItem(sourceRefs) }
            .distinctBy { it.text }
        return SummaryResult.Compare(
            main = legacyItems.firstOrNull()?.text,
            points = legacyItems.drop(SummaryLimits.Compare.mainSentences)
        )
    }

    private fun List<SummaryItem>.withoutMainDuplicate(main: String?): List<SummaryItem> {
        val normalizedMain = main?.normalizeForSummaryComparison().orEmpty()
        if (normalizedMain.isBlank()) return this
        return filterNot { item -> item.text.normalizeForSummaryComparison() == normalizedMain }
    }

    private fun String.normalizeForSummaryComparison(): String =
        trim().trimEnd('.', '!', '?').lowercase()

    private fun SummaryDetailJson.toSummaryItem(sourceRefs: Map<String, SummarySourceRef>): SummaryItem? {
        val text = text.trim()
        if (text.isBlank()) return null
        return SummaryItem(
            text = text,
            sources = sourceIds.mapNotNull { sourceRefs[it] }.distinct()
        )
    }

    private fun CompareFactJson.toSummaryItem(sourceRefs: Map<String, SummarySourceRef>): SummaryItem? {
        val text = text.trim()
        if (text.isBlank()) return null
        return SummaryItem(
            text = text,
            sources = sourceIds.mapNotNull { sourceRefs[it] }.distinct()
        )
    }

    private fun toSummaryResult(json: QaResponseJson, sourceRefs: Map<String, SummarySourceRef>, question: String): SummaryResult.QA {
        val details = json.details.mapNotNull { stmt ->
            val text = stmt.text.trim()
            if (text.isBlank()) return@mapNotNull null
            SummaryItem(
                text = text,
                sources = stmt.sourceIds.mapNotNull { sourceRefs[it] }.distinct()
            )
        }
        return SummaryResult.QA(
            question = question,
            shortAnswer = json.shortAnswer ?: "",
            details = details,
            sources = details.flatMap { it.sources }.distinct()
        )
    }

    private companion object {
        const val COMPACT_PAYLOAD_HEADER_PREFIX = "#"
        const val COMPACT_PAYLOAD_SEPARATOR = "|"
        const val COMPACT_PAYLOAD_COLUMN_COUNT = 5
        const val COMPACT_ID_INDEX = 0
        const val COMPACT_SOURCE_NAME_INDEX = 1
        const val COMPACT_SOURCE_URL_INDEX = 2
    }
}
