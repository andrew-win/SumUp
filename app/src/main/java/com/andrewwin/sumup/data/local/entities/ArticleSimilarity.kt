package com.andrewwin.sumup.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "article_similarities",
    primaryKeys = ["leftArticleId", "rightArticleId", "strategyKey"],
    foreignKeys = [
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["leftArticleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["rightArticleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("leftArticleId"),
        Index("rightArticleId"),
        Index("strategyKey")
    ]
)
data class ArticleSimilarity(
    val leftArticleId: Long,
    val rightArticleId: Long,
    val strategyKey: String,
    val score: Float,
    val updatedAt: Long = System.currentTimeMillis()
)






