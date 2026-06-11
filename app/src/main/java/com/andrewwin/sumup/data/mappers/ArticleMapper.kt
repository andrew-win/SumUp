package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.Article as RoomArticle
import com.andrewwin.sumup.domain.article.model.Article

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
    importanceScore = importanceScore
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
    importanceScore = importanceScore
)
