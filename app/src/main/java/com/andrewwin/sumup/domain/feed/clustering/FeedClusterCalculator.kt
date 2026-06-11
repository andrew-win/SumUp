package com.andrewwin.sumup.domain.feed.clustering

import android.util.Log
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
        logClusterBuildStart(articles, orderedPairScores, threshold)

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
                    logClusterDebug {
                        "pair_skip_missing score=${score.formatScore()} left=$leftId right=$rightId " +
                            "leftExists=${leftArticle != null} rightExists=${rightArticle != null}"
                    }
                    return@forEach
                }

                val leftCluster = clustersByArticleId[leftId]
                val rightCluster = clustersByArticleId[rightId]
                when {
                    leftCluster == null && rightCluster == null -> {
                        val cluster = mutableSetOf(leftId, rightId)
                        clustersByArticleId[leftId] = cluster
                        clustersByArticleId[rightId] = cluster
                        logClusterDebug {
                            "cluster_create score=${score.formatScore()} pair=${pairScore.describe(articleById)} " +
                                "cluster=${cluster.describeCluster(articleById)}"
                        }
                    }

                    leftCluster != null && rightCluster == null -> {
                        val isAccepted = rightId.hasSimilarityToEveryArticleIn(leftCluster, connectionIndex)
                        if (isAccepted) {
                            leftCluster.add(rightId)
                            clustersByArticleId[rightId] = leftCluster
                            logClusterDebug {
                                "cluster_attach_existing_right score=${score.formatScore()} pair=${pairScore.describe(articleById)} " +
                                    "target=${leftCluster.describeCluster(articleById)} " +
                                    "metrics=${rightId.describeStrictMembership(leftCluster, connectionIndex)}"
                            }
                        } else {
                            logClusterDebug {
                                "cluster_attach_rejected_right score=${score.formatScore()} pair=${pairScore.describe(articleById)} " +
                                    "target=${leftCluster.describeCluster(articleById)} " +
                                    "metrics=${rightId.describeStrictMembership(leftCluster, connectionIndex)}"
                            }
                        }
                    }

                    leftCluster == null && rightCluster != null -> {
                        val isAccepted = leftId.hasSimilarityToEveryArticleIn(rightCluster, connectionIndex)
                        if (isAccepted) {
                            rightCluster.add(leftId)
                            clustersByArticleId[leftId] = rightCluster
                            logClusterDebug {
                                "cluster_attach_existing_left score=${score.formatScore()} pair=${pairScore.describe(articleById)} " +
                                    "target=${rightCluster.describeCluster(articleById)} " +
                                    "metrics=${leftId.describeStrictMembership(rightCluster, connectionIndex)}"
                            }
                        } else {
                            logClusterDebug {
                                "cluster_attach_rejected_left score=${score.formatScore()} pair=${pairScore.describe(articleById)} " +
                                    "target=${rightCluster.describeCluster(articleById)} " +
                                    "metrics=${leftId.describeStrictMembership(rightCluster, connectionIndex)}"
                            }
                        }
                    }

                    leftCluster != null && rightCluster != null && leftCluster !== rightCluster -> {
                        val canMerge = leftCluster.canMergeWith(rightCluster, connectionIndex)
                        if (canMerge) {
                            val beforeLeft = leftCluster.toSet()
                            val beforeRight = rightCluster.toSet()
                            leftCluster.addAll(rightCluster)
                            rightCluster.forEach { clustersByArticleId[it] = leftCluster }
                            logClusterDebug {
                                "cluster_merge score=${score.formatScore()} pair=${pairScore.describe(articleById)} " +
                                    "left=${beforeLeft.describeCluster(articleById)} " +
                                    "right=${beforeRight.describeCluster(articleById)} " +
                                    "merged=${leftCluster.describeCluster(articleById)} " +
                                    "metrics=${beforeLeft.describeStrictMerge(beforeRight, connectionIndex)}"
                            }
                        } else {
                            logClusterDebug {
                                "cluster_merge_rejected score=${score.formatScore()} pair=${pairScore.describe(articleById)} " +
                                    "left=${leftCluster.describeCluster(articleById)} " +
                                    "right=${rightCluster.describeCluster(articleById)} " +
                                    "metrics=${leftCluster.describeStrictMerge(rightCluster, connectionIndex)}"
                            }
                        }
                    }

                    else -> {
                        logClusterDebug {
                            "pair_skip_same_cluster score=${score.formatScore()} pair=${pairScore.describe(articleById)} " +
                                "cluster=${leftCluster?.describeCluster(articleById).orEmpty()}"
                        }
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
                logClusterDebug { "cluster_singleton article=${it.describeArticle()} reason=no_cluster_match" }
            }

        logFinalClusters(result, articleById, pairScoreByKey)

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

    private fun Long.describeStrictMembership(
        cluster: Set<Long>,
        connectionIndex: Map<Long, Set<Long>>
    ): String {
        val missingLinks = cluster
            .filterNot { it == this || connectionIndex[this]?.contains(it) == true }
            .sorted()
        return if (missingLinks.isEmpty()) {
            "all_links_present clusterSize=${cluster.size}"
        } else {
            "missing_links=${missingLinks.joinToString()} clusterSize=${cluster.size}"
        }
    }

    private fun Set<Long>.describeStrictMerge(
        other: Set<Long>,
        connectionIndex: Map<Long, Set<Long>>
    ): String {
        val missingPairs = buildList<String> {
            this@describeStrictMerge.forEach { leftId ->
                other.forEach { rightId ->
                    if (leftId != rightId && connectionIndex[leftId]?.contains(rightId) != true) {
                        add("$leftId-$rightId")
                    }
                }
            }
        }
        return if (missingPairs.isEmpty()) {
            "all_cross_links_present leftSize=${size} rightSize=${other.size}"
        } else {
            "missing_cross_links=${missingPairs.joinToString()} leftSize=${size} rightSize=${other.size}"
        }
    }

    private fun logClusterBuildStart(
        articles: List<Article>,
        orderedPairScores: List<ArticlePairScore>,
        threshold: Float
    ) {
        if (!CLUSTER_DEBUG_LOGS_ENABLED) return
        logClusterDebug {
            "cluster_build_start articles=${articles.size} pairScores=${orderedPairScores.size} threshold=${threshold.formatScore()}"
        }
        val articleById = articles.associateBy { it.id }
        orderedPairScores
            .forEachIndexed { index, pairScore ->
                logClusterDebug {
                    "pair_score[$index] score=${pairScore.score.formatScore()} pair=${pairScore.describe(articleById)}"
                }
            }
    }

    private fun logFinalClusters(
        clusters: List<ArticleCluster>,
        articleById: Map<Long, Article>,
        pairScores: Map<ArticlePairKey, Float>
    ) {
        if (!CLUSTER_DEBUG_LOGS_ENABLED) return
        clusters.forEachIndexed { index, cluster ->
            val duplicateDetails = cluster.duplicates.joinToString(separator = "; ") { (article, score) ->
                "${article.describeArticle()} score=${score.formatScore()}"
            }.ifBlank { "none" }
            val memberIds = listOf(cluster.representative.id) + cluster.duplicates.map { it.first.id }
            val memberPairScores = buildList {
                for (leftIndex in memberIds.indices) {
                    for (rightIndex in leftIndex + 1 until memberIds.size) {
                        val pairKey = ArticlePairKey.of(memberIds[leftIndex], memberIds[rightIndex])
                        add(
                            "${pairKey.describe(articleById)}=" +
                                "${pairScores[pairKey]?.formatScore() ?: "missing"}"
                        )
                    }
                }
            }.joinToString(separator = "; ").ifBlank { "none" }
            logClusterDebug {
                "cluster_final[$index] representative=${cluster.representative.describeArticle()} " +
                    "size=${memberIds.size} duplicates=$duplicateDetails pairs=$memberPairScores"
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

    private fun Set<Long>.describeCluster(articleById: Map<Long, Article>): String {
        return sorted().joinToString(prefix = "[", postfix = "]") { articleId ->
            articleById[articleId]?.describeArticle() ?: articleId.toString()
        }
    }

    private fun ArticlePairKey.describe(articleById: Map<Long, Article>): String {
        val left = articleById[firstId]?.describeArticle() ?: firstId.toString()
        val right = articleById[secondId]?.describeArticle() ?: secondId.toString()
        return "$left <-> $right"
    }

    private fun ArticlePairScore.describe(articleById: Map<Long, Article>): String {
        val left = articleById[leftArticleId]?.describeArticle() ?: leftArticleId.toString()
        val right = articleById[rightArticleId]?.describeArticle() ?: rightArticleId.toString()
        return "$left <-> $right"
    }

    private fun Article.describeArticle(): String {
        val normalizedTitle = title.replace('\n', ' ').trim()
        val shortTitle = if (normalizedTitle.length > MAX_TITLE_LOG_LENGTH) {
            normalizedTitle.take(MAX_TITLE_LOG_LENGTH) + "..."
        } else {
            normalizedTitle
        }
        return "$id:\"$shortTitle\""
    }

    private fun Float.formatScore(): String = String.format(java.util.Locale.US, "%.4f", this)

    private inline fun logClusterDebug(message: () -> String) {
        if (CLUSTER_DEBUG_LOGS_ENABLED) {
            Log.d(CLUSTER_DEBUG_LOG_TAG, message())
        }
    }

    private const val CLUSTER_DEBUG_LOGS_ENABLED = false
    private const val CLUSTER_DEBUG_LOG_TAG = "FeedClusterDebug"
    private const val MAX_TITLE_LOG_LENGTH = 80
}
