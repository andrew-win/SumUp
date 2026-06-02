package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.ArticleSimilarity
import com.andrewwin.sumup.domain.article.model.ArticleSimilarityRecord

fun ArticleSimilarity.toDomainRecord(): ArticleSimilarityRecord = ArticleSimilarityRecord(
    leftArticleId = leftArticleId,
    rightArticleId = rightArticleId,
    strategyKey = strategyKey,
    score = score,
    leftContentSignature = leftContentSignature,
    rightContentSignature = rightContentSignature,
    updatedAt = updatedAt
)

fun ArticleSimilarityRecord.toRoomEntity(): ArticleSimilarity = ArticleSimilarity(
    leftArticleId = leftArticleId,
    rightArticleId = rightArticleId,
    strategyKey = strategyKey,
    score = score,
    leftContentSignature = leftContentSignature,
    rightContentSignature = rightContentSignature,
    updatedAt = updatedAt
)
