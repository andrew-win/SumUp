package com.andrewwin.sumup

import com.andrewwin.sumup.domain.support.DispatcherProvider
import com.andrewwin.sumup.domain.usecase.common.CleanArticleTextUseCase
import kotlinx.coroutines.Dispatchers

object ContentProcessingTestSupport {
    val dispatcherProvider = object : DispatcherProvider {
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    val cleanArticleTextUseCase = CleanArticleTextUseCase(dispatcherProvider)

    fun parseRssItemsForTest(xml: String, sourceId: Long): List<TestParsedArticle> {
        val itemRegex = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
        val titleRegex = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
        val linkRegex = Regex("<link>(.*?)</link>", RegexOption.DOT_MATCHES_ALL)
        val descriptionRegex = Regex("<description>(.*?)</description>", RegexOption.DOT_MATCHES_ALL)
        val pubDateRegex = Regex("<pubDate>(.*?)</pubDate>", RegexOption.DOT_MATCHES_ALL)
        val imageRegex = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

        return itemRegex.findAll(xml).mapNotNull { match ->
            val item = match.groupValues[1]
            val title = titleRegex.find(item)?.groupValues?.get(1)?.trim().orEmpty()
            val rawUrl = linkRegex.find(item)?.groupValues?.get(1)?.trim().orEmpty()
            val descriptionRaw = descriptionRegex.find(item)?.groupValues?.get(1)?.trim().orEmpty()
            val pubDateRaw = pubDateRegex.find(item)?.groupValues?.get(1)?.trim().orEmpty()

            if (title.isBlank() || rawUrl.isBlank()) return@mapNotNull null

            val normalizedUrl = rawUrl.substringBefore("#").substringBefore("?")
            val description = descriptionRaw
                .removePrefix("<![CDATA[")
                .removeSuffix("]]>")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            val mediaUrl = imageRegex.find(descriptionRaw)?.groupValues?.get(1)

            TestParsedArticle(
                sourceId = sourceId,
                title = title,
                content = description,
                mediaUrl = mediaUrl,
                url = normalizedUrl,
                publishedAt = if (pubDateRaw.isBlank()) System.currentTimeMillis() else 1L
            )
        }.toList()
    }
}

data class TestParsedArticle(
    val sourceId: Long,
    val title: String,
    val content: String,
    val mediaUrl: String?,
    val url: String,
    val publishedAt: Long
)

