package com.andrewwin.sumup.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(
            entity = Source::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sourceId"),
        Index("url", unique = true),
        Index("publishedAt")
    ]
)
data class Article(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val title: String,
    val content: String,
    val url: String,
    val publishedAt: Long,
    val viewCount: Long = 0,
    val isRead: Boolean = false,
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray? = null
)
