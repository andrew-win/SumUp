package com.andrewwin.sumup.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteParserTest {

    private val parser = WebsiteParser()

    @Test
    fun parse_extracts_articles_by_heading_selector() {
        val html = """
            <html>
              <body>
                <div class="news-item">
                  <a class="heading" href="/rus/news/first-post">First title</a>
                  <p class="desc">First description</p>
                  <time class="published">2026-03-20T10:00:00Z</time>
                </div>
                <div class="news-item">
                  <a class="heading" href="/rus/news/second-post">Second title</a>
                  <p class="desc">Second description</p>
                  <time class="published">2026-03-20T11:00:00Z</time>
                </div>
              </body>
            </html>
        """.trimIndent()

        val articles = parser.parse(
            sourceId = 10L,
            sourceUrl = "https://www.rbc.ua/rus/news",
            html = html,
            titleSelector = "a.heading",
            postLinkSelector = null,
            descriptionSelector = ".desc",
            dateSelector = ".published"
        )

        assertEquals(2, articles.size)
        assertEquals("First title", articles[0].title)
        assertEquals("First description", articles[0].content)
        assertEquals("https://www.rbc.ua/rus/news/first-post", articles[0].url)
        assertTrue(articles[0].publishedAt > 0L)
    }

    @Test
    fun parse_falls_back_to_title_when_description_absent() {
        val html = """
            <html>
              <body>
                <div>
                  <a class="heading" href="/rus/news/only-title">Only title</a>
                </div>
              </body>
            </html>
        """.trimIndent()

        val articles = parser.parse(
            sourceId = 11L,
            sourceUrl = "https://www.rbc.ua/rus/news",
            html = html,
            titleSelector = "a.heading",
            postLinkSelector = null,
            descriptionSelector = ".missing",
            dateSelector = ".missing"
        )

        assertEquals(1, articles.size)
        assertEquals("Only title", articles[0].content)
    }
}
