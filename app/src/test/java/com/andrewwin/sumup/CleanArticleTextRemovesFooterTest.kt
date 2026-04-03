package com.andrewwin.sumup

import com.andrewwin.sumup.data.local.entities.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanArticleTextRemovesFooterTest {
    @Test
    fun cleanArticleText_removesHtmlAndGenericFooterForWebsite() = runBlocking {
        val raw = """
            <div>Core news paragraph one.</div>
            <div>Core news paragraph two with details.</div>
            <div>Subscribe to our channel</div>
            <div>https://t.me/example_channel</div>
        """.trimIndent()

        val cleaned = ContentProcessingTestSupport.cleanArticleTextUseCase(
            text = raw,
            type = SourceType.WEBSITE,
            footerPattern = null
        )

        assertTrue(!cleaned.contains("<div>"))
        assertTrue(cleaned.contains("Core news paragraph one."))
        assertFalse(cleaned.lowercase().contains("subscribe"))
        assertFalse(cleaned.contains("https://t.me/example_channel"))
    }
}

