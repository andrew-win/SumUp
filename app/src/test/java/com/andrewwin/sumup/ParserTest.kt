package com.andrewwin.sumup

import com.andrewwin.sumup.data.remote.TelegramParser
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test

class ParserTest {

    @Test
    fun testTelegramParsing() {
        val client = OkHttpClient()
        val parser = TelegramParser()
        val channels = listOf("truexanewsua", "xydessa_")

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
                val articles = parser.parse(html, 0L).take(3)
                
                articles.forEachIndexed { index, article ->
                    println("\n--- Post #${index + 1} ---")
                    println("TITLE: ${article.title}")
                    println("CONTENT START:\n${article.content}")
                    println("-----------------------")
                }
            }
        }
    }
}
