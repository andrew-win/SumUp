package com.andrewwin.sumup.domain.repository

import com.andrewwin.sumup.domain.entities.article.Article
import com.andrewwin.sumup.domain.news.ArticleCluster

interface FeedClusterSnapshotRepository {
    suspend fun loadClusters(
        articles: List<Article>,
        clusteringSettingsSignature: String
    ): List<ArticleCluster>?

    suspend fun saveClusters(
        articles: List<Article>,
        clusteringSettingsSignature: String,
        clusters: List<ArticleCluster>
    )

    suspend fun clearAll(reason: String)

    fun buildArticlesSignature(articles: List<Article>): String

    fun buildClusteringSettingsSignature(strategyKey: String, threshold: Float): String
}
