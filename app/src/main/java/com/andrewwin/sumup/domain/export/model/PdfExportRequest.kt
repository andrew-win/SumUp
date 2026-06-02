package com.andrewwin.sumup.domain.export.model

import java.io.OutputStream

fun interface ExportDestination {
    fun openOutputStream(): OutputStream?
}

sealed interface PdfExportRequest {
    val destination: ExportDestination
}

data class FeedPdfExportRequest(
    val articles: List<FeedExportArticle>,
    override val destination: ExportDestination,
    val includeMedia: Boolean
) : PdfExportRequest

data class SummariesPdfExportRequest(
    val summaries: List<SummaryExportItem>,
    override val destination: ExportDestination
) : PdfExportRequest
