package com.andrewwin.sumup

import com.andrewwin.sumup.data.remote.sources.rss.RssParser
import com.andrewwin.sumup.data.remote.sources.SourceRefreshBoundary
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

    @Test
    fun rssParser_parseRssItemWithCdataImage_extractsCoreFields() = runBlocking {
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

        val parser = RssParser()
        val articles = parser.parseArticlesXml(xml, sourceId = 12L).getOrThrow()

        assertEquals(1, articles.size)
        assertEquals("Rss title", articles[0].title)
        assertEquals("https://example.com/post-1?utm_source=test", articles[0].url)
        assertEquals("<img src=\"https://img.example.com/1.jpg\"/>Rss content", articles[0].content)
        assertEquals("https://img.example.com/1.jpg", articles[0].mediaUrl)
        assertTrue(articles[0].publishedAt > 0L)
    }

    @Test
    fun rssParser_parseAtomEntry_usesHrefLink() = runBlocking {
        val xml = """
            <feed>
              <title>Atom feed</title>
              <entry>
                <title>Atom title</title>
                <link href="https://example.com/atom-1#comments" />
                <id>tag:example.com,2026:atom-1</id>
                <summary>Atom summary</summary>
                <updated>Wed, 01 Apr 2026 10:30:00 +0000</updated>
              </entry>
            </feed>
        """.trimIndent()

        val parser = RssParser()
        val articles = parser.parseArticlesXml(xml, sourceId = 12L).getOrThrow()

        assertEquals(1, articles.size)
        assertEquals("Atom title", articles[0].title)
        assertEquals("https://example.com/atom-1", articles[0].url)
        assertEquals("Atom summary", articles[0].content)
    }

    @Test
    fun rssParser_parseArticlesXml_stopsAtKnownArticle() = runBlocking {
        val xml = """
            <rss version="2.0">
              <channel>
                <item>
                  <title>New title</title>
                  <link>https://example.com/new</link>
                  <pubDate>Wed, 01 Apr 2026 10:30:00 +0000</pubDate>
                </item>
                <item>
                  <title>Known title</title>
                  <link>https://example.com/known</link>
                  <pubDate>Wed, 01 Apr 2026 09:30:00 +0000</pubDate>
                </item>
                <item>
                  <title>Older title</title>
                  <link>https://example.com/older</link>
                  <pubDate>Wed, 01 Apr 2026 08:30:00 +0000</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val boundary = SourceRefreshBoundary(
            knownStableArticleKeys = emptySet(),
            knownUrls = setOf("https://example.com/known"),
            knownVideoIds = emptySet()
        )

        val parser = RssParser()
        val articles = parser.parseArticlesXml(xml, sourceId = 12L, refreshBoundary = boundary).getOrThrow()

        assertEquals(1, articles.size)
        assertEquals("New title", articles[0].title)
        assertEquals("https://example.com/new", articles[0].url)
    }

    @Test
    fun rssSource_parseChannelTitleXml_removesCdataWrapper() = runBlocking {
        val xml = """
            <rss version="2.0">
              <channel>
                <title><![CDATA[ suspilne.news ]]></title>
              </channel>
            </rss>
        """.trimIndent()

        val parser = RssParser()

        assertEquals("suspilne.news", parser.parseChannelTitleXml(xml))
    }
}
