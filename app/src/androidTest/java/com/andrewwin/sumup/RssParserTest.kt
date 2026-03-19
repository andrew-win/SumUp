package com.andrewwin.sumup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.andrewwin.sumup.data.remote.RssParser
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class RssParserTest {

    @Test
    fun testRssParsing_withEnclosureGuidAndHtmlImage() {
        val rss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Example RSS</title>
                <item>
                  <title>First item</title>
                  <link>https://example.com/news/1?utm=abc#frag</link>
                  <guid>https://example.com/news/1?utm=abc#frag</guid>
                  <description><![CDATA[
                    <p>Body text</p>
                    <img src="https://img.example.com/1.jpg" />
                  ]]></description>
                  <pubDate>Wed, 01 Jan 2025 10:30:00 +0000</pubDate>
                  <enclosure url="https://media.example.com/1.mp4" type="video/mp4" />
                </item>
                <item>
                  <title>Second item</title>
                  <link>https://example.com/news/2</link>
                  <description>Plain text without image</description>
                  <pubDate>Wed, 01 Jan 2025 11:00:00 +0000</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parser = RssParser(okHttpClient = okhttp3.OkHttpClient())
        val articles = runBlocking { parser.parseXml(rss, 42L) }

        println("=== RSS PARSE DEBUG ===")
        println("Count: ${articles.size}")
        articles.forEachIndexed { index, article ->
            println("-- Item #$index --")
            println("title=${article.title}")
            println("url=${article.url}")
            println("mediaUrl=${article.mediaUrl}")
            println("publishedAt=${article.publishedAt}")
            println("contentStart=${article.content.lines().take(3).joinToString("\\n")}")
        }
        println("=======================")

        assert(articles.size == 2) { "Expected 2 items, got ${articles.size}" }

        val first = articles[0]
        assert(first.url == "https://example.com/news/1") {
            "Expected URL to be cleaned from guid, got ${first.url}"
        }
        assert(first.mediaUrl == "https://media.example.com/1.mp4") {
            "Expected enclosure to win for mediaUrl, got ${first.mediaUrl}"
        }

        val second = articles[1]
        assert(second.mediaUrl == null) { "Expected no mediaUrl, got ${second.mediaUrl}" }

        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
        val expectedFirstDate = dateFormat.parse("Wed, 01 Jan 2025 10:30:00 +0000")!!.time
        assert(first.publishedAt == expectedFirstDate) {
            "Expected publishedAt to match RSS date, got ${first.publishedAt}"
        }
    }

    @Test
    fun testAtomParsing_withAlternateLinkAndEnclosureImage() {
        val atom = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Example Atom</title>
              <entry>
                <title>Atom entry</title>
                <link rel="alternate" href="https://example.com/atom/1?utm=abc#frag" />
                <link rel="enclosure" type="image/jpeg" href="https://img.example.com/a1.jpg" />
                <summary><![CDATA[
                  <p>Summary text</p>
                ]]></summary>
                <published>2025-01-01T12:00:00Z</published>
              </entry>
            </feed>
        """.trimIndent()

        val parser = RssParser(okHttpClient = okhttp3.OkHttpClient())
        val articles = runBlocking { parser.parseXml(atom, 7L) }

        println("=== ATOM PARSE DEBUG ===")
        println("Count: ${articles.size}")
        articles.forEachIndexed { index, article ->
            println("-- Entry #$index --")
            println("title=${article.title}")
            println("url=${article.url}")
            println("mediaUrl=${article.mediaUrl}")
            println("publishedAt=${article.publishedAt}")
            println("contentStart=${article.content.lines().take(3).joinToString("\\n")}")
        }
        println("========================")

        assert(articles.size == 1) { "Expected 1 entry, got ${articles.size}" }
        val entry = articles[0]
        assert(entry.url == "https://example.com/atom/1") {
            "Expected URL to be cleaned from alternate link, got ${entry.url}"
        }
        assert(entry.mediaUrl == "https://img.example.com/a1.jpg") {
            "Expected enclosure image to become mediaUrl, got ${entry.mediaUrl}"
        }
        assert(entry.title == "Atom entry") { "Unexpected title: ${entry.title}" }
    }

}
