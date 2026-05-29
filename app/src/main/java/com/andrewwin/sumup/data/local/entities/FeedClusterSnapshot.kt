package com.andrewwin.sumup.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feed_cluster_snapshots")
data class FeedClusterSnapshot(
    @PrimaryKey val cacheKey: String,
    val articlesSignature: String,
    val clusteringSettingsSignature: String,
    val payloadJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)
