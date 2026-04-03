package com.andrewwin.sumup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssSourceParsesXmlLikePayloadTest {
    @Test
    fun rssSource_parseXmlLikePayload_extractsCoreFields() = runBlocking {
        val xml = """
            <rss version="2.0">
              <channel>
                <title>Test feed</title>
                <item>
                  <title>Rss title</title>
                  <link>https://example.com/post-1?utm_source=test#anchor</link>
                  <description><![CDATA[<img src="https://img.example.com/1.jpg"/>Rss content]]></description>
                  <pubDate>Wed, 01 Apr 2026 10:30:00 +0000</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val articles = ContentProcessingTestSupport.parseRssItemsForTest(xml, sourceId = 12L)

        assertEquals(1, articles.size)
        assertEquals("Rss title", articles[0].title)
        assertEquals("https://example.com/post-1", articles[0].url)
        assertEquals("https://img.example.com/1.jpg", articles[0].mediaUrl)
        assertTrue(articles[0].publishedAt > 0L)
    }
}

