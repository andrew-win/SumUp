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
        val refs = mutableMapOf<String, SummarySourceRef>()
        val regex = Regex("source_id:\\s*(\\d+)\\s*source_name:\\s*(.+?)\\s*source_url:\\s*(.+?)\\s*title:", RegexOption.DOT_MATCHES_ALL)
        regex.findAll(content).forEach { match ->
            val id = match.groupValues[1]
            val name = match.groupValues[2].trim()
            val url = match.groupValues[3].trim()
            refs[id] = SummarySourceRef(name, url)
        }
        return refs
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
}
