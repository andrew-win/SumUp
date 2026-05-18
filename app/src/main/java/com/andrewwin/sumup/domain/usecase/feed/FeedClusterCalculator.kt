package com.andrewwin.sumup.domain.usecase.feed

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.news.ArticleCluster

object FeedClusterCalculator {
    fun buildFinalClusters(
        articles: List<Article>,
        pairScores: Map<ArticlePairKey, Float>
    ): List<ArticleCluster> {
        val articleById = articles.associateBy { it.id }
        val clustersByArticleId = mutableMapOf<Long, MutableSet<Long>>()

        pairScores.entries
            .sortedByDescending { it.value }
            .forEach { (pair, _) ->
                val leftId = pair.firstId
                val rightId = pair.secondId
                if (articleById[leftId] == null || articleById[rightId] == null) return@forEach

                val leftCluster = clustersByArticleId[leftId]
                val rightCluster = clustersByArticleId[rightId]
                when {
                    leftCluster == null && rightCluster == null -> {
                        val cluster = mutableSetOf(leftId, rightId)
                        clustersByArticleId[leftId] = cluster
                        clustersByArticleId[rightId] = cluster
                    }

                    leftCluster != null && rightCluster == null -> {
                        if (rightId.hasSimilarityToEveryArticleIn(leftCluster, pairScores)) {
                            leftCluster.add(rightId)
                            clustersByArticleId[rightId] = leftCluster
                        }
                    }

                    leftCluster == null && rightCluster != null -> {
                        if (leftId.hasSimilarityToEveryArticleIn(rightCluster, pairScores)) {
                            rightCluster.add(leftId)
                            clustersByArticleId[leftId] = rightCluster
                        }
                    }

                    leftCluster != null &&
                        rightCluster != null &&
                        leftCluster !== rightCluster &&
                        leftCluster.canMergeWith(rightCluster, pairScores) -> {
                        leftCluster.addAll(rightCluster)
                        rightCluster.forEach { clustersByArticleId[it] = leftCluster }
                    }
                }
            }

        val acceptedClusterSets = clustersByArticleId.values
            .distinctBy { System.identityHashCode(it) }
            .filter { it.size >= 2 }

        val result = acceptedClusterSets.mapNotNull { clusterIds ->
            val clusterArticles = clusterIds.mapNotNull { articleById[it] }
            if (clusterArticles.size < 2) return@mapNotNull null

            val representative = selectRepresentativeArticleForCluster(clusterArticles)
            val duplicates = clusterArticles
                .asSequence()
                .filterNot { it.id == representative.id }
                .mapNotNull { article ->
                    val score = pairScores[ArticlePairKey.of(representative.id, article.id)]
                        ?: return@mapNotNull null
                    article to score
                }
                .sortedByDescending { it.first.publishedAt }
                .toList()

            if (duplicates.isEmpty()) null else ArticleCluster(representative, duplicates)
        }.toMutableList()

        val clusteredIds = result
            .flatMap { cluster -> listOf(cluster.representative.id) + cluster.duplicates.map { it.first.id } }
            .toSet()
        articles
            .filterNot { it.id in clusteredIds }
            .forEach { result.add(ArticleCluster(it, emptyList())) }

        return result.sortedByDescending { it.representative.publishedAt }
    }

    fun selectRepresentativeArticleForCluster(articles: List<Article>): Article {
        return articles.minBy { it.publishedAt }
    }

    private fun Long.hasSimilarityToEveryArticleIn(
        cluster: Set<Long>,
        pairScores: Map<ArticlePairKey, Float>
    ): Boolean {
        return cluster.all { articleId ->
            articleId == this || pairScores.containsKey(ArticlePairKey.of(this, articleId))
        }
    }

    private fun Set<Long>.canMergeWith(
        other: Set<Long>,
        pairScores: Map<ArticlePairKey, Float>
    ): Boolean {
        return all { leftId ->
            other.all { rightId ->
                leftId == rightId || pairScores.containsKey(ArticlePairKey.of(leftId, rightId))
            }
        }
    }
}
