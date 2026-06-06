package com.andrewwin.sumup

import com.andrewwin.sumup.data.remote.sources.telegram.TelegramParser
import org.jsoup.Jsoup
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

        val articles = TelegramParser().parseHtml(html, sourceId = 10L)

        assertEquals(2, articles.size)
        assertEquals("Newer news line", articles[0].title)
        assertEquals("https://t.me/s/channel/2", articles[0].url)
        assertEquals("https://t.me/s/channel/1", articles[1].url)
        assertTrue(articles[0].publishedAt >= articles[1].publishedAt)
    }

    @Test
    fun telegramParser_scanPageKeepsMetadataAndAllRelevantArticles() {
        val html = """
            <div class="tgme_widget_message" data-post="channel/1">
              <a class="tgme_widget_message_date" href="https://t.me/channel/1">
                <time datetime="2026-03-31T10:00:00+00:00"></time>
              </a>
              <div class="tgme_widget_message_text">Known news line</div>
            </div>
            <div class="tgme_widget_message" data-post="channel/2">
              <a class="tgme_widget_message_date" href="https://t.me/channel/2">
                <time datetime="2026-04-01T12:00:00+00:00"></time>
              </a>
              <div class="tgme_widget_message_text">Fresh news line</div>
            </div>
        """.trimIndent()

        val result = TelegramParser().parsePage(
            document = Jsoup.parse(html),
            sourceId = 10L,
            oldestAllowedPublishedAt = null
        )

        assertEquals(2, result.metadata.messageCount)
        assertEquals(1L, result.metadata.oldestMessageId)
        assertEquals(2L, result.metadata.newestMessageId)
        assertEquals("1", result.nextPageCursor)
        assertEquals(2, result.articles.size)
        assertEquals("Fresh news line", result.articles.first().title)
        assertEquals("Known news line", result.articles.last().title)
    }
}
