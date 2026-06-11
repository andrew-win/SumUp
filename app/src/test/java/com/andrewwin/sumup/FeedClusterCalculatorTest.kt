package com.andrewwin.sumup

import com.andrewwin.sumup.domain.article.model.Article
import com.andrewwin.sumup.domain.feed.clustering.FeedClusterCalculator
import com.andrewwin.sumup.domain.feed.model.ArticleCluster
import com.andrewwin.sumup.domain.feed.model.ArticlePairKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedClusterCalculatorTest {
    @Test
    fun `does not attach article without links to every cluster member`() {
        val clusters = FeedClusterCalculator.buildFinalClusters(
            articles = listOf(
                testArticle(id = 1, publishedAt = 300),
                testArticle(id = 2, publishedAt = 200),
                testArticle(id = 3, publishedAt = 100)
            ),
            pairScores = mapOf(
                ArticlePairKey.of(1, 2) to 0.99f,
                ArticlePairKey.of(1, 3) to 0.98f
            ),
            threshold = 0.86f
        )

        val memberSets = clusters.map { it.memberIds() }

        assertTrue(memberSets.contains(setOf(1L, 2L)))
        assertTrue(memberSets.contains(setOf(3L)))
        assertFalse(memberSets.contains(setOf(1L, 2L, 3L)))
    }

    @Test
    fun `does not merge clusters without all cross links`() {
        val clusters = FeedClusterCalculator.buildFinalClusters(
            articles = listOf(
                testArticle(id = 1, publishedAt = 400),
                testArticle(id = 2, publishedAt = 300),
                testArticle(id = 3, publishedAt = 200),
                testArticle(id = 4, publishedAt = 100)
            ),
            pairScores = mapOf(
                ArticlePairKey.of(1, 2) to 0.99f,
                ArticlePairKey.of(3, 4) to 0.98f,
                ArticlePairKey.of(2, 3) to 0.97f
            ),
            threshold = 0.86f
        )

        val memberSets = clusters.map { it.memberIds() }

        assertTrue(memberSets.contains(setOf(1L, 2L)))
        assertTrue(memberSets.contains(setOf(3L, 4L)))
        assertFalse(memberSets.contains(setOf(1L, 2L, 3L, 4L)))
    }

    @Test
    fun `merges clusters when all cross links exist`() {
        val clusters = FeedClusterCalculator.buildFinalClusters(
            articles = listOf(
                testArticle(id = 1, publishedAt = 400),
                testArticle(id = 2, publishedAt = 300),
                testArticle(id = 3, publishedAt = 200),
                testArticle(id = 4, publishedAt = 100)
            ),
            pairScores = mapOf(
                ArticlePairKey.of(1, 2) to 0.99f,
                ArticlePairKey.of(3, 4) to 0.98f,
                ArticlePairKey.of(2, 3) to 0.97f,
                ArticlePairKey.of(1, 3) to 0.96f,
                ArticlePairKey.of(1, 4) to 0.95f,
                ArticlePairKey.of(2, 4) to 0.94f
            ),
            threshold = 0.86f
        )

        assertEquals(listOf(setOf(1L, 2L, 3L, 4L)), clusters.map { it.memberIds() })
    }

    private fun ArticleCluster.memberIds(): Set<Long> {
        return (listOf(representative.id) + duplicates.map { it.first.id }).toSet()
    }

    private fun testArticle(id: Long, publishedAt: Long): Article {
        return Article(
            id = id,
            sourceId = 1,
            title = "Article $id",
            content = "Content $id",
            url = "https://example.com/$id",
            publishedAt = publishedAt
        )
    }
}
