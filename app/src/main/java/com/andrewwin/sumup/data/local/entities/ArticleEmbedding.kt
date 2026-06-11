package com.andrewwin.sumup.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "article_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("embeddingType")
    ]
)
data class ArticleEmbedding(
    @PrimaryKey val articleId: Long,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val embedding: ByteArray,
    val embeddingType: String
)
