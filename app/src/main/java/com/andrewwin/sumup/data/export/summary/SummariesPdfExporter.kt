package com.andrewwin.sumup.data.export.summary

import android.content.Context
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.export.common.PdfWriter
import com.andrewwin.sumup.domain.export.model.SummariesPdfExportRequest
import com.andrewwin.sumup.domain.export.model.SummaryExportStrategy
import com.andrewwin.sumup.domain.summary.model.SummarySourceMeta
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class SummariesPdfExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sourceMetaInlineRegex = Regex("${Regex.escape(SummarySourceMeta.PREFIX)}[^\\n]*")

    fun export(writer: PdfWriter, request: SummariesPdfExportRequest) {
        val dateFormat = SimpleDateFormat("HH:mm, dd MMMM yyyy", Locale.getDefault())
        writer.drawDocumentHeader(
            title = context.getString(R.string.summary_pdf_title),
            generatedAtText = context.getString(
                R.string.summary_pdf_generated,
                dateFormat.format(Date())
            )
        )

        request.summaries.forEachIndexed { index, summary ->
            writer.drawWrappedText(
                text = context.getString(R.string.summary_pdf_item_header, index + 1),
                paint = writer.titlePaint
            )
            writer.drawWrappedText(
                text = context.getString(
                    R.string.summary_pdf_item_meta,
                    dateFormat.format(Date(summary.createdAt)),
                    summary.strategy.toDisplayName()
                ),
                paint = writer.subtitlePaint
            )
            writer.drawWrappedText(
                text = cleanSummaryTextForPdf(summary.content),
                paint = writer.bodyPaint
            )
            writer.addVerticalSpace(writer.bodyLineHeight)
        }
    }

    private fun cleanSummaryTextForPdf(raw: String): String {
        val normalizedRaw = raw.replace(
            Regex("\\s*${Regex.escape(SummarySourceMeta.PREFIX)}"),
            "\n${SummarySourceMeta.PREFIX}"
        )
        return normalizedRaw
            .lines()
            .mapNotNull { line ->
                val trimmedLine = line.trimEnd()
                if (trimmedLine.trim().startsWith(SummarySourceMeta.PREFIX)) {
                    null
                } else {
                    trimmedLine.replace(sourceMetaInlineRegex, "").takeIf { it.isNotBlank() }
                }
            }
            .joinToString("\n")
    }

    private fun SummaryExportStrategy.toDisplayName(): String {
        return when (this) {
            SummaryExportStrategy.CLOUD -> context.getString(R.string.ai_strategy_cloud)
            SummaryExportStrategy.LOCAL -> context.getString(R.string.ai_strategy_local)
            SummaryExportStrategy.ADAPTIVE -> context.getString(R.string.ai_strategy_adaptive)
        }
    }
}
