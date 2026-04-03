package com.andrewwin.sumup

import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.WebsiteParser
import com.andrewwin.sumup.domain.usecase.ai.FormatExtractiveSummaryUseCase
import com.andrewwin.sumup.domain.usecase.common.BuildExtractiveSummaryUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrationBuildExtractiveSummaryUseCaseTest {
    @Test
    fun integration_buildExtractiveSummaryUseCase_formatsPipelineOutput() = runBlocking {
        val rssXml = """
            <rss version="2.0"><channel>
                <item>
                  <title>Energy policy update</title>
                  <link>https://example.com/energy</link>
                  <description>
                    Parliament approved a new energy policy roadmap.
                    Implementation starts in Q2 and includes grid upgrades.
                  </description>
                  <pubDate>Wed, 01 Apr 2026 10:30:00 +0000</pubDate>
                </item>
            </channel></rss>
        """.trimIndent()
        val websiteHtml = """
            <html><body>
              <article>
                <a class="title" href="/economy">Economic outlook improves</a>
                <div class="desc">
                  GDP forecast was revised upward after export growth and stable inflation data.
                  Analysts still monitor risks in logistics.
                </div>
              </article>
            </body></html>
        """.trimIndent()

        val rssArticle = ContentProcessingTestSupport.parseRssItemsForTest(rssXml, sourceId = 14L).first()
        val websiteArticle = WebsiteParser().parse(
            sourceId = 15L,
            sourceUrl = "https://news.example.com",
            html = websiteHtml,
            titleSelector = "a.title",
            postLinkSelector = "a.title",
            descriptionSelector = ".desc",
            dateSelector = null
        ).first()

        val cleanedRss = ContentProcessingTestSupport.cleanArticleTextUseCase(
            rssArticle.content,
            SourceType.RSS,
            footerPattern = null
        )
        val cleanedWebsite = ContentProcessingTestSupport.cleanArticleTextUseCase(
            websiteArticle.content,
            SourceType.WEBSITE,
            footerPattern = null
        )

        val useCase = BuildExtractiveSummaryUseCase(
            formatExtractiveSummaryUseCase = FormatExtractiveSummaryUseCase(),
            dispatcherProvider = ContentProcessingTestSupport.dispatcherProvider
        )

        val summary = useCase(
            headlines = listOf(rssArticle.title, websiteArticle.title),
            contentMap = mapOf(
                rssArticle.title to cleanedRss,
                websiteArticle.title to cleanedWebsite
            ),
            topCount = 2,
            sentencesPerArticle = 2
        )

        assertNotNull(summary)
        assertTrue(summary.contains("Energy policy update"))
        assertTrue(summary.contains("Economic outlook improves"))
        assertTrue(summary.contains("•"))
    }
}

