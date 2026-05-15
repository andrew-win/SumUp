package com.andrewwin.sumup

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.news.ArticleTitleFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ArticleTitleFormatterTest {
    private val formatter = ArticleTitleFormatter()

    @Test
    fun telegramTitleFormatter_buildsTitleFromMultipleLinesWithinLimit() {
        val article = testArticle(
            title = "Об итогах визита Трампа в Китай",
            content = """
                Об итогах визита Трампа в Китай

                Сегодня закончилась двухдневная поездка президента США в Пекин.
            """.trimIndent()
        )

        val formatted = formatter.format(article, SourceType.TELEGRAM)

        assertEquals(
            "Об итогах визита Трампа в Китай. Сегодня закончилась двухдневная поездка президента США в Пекин",
            formatted.title
        )
        assertEquals("", formatted.content)
    }

    @Test
    fun telegramTitleFormatter_buildsTitleWithoutEmojiAndMarkdownUrl() {
        val article = testArticle(
            title = "Главное за 15 мая",
            content = """
                Главное за 15 мая

                🔹В ночь на 15 мая Рязань была атакована (https://t.me/tvrain/104791), по данным местных властей, 99 беспилотниками
            """.trimIndent()
        )

        val formatted = formatter.format(article, SourceType.TELEGRAM)

        assertEquals(
            "Главное за 15 мая. В ночь на 15 мая Рязань была атакована, по данным местных властей, 99 беспилотниками",
            formatted.title
        )
        assertFalse(formatted.title.contains("🔹"))
        assertFalse(formatted.title.contains("https://"))
    }

    @Test
    fun rssTitleFormatter_removesEmojiHashtagsMentionsAndTrailingDotWithoutChangingContent() {
        val article = testArticle(
            title = "⚡ Breaking news. #Ukraine @channelname",
            content = "Original content #Ukraine @channelname."
        )

        val formatted = formatter.format(article, SourceType.RSS)

        assertEquals("Breaking news", formatted.title)
        assertEquals("Original content #Ukraine @channelname.", formatted.content)
    }

    @Test
    fun youtubeTitleFormatter_keepsQuestionAndExclamationMarks() {
        val questionArticle = testArticle(
            title = "What happened?",
            content = "Original content"
        )
        val exclamationArticle = testArticle(
            title = "Breaking news!",
            content = "Original content"
        )

        assertEquals("What happened?", formatter.format(questionArticle, SourceType.YOUTUBE).title)
        assertEquals("Breaking news!", formatter.format(exclamationArticle, SourceType.YOUTUBE).title)
    }

    @Test
    fun telegramTitleFormatter_cutsLongTitleByCompleteWords() {
        val article = testArticle(
            title = "",
            content = "Мігранти без попередження тікають із роботи після першого нічного обстрілу: " +
                "«МХП» поділилися історією про сімох іноземців, які прибули до України — " +
                "переселенці просто перестали виходити на звʼязок."
        )

        val formatted = formatter.format(article, SourceType.TELEGRAM)

        assertEquals(
            "Мігранти без попередження тікають із роботи після першого нічного обстрілу: " +
                "«МХП» поділилися історією про сімох іноземців, які прибули до України",
            formatted.title
        )
    }

    private fun testArticle(
        title: String,
        content: String
    ): Article = Article(
        sourceId = 1L,
        title = title,
        content = content,
        url = "https://example.com/article",
        publishedAt = 1L
    )
}
