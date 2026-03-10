package com.andrewwin.sumup.data.remote

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.TextCleaner
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.*

class TelegramParser {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    fun parse(html: String, sourceId: Long): List<Article> {
        val doc = Jsoup.parse(html)
        val messages = doc.select(".tgme_widget_message")
        val articles = mutableListOf<Article>()

        messages.forEach { element ->
            val textElement = element.selectFirst(".tgme_widget_message_text") ?: return@forEach
            val dateElement = element.selectFirst("time")
            val linkElement = element.selectFirst(".tgme_widget_message_date")

            val fullText = TextCleaner.clean(textElement.wholeText())
            if (fullText.isEmpty()) return@forEach

            val lines = fullText.split("\n").filter { it.isNotBlank() }
            val title = lines.firstOrNull()?.take(100) ?: ""
            
            val url = linkElement?.attr("href") ?: ""
            val dateStr = dateElement?.attr("datetime")
            val publishedAt = try {
                dateStr?.let { dateFormat.parse(it)?.time } ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }

            articles.add(
                Article(
                    sourceId = sourceId,
                    title = title,
                    content = fullText,
                    url = url,
                    publishedAt = publishedAt
                )
            )
        }
        return articles
    }
}
