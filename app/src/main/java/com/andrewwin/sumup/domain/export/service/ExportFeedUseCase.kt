package com.andrewwin.sumup.domain.export.service

import com.andrewwin.sumup.domain.export.model.ExportDestination
import com.andrewwin.sumup.domain.export.model.FeedPdfExportRequest
import com.andrewwin.sumup.domain.export.model.FeedExportArticle
import javax.inject.Inject

class ExportFeedUseCase @Inject constructor(
    private val pdfExportService: PdfExportService
) {
    suspend operator fun invoke(
        articles: List<FeedExportArticle>,
        destination: ExportDestination,
        includeMedia: Boolean
    ): Result<Unit> {
        return pdfExportService.export(
            FeedPdfExportRequest(
                articles = articles,
                destination = destination,
                includeMedia = includeMedia
            )
        )
    }
}
