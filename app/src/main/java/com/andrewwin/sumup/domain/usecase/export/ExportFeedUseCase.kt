package com.andrewwin.sumup.domain.usecase.export

import android.net.Uri
import com.andrewwin.sumup.domain.export.FeedExportArticle
import com.andrewwin.sumup.domain.export.PdfExportService
import javax.inject.Inject

class ExportFeedUseCase @Inject constructor(
    private val pdfExportService: PdfExportService
) {
    suspend operator fun invoke(
        articles: List<FeedExportArticle>,
        uri: Uri,
        includeMedia: Boolean
    ): Result<Unit> {
        return pdfExportService.exportFeed(
            articles = articles,
            uri = uri,
            includeMedia = includeMedia
        )
    }
}
