package com.andrewwin.sumup.domain.export

import android.net.Uri

interface PdfExportService {
    suspend fun exportFeed(
        articles: List<FeedExportArticle>,
        uri: Uri,
        includeMedia: Boolean
    ): Result<Unit>

    suspend fun exportSummaries(
        summaries: List<SummaryExportItem>,
        uri: Uri
    ): Result<Unit>
}
