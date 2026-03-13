package com.andrewwin.sumup

import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.RssParser
import com.andrewwin.sumup.data.remote.TelegramParser
import com.andrewwin.sumup.data.remote.YouTubeParser
import com.andrewwin.sumup.data.remote.datasource.RemoteArticleDataSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.junit.Test

class FullContentTest {

    @Test
    fun testSuspilneFullContent() {
        val client = OkHttpClient()
        // Використовуємо реальні парсери, але для RSS в тесті візьмемо Jsoup, 
        // щоб обійти обмеження XmlPullParser у Unit-тестах
        val dataSource = RemoteArticleDataSource(
            okHttpClient = client,
            rssParser = RssParser(),
            telegramParser = TelegramParser(),
            youtubeParser = YouTubeParser()
        )

        val rssUrl = "https://suspilne.media/rss/all.rss"
        println("=== STARTING TEST: $rssUrl ===")

        val request = Request.Builder().url(rssUrl).build()
        client.newCall(request).execute().use { response ->
            val html = response.body?.string() ?: ""
            val doc = Jsoup.parse(html, "", org.jsoup.parser.Parser.xmlParser())
            
            // Витягуємо лінки вручну через Jsoup для тесту
            val items = doc.select("item").take(3)
            
            if (items.isEmpty()) {
                println("ERROR: No items found in RSS!")
                return
            }

            items.forEach { item ->
                val title = item.select("title").text()
                val url = item.select("link").text().substringBefore("?").substringBefore("#")
                
                println("\nARTICLE: $title")
                println("TARGET URL: $url")

                val fullContent = kotlinx.coroutines.runBlocking {
                    dataSource.fetchFullContent(url, SourceType.RSS)
                }

                if (fullContent != null) {
                    println("SUCCESS! Length: ${fullContent.length}")
                    println("CONTENT PREVIEW: ${fullContent.take(300)}...")
                } else {
                    println("FAILURE: Returned NULL for $url")
                }
            }
        }
    }
}
