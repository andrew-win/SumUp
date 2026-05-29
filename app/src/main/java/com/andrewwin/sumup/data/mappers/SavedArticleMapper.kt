package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.SavedArticle
import com.andrewwin.sumup.domain.entities.article.SavedArticleSnapshot

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
