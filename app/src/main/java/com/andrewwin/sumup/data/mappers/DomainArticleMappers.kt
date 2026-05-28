package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.dao.ArticleEmbedding
import com.andrewwin.sumup.data.local.entities.ArticleSimilarity
import com.andrewwin.sumup.data.local.entities.SavedArticle
import com.andrewwin.sumup.data.local.entities.Article as RoomArticle
import com.andrewwin.sumup.domain.article.Article
import com.andrewwin.sumup.domain.article.ArticleEmbeddingRecord
import com.andrewwin.sumup.domain.article.ArticleSimilarityRecord
import com.andrewwin.sumup.domain.article.SavedArticleSnapshot

fun RoomArticle.toDomainModel(): Article = Article(
    id = id,
    stableArticleKey = stableArticleKey,
    sourceId = sourceId,
    title = title,
    content = content,
    mediaUrl = mediaUrl,
    videoId = videoId,
    url = url,
    publishedAt = publishedAt,
    viewCount = viewCount,
    isRead = isRead,
    isFavorite = isFavorite,
    importanceScore = importanceScore,
    embedding = embedding,
    embeddingType = embeddingType
)

fun Article.toRoomEntity(): RoomArticle = RoomArticle(
    id = id,
    stableArticleKey = stableArticleKey,
    sourceId = sourceId,
    title = title,
    content = content,
    mediaUrl = mediaUrl,
    videoId = videoId,
    url = url,
    publishedAt = publishedAt,
    viewCount = viewCount,
    isRead = isRead,
    isFavorite = isFavorite,
    importanceScore = importanceScore,
    embedding = embedding,
    embeddingType = embeddingType
)

fun ArticleEmbedding.toDomainRecord(): ArticleEmbeddingRecord = ArticleEmbeddingRecord(
    id = id,
    embedding = embedding,
    embeddingType = embeddingType
)

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

fun SavedArticle.toDomainSnapshot(): SavedArticleSnapshot = SavedArticleSnapshot(
    id = id,
    url = url,
    title = title,
    content = content,
    mediaUrl = mediaUrl,
    videoId = videoId,
    publishedAt = publishedAt,
    viewCount = viewCount,
    sourceName = sourceName,
    groupName = groupName,
    savedAt = savedAt,
    clusterKey = clusterKey,
    clusterScore = clusterScore
)

fun SavedArticleSnapshot.toRoomEntity(): SavedArticle = SavedArticle(
    id = id,
    url = url,
    title = title,
    content = content,
    mediaUrl = mediaUrl,
    videoId = videoId,
    publishedAt = publishedAt,
    viewCount = viewCount,
    sourceName = sourceName,
    groupName = groupName,
    savedAt = savedAt,
    clusterKey = clusterKey,
    clusterScore = clusterScore
)
