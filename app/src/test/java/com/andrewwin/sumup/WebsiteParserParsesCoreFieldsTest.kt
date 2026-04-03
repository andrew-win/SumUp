package com.andrewwin.sumup

import com.andrewwin.sumup.data.remote.WebsiteParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteParserParsesCoreFieldsTest {
    @Test
    fun websiteParser_parsesTitlesLinksDescriptionAndDate() {
        val html = """
            <html><body>
              <article class="post">
                <a class="title" href="/news/a">First title</a>
                <div class="desc">First article description</div>
                <time class="date">2026-04-01 12:00:00</time>
              </article>
              <article class="post">
                <a class="title" href="/news/b">Second title</a>
                <div class="desc">Second article description</div>
                <time class="date">2026-04-01 11:00:00</time>
              </article>
            </body></html>
        """.trimIndent()

        val articles = WebsiteParser().parse(
            sourceId = 11L,
            sourceUrl = "https://example.com",
            html = html,
            titleSelector = "a.title",
            postLinkSelector = "a.title",
            descriptionSelector = ".desc",
            dateSelector = ".date"
        )

        assertEquals(2, articles.size)
        assertEquals("First title", articles[0].title)
        assertEquals("https://example.com/news/a", articles[0].url)
        assertEquals("First article description", articles[0].content)
        assertTrue(articles[0].publishedAt > 0L)
    }
}

