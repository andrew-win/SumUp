package com.andrewwin.sumup.data.repository

import android.util.Log
import com.andrewwin.sumup.data.local.dao.FeedClusterSnapshotDao
import com.andrewwin.sumup.data.local.entities.FeedClusterSnapshotEntity
import com.andrewwin.sumup.domain.article.Article
import com.andrewwin.sumup.domain.news.ArticleCluster
import com.andrewwin.sumup.domain.repository.FeedClusterSnapshotRepository
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedClusterSnapshotStore @Inject constructor(
    private val feedClusterSnapshotDao: FeedClusterSnapshotDao
) : FeedClusterSnapshotRepository {
    override suspend fun loadClusters(
        articles: List<Article>,
        clusteringSettingsSignature: String
    ): List<ArticleCluster>? {
        val startedAt = System.currentTimeMillis()
        val snapshot = feedClusterSnapshotDao.getSnapshot(FEED_CACHE_KEY)
            ?: return logMiss("snapshot_missing", startedAt, articles.size)
        val signatureStartedAt = System.currentTimeMillis()
        val articlesSignature = buildArticlesSignature(articles)
        val signatureDurationMs = System.currentTimeMillis() - signatureStartedAt
        if (snapshot.articlesSignature != articlesSignature) {
            return logMiss(
                reason = "snapshot_articles_signature_mismatch",
                startedAt = startedAt,
                articlesCount = articles.size,
                signatureDurationMs = signatureDurationMs
            )
        }
        if (snapshot.clusteringSettingsSignature != clusteringSettingsSignature) {
            return logMiss(
                reason = "snapshot_settings_signature_mismatch",
                startedAt = startedAt,
                articlesCount = articles.size,
                signatureDurationMs = signatureDurationMs
            )
        }

        val articleById = articles.associateBy { it.id }
        val decodeStartedAt = System.currentTimeMillis()
        val clusters = decodeClusters(snapshot.payloadJson, articleById)
            ?: return logMiss(
                reason = "snapshot_payload_invalid",
                startedAt = startedAt,
                articlesCount = articles.size,
                signatureDurationMs = signatureDurationMs
            )
        val decodeDurationMs = System.currentTimeMillis() - decodeStartedAt
        Log.d(FEED_STATES_LOG_TAG, "snapshot_hit articles=${articles.size} clusters=${clusters.size}")
        Log.d(
            FEED_BUILD_PROFILE_LOG_TAG,
            "snapshot_load hit=true durationMs=${System.currentTimeMillis() - startedAt} " +
                "signatureDurationMs=$signatureDurationMs decodeDurationMs=$decodeDurationMs " +
                "articles=${articles.size} clusters=${clusters.size} payloadChars=${snapshot.payloadJson.length}"
        )
        return clusters
    }

    override suspend fun saveClusters(
        articles: List<Article>,
        clusteringSettingsSignature: String,
        clusters: List<ArticleCluster>
    ) {
        val startedAt = System.currentTimeMillis()
        val encodeStartedAt = System.currentTimeMillis()
        val payloadJson = encodeClusters(clusters)
        val encodeDurationMs = System.currentTimeMillis() - encodeStartedAt
        val signatureStartedAt = System.currentTimeMillis()
        val articlesSignature = buildArticlesSignature(articles)
        val signatureDurationMs = System.currentTimeMillis() - signatureStartedAt
        val dbStartedAt = System.currentTimeMillis()
        feedClusterSnapshotDao.upsertSnapshot(
            FeedClusterSnapshotEntity(
                cacheKey = FEED_CACHE_KEY,
                articlesSignature = articlesSignature,
                clusteringSettingsSignature = clusteringSettingsSignature,
                payloadJson = payloadJson
            )
        )
        val dbDurationMs = System.currentTimeMillis() - dbStartedAt
        Log.d(FEED_STATES_LOG_TAG, "snapshot_saved articles=${articles.size} clusters=${clusters.size}")
        Log.d(
            FEED_BUILD_PROFILE_LOG_TAG,
            "snapshot_save durationMs=${System.currentTimeMillis() - startedAt} " +
                "encodeDurationMs=$encodeDurationMs signatureDurationMs=$signatureDurationMs dbDurationMs=$dbDurationMs " +
                "articles=${articles.size} clusters=${clusters.size} payloadChars=${payloadJson.length}"
        )
    }

    override suspend fun clearAll(reason: String) {
        feedClusterSnapshotDao.deleteAll()
        Log.d(FEED_STATES_LOG_TAG, "snapshot_cleared reason=$reason")
    }

    override fun buildArticlesSignature(articles: List<Article>): String {
        val payload = buildString(articles.size * 64) {
            articles.sortedBy { it.id }.forEach { article ->
                append(article.id)
                append('|')
                append(article.sourceId)
                append('|')
                append(article.publishedAt)
                append('|')
                append(article.title.trim())
                append('|')
                append(article.content.trim())
                append('\n')
            }
        }
        return sha1(payload)
    }

    override fun buildClusteringSettingsSignature(strategyKey: String, threshold: Float): String =
        sha1(
            "$strategyKey|${String.format(Locale.US, "%.4f", threshold)}|$SNAPSHOT_ALGORITHM_VERSION"
        )

    private fun encodeClusters(clusters: List<ArticleCluster>): String {
        val clustersJson = JSONArray()
        clusters.forEach { cluster ->
            val duplicatesJson = JSONArray()
            cluster.duplicates.forEach { (article, score) ->
                duplicatesJson.put(
                    JSONObject()
                        .put("id", article.id)
                        .put("score", score.toDouble())
                )
            }
            clustersJson.put(
                JSONObject()
                    .put("representativeId", cluster.representative.id)
                    .put("duplicates", duplicatesJson)
            )
        }
        return clustersJson.toString()
    }

    private fun decodeClusters(
        payloadJson: String,
        articleById: Map<Long, Article>
    ): List<ArticleCluster>? = runCatching {
        val result = mutableListOf<ArticleCluster>()
        val clustersJson = JSONArray(payloadJson)
        for (clusterIndex in 0 until clustersJson.length()) {
            val clusterJson = clustersJson.getJSONObject(clusterIndex)
            val representativeId = clusterJson.getLong("representativeId")
            val representative = articleById[representativeId] ?: return null
            val duplicatesJson = clusterJson.getJSONArray("duplicates")
            val duplicates = mutableListOf<Pair<Article, Float>>()
            for (duplicateIndex in 0 until duplicatesJson.length()) {
                val duplicateJson = duplicatesJson.getJSONObject(duplicateIndex)
                val duplicateArticle = articleById[duplicateJson.getLong("id")] ?: return null
                duplicates += duplicateArticle to duplicateJson.getDouble("score").toFloat()
            }
            result += ArticleCluster(representative, duplicates)
        }
        result
    }.getOrNull()

    private fun logMiss(
        reason: String,
        startedAt: Long,
        articlesCount: Int,
        signatureDurationMs: Long = 0L
    ): List<ArticleCluster>? {
        Log.d(FEED_STATES_LOG_TAG, "snapshot_miss reason=$reason")
        Log.d(
            FEED_BUILD_PROFILE_LOG_TAG,
            "snapshot_load hit=false reason=$reason durationMs=${System.currentTimeMillis() - startedAt} " +
                "signatureDurationMs=$signatureDurationMs articles=$articlesCount"
        )
        return null
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        private const val FEED_CACHE_KEY = "default_feed"
        private const val SNAPSHOT_ALGORITHM_VERSION = "feed-cluster-snapshot-v1"
        private const val FEED_STATES_LOG_TAG = "FeedStatesDebug"
        private const val FEED_BUILD_PROFILE_LOG_TAG = "FeedBuildProfile"
    }
}
