package com.andrewwin.sumup

import com.andrewwin.sumup.domain.article.model.Article
import com.andrewwin.sumup.domain.article.processing.ArticleImportanceScorer
import com.andrewwin.sumup.domain.source.model.SourceType
import org.junit.Assert.assertEquals
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

    @Test
    fun rssHeadlineOnly_ukrainianUppercaseAbbreviations_reachImportanceThreshold() {
        val article = Article(
            sourceId = 1L,
            title = "ЗСУ СБУ ГУР повідомили деталі",
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
            "Expected Ukrainian uppercase abbreviations to count as entities and reach ${ArticleImportanceScorer.IMPORTANCE_THRESHOLD}, but was $score",
            score >= ArticleImportanceScorer.IMPORTANCE_THRESHOLD
        )
    }

    @Test
    fun nonRss_twoThirdsAverageViewsAndOneFact_reachesImportanceThreshold() {
        val article = Article(
            sourceId = 1L,
            title = "ЗСУ повідомили деталі",
            content = "Короткий опис події без додаткових чисел.",
            url = "https://example.com/article",
            publishedAt = 1L,
            viewCount = 666L
        )

        val score = scorer.score(
            article = article,
            averageViews = 999L,
            sourceType = SourceType.TELEGRAM
        )

        assertTrue(
            "Expected score >= ${ArticleImportanceScorer.IMPORTANCE_THRESHOLD}, but was $score",
            score >= ArticleImportanceScorer.IMPORTANCE_THRESHOLD
        )
    }

    @Test
    fun nonRss_belowViewsThresholdWithoutFacts_doesNotReachImportanceThreshold() {
        val article = Article(
            sourceId = 1L,
            title = "звичайне повідомлення без власних назв",
            content = "короткий опис події без чисел і назв",
            url = "https://example.com/article",
            publishedAt = 1L,
            viewCount = 600L
        )

        val score = scorer.score(
            article = article,
            averageViews = 1_000L,
            sourceType = SourceType.TELEGRAM
        )

        assertTrue(
            "Expected score < ${ArticleImportanceScorer.IMPORTANCE_THRESHOLD}, but was $score",
            score < ArticleImportanceScorer.IMPORTANCE_THRESHOLD
        )
    }

    @Test
    fun highViewsAndManyFacts_canExceedOne() {
        val article = Article(
            sourceId = 1L,
            title = "ЗСУ СБУ ГУР повідомили 3 деталі",
            content = "У Києві зафіксували 12 нових рішень і 2 заяви.",
            url = "https://example.com/article",
            publishedAt = 1L,
            viewCount = 3_000L
        )

        val score = scorer.score(
            article = article,
            averageViews = 1_000L,
            sourceType = SourceType.TELEGRAM
        )

        assertTrue("Expected score > 1, but was $score", score > 1f)
    }

    @Test
    fun zeroScoreKeyword_stillForcesZero() {
        val article = Article(
            sourceId = 1L,
            title = "Реклама партнерського сервісу",
            content = "Київ повідомив 10 деталей у великому тексті.",
            url = "https://example.com/article",
            publishedAt = 1L,
            viewCount = 10_000L
        )

        val score = scorer.score(
            article = article,
            averageViews = 1_000L,
            sourceType = SourceType.TELEGRAM
        )

        assertEquals(0f, score, 0.0001f)
    }
}
