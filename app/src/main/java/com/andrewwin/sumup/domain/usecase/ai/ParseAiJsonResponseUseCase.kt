package com.andrewwin.sumup.domain.usecase.ai

import javax.inject.Inject

class ParseAiJsonResponseUseCase @Inject constructor() {

    fun parseSingle(jsonResponse: String, content: String): SummaryResult.Single {
        val parsed = AiJsonResponseParser.parseSummary(jsonResponse)
        val sourceRefs = extractStructuredSourceRefs(content)
        val result = toSummaryResult(parsed, sourceRefs)
        return if (result is SummaryResult.Single) result else SummaryResult.Single(points = emptyList())
    }

    fun parseCompare(jsonResponse: String, content: String): SummaryResult.Compare {
        val parsed = AiJsonResponseParser.parseCompare(jsonResponse)
        val sourceRefs = extractStructuredSourceRefs(content)
        return toSummaryResult(parsed, sourceRefs)
    }

    fun parseFeed(jsonResponse: String, content: String): SummaryResult.Digest {
        val parsed = AiJsonResponseParser.parseSummary(jsonResponse)
        val sourceRefs = extractStructuredSourceRefs(content)
        val result = toSummaryResult(parsed, sourceRefs)
        return if (result is SummaryResult.Digest) result else SummaryResult.Digest(emptyList())
    }

    fun parseQuestion(jsonResponse: String, content: String, question: String): SummaryResult.QA {
        val parsed = AiJsonResponseParser.parseQa(jsonResponse)
        val sourceRefs = extractStructuredSourceRefs(content)
        return toSummaryResult(parsed, sourceRefs, question)
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
            SummaryResult.Single(
                points = json.items.flatMap { item ->
                    val sources = (item.sourceIds + listOfNotNull(item.sourceId)).mapNotNull { sourceRefs[it] }.distinct()
                    item.bullets.map { SummaryItem(it, sources) }.ifEmpty {
                        listOf(SummaryItem(item.title ?: "", sources))
                    }
                }
            )
        }
    }

    private fun toSummaryResult(json: CompareResponseJson, sourceRefs: Map<String, SummarySourceRef>): SummaryResult.Compare {
        val common = json.commonFacts.map { fact ->
            SummaryItem(
                text = fact.text,
                sources = fact.sourceIds.mapNotNull { sourceRefs[it] }.distinct()
            )
        }
        val unique = json.items.flatMap { item ->
            val sources = listOfNotNull(item.sourceId).mapNotNull { sourceRefs[it] }
            item.uniqueDetails.map { SummaryItem(it, sources) }
        }
        return SummaryResult.Compare(common, unique)
    }

    private fun toSummaryResult(json: QaResponseJson, sourceRefs: Map<String, SummarySourceRef>, question: String): SummaryResult.QA {
        val details = (json.details + json.statements).map { stmt ->
            SummaryItem(
                text = stmt.text,
                sources = stmt.sources.mapNotNull { sourceRefs[it] }.distinct()
            )
        }
        val sources = json.sources.mapNotNull { sourceRefs[it] }.distinct()
        return SummaryResult.QA(
            question = json.question ?: question,
            shortAnswer = json.shortAnswer ?: json.answer ?: "",
            details = details,
            sources = sources
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
