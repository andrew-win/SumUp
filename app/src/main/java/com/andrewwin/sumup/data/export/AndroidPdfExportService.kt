package com.andrewwin.sumup.data.export

import android.content.Context
import android.net.Uri
import com.andrewwin.sumup.domain.export.FeedExportArticle
import com.andrewwin.sumup.domain.export.PdfExportService
import com.andrewwin.sumup.domain.export.SummaryExportItem
import com.andrewwin.sumup.ui.util.PdfExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPdfExportService @Inject constructor(
    @ApplicationContext private val context: Context
) : PdfExportService {
    override suspend fun exportFeed(
        articles: List<FeedExportArticle>,
        uri: Uri,
        includeMedia: Boolean
    ): Result<Unit> {
        return PdfExporter.exportFeedToPdf(
            context = context,
            articles = articles,
            uri = uri,
            includeMedia = includeMedia
        )
    }

    override suspend fun exportSummaries(
        summaries: List<SummaryExportItem>,
        uri: Uri
    ): Result<Unit> {
        return PdfExporter.exportSummariesToPdf(
            context = context,
            summaries = summaries,
            uri = uri
        )
    }
}
