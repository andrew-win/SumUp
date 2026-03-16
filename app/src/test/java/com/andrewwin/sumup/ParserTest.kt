package com.andrewwin.sumup

import com.andrewwin.sumup.data.remote.TelegramParser
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class ParserTest {

    @Test
    fun testTelegramParsing() {
        val client = OkHttpClient()
        val parser = TelegramParser()
        val channels = listOf("truexanewsua", "xydessa_")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        channels.forEach { channel ->
            println("\n=== CHANNEL: $channel ===")
            val url = "https://t.me/s/$channel"
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    println("Failed to fetch $channel: ${response.code}")
                    return@use
                }
                
                val html = response.body?.string() ?: ""
                val result = parser.parseWithDebug(html, 0L)
                val articles = result.articles.take(3)
                val debug = result.debug.take(10)

                println("\n--- DEBUG ---")
                debug.forEach { d ->
                    val formatted = d.parsedAt?.let { dateFormat.format(it) } ?: "null"
                    println("KEY=${d.key} URL=${d.url} DATE=${d.dateStr} EPOCH=${d.epochStr} PARSED=$formatted TEXT_LEN=${d.textLength}")
                }
                
                articles.forEachIndexed { index, article ->
                    println("\n--- Post #${index + 1} ---")
                    println("TITLE: ${article.title}")
                    println("PUBLISHED_AT: ${dateFormat.format(article.publishedAt)}")
                    println("URL: ${article.url}")
                    println("CONTENT START:\n${article.content}")
                    println("-----------------------")
                }
            }
        }
    }
}
