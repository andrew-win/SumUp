package com.andrewwin.sumup.domain.export.service

import com.andrewwin.sumup.domain.export.model.PdfExportRequest

interface PdfExportService {
    suspend fun export(request: PdfExportRequest): Result<Unit>
}
