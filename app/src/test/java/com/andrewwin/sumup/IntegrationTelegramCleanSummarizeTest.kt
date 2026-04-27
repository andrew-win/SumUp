package com.andrewwin.sumup

import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.TelegramParser
import com.andrewwin.sumup.domain.usecase.common.GetExtractiveSummaryUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrationTelegramCleanSummarizeTest {
    private val getExtractiveSummaryUseCase = GetExtractiveSummaryUseCase()

    @Test
    fun integration_telegramToCleanToSummarize_producesNonEmptySummary() = runBlocking {
        val html = """
            <div class="tgme_widget_message" data-post="channel/3">
              <a class="tgme_widget_message_date" href="https://t.me/channel/3">
                <time datetime="2026-04-01T12:00:00+00:00"></time>
              </a>
              <div class="tgme_widget_message_text">
                The government approved an energy reform package with multiple phases.
                Analysts expect lower grid losses and better market transparency by year end.
                #news
              </div>
            </div>
        """.trimIndent()

        val article = TelegramParser().parse(html, sourceId = 13L).first()
        val cleaned = ContentProcessingTestSupport.cleanArticleTextUseCase(
            article.content,
            SourceType.TELEGRAM,
            footerPattern = null
        )
        val summarySentences = getExtractiveSummaryUseCase(cleaned, n = 2)

        assertTrue(cleaned.isNotBlank())
        assertTrue(summarySentences.isNotEmpty())
    }
}

