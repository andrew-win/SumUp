package com.andrewwin.sumup

import com.andrewwin.sumup.data.ai.AiSummaryResponseMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiJsonResponseParserContractsTest {

    private val parser = AiSummaryResponseMapper()

    @Test
    fun parseSingle_usesMainAndDetailsSchema() {
        val result = parser.parseSingle(
            jsonResponse = """
                {
                  "main": "Core single-article essence.",
                  "details": [
                    { "text": "Supported single detail.", "source_ids": ["1"] }
                  ]
                }
            """.trimIndent(),
            content = structuredContent()
        )

        assertEquals("Core single-article essence.", result.main)
        assertEquals(listOf("Supported single detail."), result.points.map { it.text })
        assertEquals(listOf("Source One"), result.points.first().sources.map { it.name })
    }

    @Test
    fun parseCompare_usesMainAndDetailsSchema() {
        val result = parser.parseCompare(
            jsonResponse = """
                {
                  "main": "Core comparison essence.",
                  "details": [
                    { "text": "Shared confirmed fact.", "source_ids": ["1", "2"] },
                    { "text": "First source detail.", "source_ids": ["1"] },
                    { "text": "Point without known source.", "source_ids": ["unknown"] }
                  ],
                  "fallback": null
                }
            """.trimIndent(),
            content = structuredContent()
        )

        assertEquals("Core comparison essence.", result.main)
        assertEquals(3, result.points.size)
        assertEquals("Shared confirmed fact.", result.points[0].text)
        assertEquals(listOf("Source One", "Source Two"), result.points[0].sources.map { it.name })
        assertEquals("First source detail.", result.points[1].text)
        assertEquals(listOf("Source One"), result.points[1].sources.map { it.name })
        assertEquals("Point without known source.", result.points[2].text)
        assertTrue(result.points[2].sources.isEmpty())
    }

    @Test
    fun parseCompare_usesFallbackInsteadOfItems() {
        val result = parser.parseCompare(
            jsonResponse = """
                {
                  "items": [
                    { "text": "Ignored fact.", "source_ids": ["1", "2"] }
                  ],
                  "fallback": "No meaningful points."
                }
            """.trimIndent(),
            content = structuredContent()
        )

        assertEquals("No meaningful points.", result.main)
        assertTrue(result.points.isEmpty())
    }

    @Test
    fun parseCompare_mergesLegacyCommonAndUniqueFacts() {
        val result = parser.parseCompare(
            jsonResponse = """
                {
                  "common_facts": [
                    { "text": "Shared confirmed fact.", "source_ids": ["1", "2"] }
                  ],
                  "unique_facts": [
                    { "text": "First source exclusive detail.", "source_ids": ["1"] }
                  ],
                  "common_fallback": null,
                  "unique_fallback": null
                }
            """.trimIndent(),
            content = structuredContent()
        )

        assertEquals("Shared confirmed fact.", result.main)
        assertEquals(2, result.points.size)
        assertEquals("Shared confirmed fact.", result.points[0].text)
        assertEquals(listOf("Source One", "Source Two"), result.points[0].sources.map { it.name })
        assertEquals("First source exclusive detail.", result.points[1].text)
        assertEquals(listOf("Source One"), result.points[1].sources.map { it.name })
    }

    @Test
    fun parseQuestion_mapsDetailsSourceIds() {
        val result = parser.parseQuestion(
            jsonResponse = """
                {
                  "short_answer": "Short answer.",
                  "details": [
                    { "text": "Supported detail.", "source_ids": ["2"] }
                  ],
                  "answer": "Legacy answer.",
                  "statements": [
                    { "text": "Legacy statement.", "sources": ["1"] }
                  ]
                }
            """.trimIndent(),
            content = structuredContent(),
            question = "What happened?"
        )

        assertEquals("What happened?", result.question)
        assertEquals("Short answer.", result.shortAnswer)
        assertEquals(listOf("Supported detail."), result.details.map { it.text })
        assertEquals(listOf("Source Two"), result.details.first().sources.map { it.name })
        assertEquals(listOf("Source Two"), result.sources.map { it.name })
    }

    private fun structuredContent(): String {
        return """
            source_id: 1
            source_name: Source One
            source_url: https://example.com/one
            title: First title
            content: First content

            source_id: 2
            source_name: Source Two
            source_url: https://example.com/two
            title: Second title
            content: Second content
        """.trimIndent()
    }
}
