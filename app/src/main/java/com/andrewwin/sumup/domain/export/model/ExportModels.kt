package com.andrewwin.sumup.domain.export.model

data class FeedExportArticle(
    val title: String,
    val content: String,
    val sourceName: String?,
    val publishedAt: Long,
    val articleUrl: String,
    val mediaUrl: String?
)

data class SummaryExportItem(
    val content: String,
    val createdAt: Long,
    val strategy: SummaryExportStrategy
)

enum class SummaryExportStrategy {
    CLOUD,
    LOCAL,
    ADAPTIVE
}
