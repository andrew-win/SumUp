package com.andrewwin.sumup

import com.andrewwin.sumup.domain.usecase.common.GetExtractiveSummaryUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractiveSummarizerCentralHeadlinesTest {
    private val getExtractiveSummaryUseCase = GetExtractiveSummaryUseCase()

    @Test
    fun extractiveSummarizer_getCentralHeadlines_reducesNearDuplicates() {
        val headlines = listOf(
            "Apple unveils a new iPhone lineup for 2026",
            "New iPhone lineup announced by Apple in 2026",
            "Oil prices rise after OPEC decision",
            "Global oil market jumps after OPEC output update",
            "UEFA reveals Champions League schedule updates"
        )

        val selected = getExtractiveSummaryUseCase.getCentralHeadlines(headlines, count = 3)

        assertEquals(3, selected.size)
        assertTrue(selected.distinct().size == selected.size)
        assertTrue(selected.any { it.contains("iPhone", ignoreCase = true) })
        assertTrue(selected.any { it.contains("oil", ignoreCase = true) })
    }
}

