package com.andrewwin.sumup

import com.andrewwin.sumup.domain.article.model.Article
import com.andrewwin.sumup.domain.article.processing.ArticleImportanceScorer
import com.andrewwin.sumup.domain.source.model.SourceType
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleImportanceScorerTest {
    private val scorer = ArticleImportanceScorer()

    @Test
    fun rssHeadlineOnly_rutteArrivesInKyiv_reachesImportanceThreshold() {
        val article = Article(
            sourceId = 1L,
            title = "До Києва приїхав генсек НАТО Рютте",
            content = "",
            url = "https://example.com/article",
            publishedAt = 1L,
            viewCount = 1_000L
        )

        val score = scorer.score(
            article = article,
            averageViews = 1_000L,
            sourceType = SourceType.RSS
        )

        assertTrue(
            "Expected score >= ${ArticleImportanceScorer.IMPORTANCE_THRESHOLD}, but was $score",
            score >= ArticleImportanceScorer.IMPORTANCE_THRESHOLD
        )
    }
}
