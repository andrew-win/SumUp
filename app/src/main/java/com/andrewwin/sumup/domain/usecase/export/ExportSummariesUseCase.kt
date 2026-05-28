package com.andrewwin.sumup.domain.usecase.export

import android.net.Uri
import com.andrewwin.sumup.domain.export.PdfExportService
import com.andrewwin.sumup.domain.export.SummaryExportItem
import javax.inject.Inject

class ExportSummariesUseCase @Inject constructor(
    private val pdfExportService: PdfExportService
) {
    suspend operator fun invoke(
        summaries: List<SummaryExportItem>,
        uri: Uri
    ): Result<Unit> {
        return pdfExportService.exportSummaries(
            summaries = summaries,
            uri = uri
        )
    }
}
