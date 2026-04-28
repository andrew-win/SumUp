package com.andrewwin.sumup

import com.andrewwin.sumup.domain.usecase.ai.GetExtractiveSummaryUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractiveSummarizerSentenceCountTest {
    @Test
    fun extractiveSummarizer_summarize_returnsRequestedSentenceCount() {
        val text = """
            First long sentence with important context about the market and users.
            Second long sentence with additional facts and practical impact.
            Third long sentence with implementation details and expected outcomes.
            Fourth long sentence that should usually be ignored for n=3.
        """.trimIndent()

        val result = GetExtractiveSummaryUseCase()(text, n = 3)

        assertEquals(3, result.size)
        assertTrue(result.all { it.length >= 20 })
    }
}

