package com.andrewwin.sumup

import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.TelegramParser
import com.andrewwin.sumup.domain.usecase.CleanArticleTextUseCase
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.junit.Test
import java.io.BufferedReader
import java.text.SimpleDateFormat
import java.util.Locale

class ParserTest {

    @Test
    fun testTelegramParsing() {
        val parser = TelegramParser()
        val channels = listOf("truexanewsua", "xydessa_")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        channels.forEach { channel ->
            println("\n=== CHANNEL: $channel ===")
            val url = "https://t.me/s/$channel"
            println("Skipping live fetch for $url in unit tests.")
        }
    }

    @Test
    fun testTelegramReplyDoesNotBecomeTitle() {
        val parser = TelegramParser()
        val html = loadFixtureOrFetch(
            "telegram/suspilnenews_64615.html",
            "https://t.me/s/suspilnenews/64615"
        )
        val doc = Jsoup.parse(html)
        val message = doc.selectFirst(".tgme_widget_message[data-post='suspilnenews/64615']")
            ?: doc.selectFirst(".tgme_widget_message a.tgme_widget_message_date[href*='/64615']")?.closest(".tgme_widget_message")

        requireNotNull(message) { "Message element not found in fixture for suspilnenews/64615" }

        val replyText = message
            .selectFirst(".tgme_widget_message_reply")
            ?.text()
            ?.trim()
            .orEmpty()
        val replyHtml = message
            .selectFirst(".tgme_widget_message_reply")
            ?.outerHtml()
            .orEmpty()
        val textElement = message.selectFirst(".tgme_widget_message_text")
        val textHtml = textElement?.outerHtml().orEmpty()
        val textWhole = textElement?.wholeText()?.trim().orEmpty()
        val textOwn = textElement?.ownText()?.trim().orEmpty()

        val result = parser.parse(html, 0L)
        val article = result.firstOrNull { it.url.endsWith("/64615") }
        requireNotNull(article) { "Parsed article not found for suspilnenews/64615 in fixture" }

        println("--- TELEGRAM POST DEBUG ---")
        println("URL: ${article.url}")
        println("RAW_REPLY: ${replyText.ifBlank { "<none>" }}")
        if (replyHtml.isNotBlank()) {
            println("REPLY_HTML:\n$replyHtml")
        }
        if (textHtml.isNotBlank()) {
            println("TEXT_HTML:\n$textHtml")
        }
        println("TEXT_WHOLE: $textWhole")
        println("TEXT_OWN: $textOwn")
        println("PARSED_TITLE: ${article.title}")
        println("PARSED_CONTENT_START:")
        println(article.content.lines().take(10).joinToString("\n"))
        println("---------------------------")

        if (replyText.isNotBlank()) {
            val normalizedReply = replyText.replace("\\s+".toRegex(), " ").trim()
            val normalizedTitle = article.title.replace("\\s+".toRegex(), " ").trim()
            assert(!normalizedTitle.startsWith(normalizedReply)) {
                "Title incorrectly starts with reply text. Reply='$normalizedReply' Title='$normalizedTitle'"
            }
        }
    }

    @Test
    fun testTelegramRemovesHashtagsAndPhones_odesatruexaua_55930() {
        val parser = TelegramParser()
        val cleaner = CleanArticleTextUseCase()
        val url = "https://t.me/s/odesatruexa/55930"
        val html = loadFixtureOrFetch("telegram/odesatruexa_55930.html", url)
        val result = parser.parse(html, 0L)
        val article = result.firstOrNull { it.url.endsWith("/55930") }
        requireNotNull(article) { "Parsed article not found for $url" }

        val cleaned = cleaner(article.content, SourceType.TELEGRAM)

        println("--- TELEGRAM CLEANUP DEBUG (ODESA TRUEXA) ---")
        println("URL: ${article.url}")
        println("PARSED_TITLE: ${article.title}")
        println("PARSED_CONTENT_START:")
        println(article.content.lines().take(12).joinToString("\n"))
        println("CLEANED_CONTENT_START:")
        println(cleaned.lines().take(12).joinToString("\n"))
        println("-------------------------------------------")

        assert(!cleaned.contains("#")) { "Hashtag was not removed in cleaned content for $url" }
        val phoneRegex = Regex("\\+?\\d[\\d\\s().-]{7,}\\d")
        assert(!phoneRegex.containsMatchIn(cleaned)) { "Phone number was not removed in cleaned content for $url" }
    }

    @Test
    fun testTelegramRemovesHashtagsAndPhones_xydessa_63446() {
        val parser = TelegramParser()
        val cleaner = CleanArticleTextUseCase()
        val url = "https://t.me/s/xydessa/63446"
        val html = loadFixtureOrFetch("telegram/xydessa_63446.html", url)
        val result = parser.parse(html, 0L)
        val article = result.firstOrNull { it.url.endsWith("/63446") }
        requireNotNull(article) { "Parsed article not found for $url" }

        val cleaned = cleaner(article.content, SourceType.TELEGRAM)

        println("--- TELEGRAM CLEANUP DEBUG (XY DESSA) ---")
        println("URL: ${article.url}")
        println("PARSED_TITLE: ${article.title}")
        println("PARSED_CONTENT_START:")
        println(article.content.lines().take(12).joinToString("\n"))
        println("CLEANED_CONTENT_START:")
        println(cleaned.lines().take(12).joinToString("\n"))
        println("----------------------------------------")

        assert(!cleaned.contains("#")) { "Hashtag was not removed in cleaned content for $url" }
        val phoneRegex = Regex("\\+?\\d[\\d\\s().-]{7,}\\d")
        assert(!phoneRegex.containsMatchIn(cleaned)) { "Phone number was not removed in cleaned content for $url" }
    }

    private fun loadFixtureOrFetch(path: String, url: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(path)
        if (stream != null) {
            return stream.bufferedReader().use(BufferedReader::readText)
        }

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Failed to fetch $url: HTTP ${response.code}")
            }
            return response.body?.string().orEmpty().ifBlank {
                error("Empty HTML fetched from $url")
            }
        }
    }
}
