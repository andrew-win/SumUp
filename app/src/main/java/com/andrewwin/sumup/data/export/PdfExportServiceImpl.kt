package com.andrewwin.sumup.data.export

import android.graphics.pdf.PdfDocument
import com.andrewwin.sumup.data.export.common.PdfWriter
import com.andrewwin.sumup.data.export.feed.FeedPdfExporter
import com.andrewwin.sumup.data.export.summary.SummariesPdfExporter
import com.andrewwin.sumup.domain.export.model.FeedPdfExportRequest
import com.andrewwin.sumup.domain.export.model.PdfExportRequest
import com.andrewwin.sumup.domain.export.model.SummariesPdfExportRequest
import com.andrewwin.sumup.domain.export.service.PdfExportService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfExportServiceImpl @Inject constructor(
    private val feedPdfExporter: FeedPdfExporter,
    private val summariesPdfExporter: SummariesPdfExporter
) : PdfExportService {
    override suspend fun export(request: PdfExportRequest): Result<Unit> {
        return runCatching {
            val document = PdfDocument()
            try {
                val writer = PdfWriter(document)
                when (request) {
                    is FeedPdfExportRequest -> feedPdfExporter.export(writer, request)
                    is SummariesPdfExportRequest -> summariesPdfExporter.export(writer, request)
                }
                writer.finish()
                request.destination.openOutputStream()?.use(document::writeTo)
            } finally {
                document.close()
            }
        }
    }
}
