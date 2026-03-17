package com.andrewwin.sumup.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "article_similarities",
    primaryKeys = ["representativeId", "articleId"],
    foreignKeys = [
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["representativeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("representativeId"),
        Index("articleId")
    ]
)
data class ArticleSimilarity(
    val representativeId: Long,
    val articleId: Long,
    val score: Float,
    val updatedAt: Long = System.currentTimeMillis()
)
