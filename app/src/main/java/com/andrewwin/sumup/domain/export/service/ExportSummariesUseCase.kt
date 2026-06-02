package com.andrewwin.sumup.domain.export.service

import com.andrewwin.sumup.domain.export.model.ExportDestination
import com.andrewwin.sumup.domain.export.model.SummariesPdfExportRequest
import com.andrewwin.sumup.domain.export.model.SummaryExportItem
import javax.inject.Inject

class ExportSummariesUseCase @Inject constructor(
    private val pdfExportService: PdfExportService
) {
    suspend operator fun invoke(
        summaries: List<SummaryExportItem>,
        destination: ExportDestination
    ): Result<Unit> {
        return pdfExportService.export(
            SummariesPdfExportRequest(
                summaries = summaries,
                destination = destination
            )
        )
    }
}
