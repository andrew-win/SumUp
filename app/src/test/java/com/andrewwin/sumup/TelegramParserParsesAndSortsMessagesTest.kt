package com.andrewwin.sumup

import com.andrewwin.sumup.data.remote.TelegramParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramParserParsesAndSortsMessagesTest {
    @Test
    fun telegramParser_parsesAndSortsMessages() {
        val html = """
            <div class="tgme_widget_message" data-post="channel/1">
              <a class="tgme_widget_message_date" href="tg:resolve?domain=channel&post=1">
                <time datetime="2026-03-31T10:00:00+00:00"></time>
              </a>
              <div class="tgme_widget_message_text">Older news line</div>
            </div>
            <div class="tgme_widget_message" data-post="channel/2">
              <a class="tgme_widget_message_date" href="https://t.me/channel/2">
                <time datetime="2026-04-01T12:00:00+00:00"></time>
              </a>
              <div class="tgme_widget_message_text">Newer news line</div>
            </div>
        """.trimIndent()

        val articles = TelegramParser().parse(html, sourceId = 10L)

        assertEquals(2, articles.size)
        assertEquals("Newer news line", articles[0].title)
        assertEquals("https://t.me/s/channel/2", articles[0].url)
        assertEquals("https://t.me/s/channel/1", articles[1].url)
        assertTrue(articles[0].publishedAt >= articles[1].publishedAt)
    }
}

