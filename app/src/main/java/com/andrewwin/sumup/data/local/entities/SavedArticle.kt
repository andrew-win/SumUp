package com.andrewwin.sumup.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_articles",
    indices = [Index(value = ["url"], unique = true)]
)
data class SavedArticle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val content: String,
    val mediaUrl: String? = null,
    val videoId: String? = null,
    val publishedAt: Long,
    val viewCount: Long = 0,
    val sourceName: String? = null,
    val groupName: String? = null,
    val savedAt: Long = System.currentTimeMillis(),
    val clusterKey: String? = null,
    val clusterScore: Float = 0f
)
