package com.andrewwin.sumup.domain.feed.clustering

import com.andrewwin.sumup.domain.article.model.Article
import com.andrewwin.sumup.domain.feed.model.ArticleCluster
import com.andrewwin.sumup.domain.feed.model.ArticlePairKey
import com.andrewwin.sumup.domain.feed.model.ArticlePairScore

object FeedClusterCalculator {
    fun buildFinalClusters(
        articles: List<Article>,
        pairScores: Map<ArticlePairKey, Float>,
        threshold: Float
    ): List<ArticleCluster> {
        return buildFinalClusters(
            articles = articles,
            orderedPairScores = pairScores.toOrderedPairScores(threshold),
            pairScoreByKey = pairScores,
            threshold = threshold
        )
    }

    fun buildFinalClusters(
        articles: List<Article>,
        orderedPairScores: List<ArticlePairScore>,
        pairScoreByKey: Map<ArticlePairKey, Float>,
        threshold: Float
    ): List<ArticleCluster> {
        val articleById = articles.associateBy { it.id }
        val clustersByArticleId = mutableMapOf<Long, MutableSet<Long>>()
        val connectionIndex = orderedPairScores.toConnectionIndex(threshold)

        orderedPairScores
            .asSequence()
            .filter { it.score >= threshold }
            .forEach { pairScore ->
                val leftId = pairScore.leftArticleId
                val rightId = pairScore.rightArticleId
                val score = pairScore.score
                val leftArticle = articleById[leftId]
                val rightArticle = articleById[rightId]
                if (leftArticle == null || rightArticle == null) {
                    return@forEach
                }

                val leftCluster = clustersByArticleId[leftId]
                val rightCluster = clustersByArticleId[rightId]
                when {
                    leftCluster == null && rightCluster == null -> {
                        val cluster = mutableSetOf(leftId, rightId)
                        clustersByArticleId[leftId] = cluster
                        clustersByArticleId[rightId] = cluster
                    }

                    leftCluster != null && rightCluster == null -> {
                        val isAccepted = rightId.hasSimilarityToEveryArticleIn(leftCluster, connectionIndex)
                        if (isAccepted) {
                            leftCluster.add(rightId)
                            clustersByArticleId[rightId] = leftCluster
                        }
                    }

                    leftCluster == null && rightCluster != null -> {
                        val isAccepted = leftId.hasSimilarityToEveryArticleIn(rightCluster, connectionIndex)
                        if (isAccepted) {
                            rightCluster.add(leftId)
                            clustersByArticleId[leftId] = rightCluster
                        }
                    }

                    leftCluster != null && rightCluster != null && leftCluster !== rightCluster -> {
                        val canMerge = leftCluster.canMergeWith(rightCluster, connectionIndex)
                        if (canMerge) {
                            leftCluster.addAll(rightCluster)
                            rightCluster.forEach { clustersByArticleId[it] = leftCluster }
                        }
                    }

                    else -> Unit
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
                    val score = pairScoreByKey[ArticlePairKey.of(representative.id, article.id)]
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
            .forEach {
                result.add(ArticleCluster(it, emptyList()))
            }

        return result.sortedByDescending { it.representative.publishedAt }
    }

    fun selectRepresentativeArticleForCluster(articles: List<Article>): Article {
        return articles.minBy { it.publishedAt }
    }

    private fun Long.hasSimilarityToEveryArticleIn(
        cluster: Set<Long>,
        connectionIndex: Map<Long, Set<Long>>
    ): Boolean {
        return cluster.all { articleId ->
            articleId == this || connectionIndex[this]?.contains(articleId) == true
        }
    }

    private fun Set<Long>.canMergeWith(
        other: Set<Long>,
        connectionIndex: Map<Long, Set<Long>>
    ): Boolean {
        return all { leftId ->
            other.all { rightId ->
                leftId == rightId || connectionIndex[leftId]?.contains(rightId) == true
            }
        }
    }

    private fun List<ArticlePairScore>.toConnectionIndex(threshold: Float): Map<Long, Set<Long>> {
        val connectionIndex = HashMap<Long, MutableSet<Long>>(size * 2)
        forEach { pairScore ->
            if (pairScore.score >= threshold) {
                connectionIndex.getOrPut(pairScore.leftArticleId) { HashSet() }.add(pairScore.rightArticleId)
                connectionIndex.getOrPut(pairScore.rightArticleId) { HashSet() }.add(pairScore.leftArticleId)
            }
        }
        return connectionIndex
    }

    private fun Map<ArticlePairKey, Float>.toOrderedPairScores(threshold: Float): List<ArticlePairScore> {
        return entries
            .asSequence()
            .filter { it.value >= threshold }
            .sortedByDescending { it.value }
            .map { (pair, score) ->
                ArticlePairScore(
                    leftArticleId = pair.firstId,
                    rightArticleId = pair.secondId,
                    score = score
                )
            }
            .toList()
    }
}
