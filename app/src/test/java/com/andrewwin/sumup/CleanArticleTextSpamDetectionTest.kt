package com.andrewwin.sumup

import com.andrewwin.sumup.data.local.entities.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanArticleTextSpamDetectionTest {
    @Test
    fun cleanArticleText_flagsSpamWhenHashtagAndPhoneDetected() = runBlocking {
        val raw = "Промо текст #реклама Дзвоніть +380 (67) 123-45-67 прямо зараз"

        val cleaned = ContentProcessingTestSupport.cleanArticleTextUseCase(
            text = raw,
            type = SourceType.TELEGRAM,
            footerPattern = null
        )

        assertTrue(cleaned.startsWith("[ad]"))
        assertFalse(cleaned.contains("#реклама"))
        assertFalse(cleaned.contains("+380"))
    }
}

