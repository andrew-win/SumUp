package com.andrewwin.sumup.data.export.feed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.export.common.PdfWriter
import com.andrewwin.sumup.domain.export.model.FeedPdfExportRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class FeedPdfExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun export(writer: PdfWriter, request: FeedPdfExportRequest) = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("HH:mm, dd MMMM yyyy", Locale.getDefault())
        writer.drawDocumentHeader(
            title = context.getString(R.string.feed_pdf_title),
            generatedAtText = context.getString(
                R.string.feed_pdf_generated,
                dateFormat.format(Date())
            )
        )

        request.articles.forEach { article ->
            writer.drawWrappedText(
                text = context.getString(R.string.feed_pdf_item_header, article.title),
                paint = writer.titlePaint
            )
            writer.drawWrappedText(
                text = context.getString(
                    R.string.feed_pdf_item_meta,
                    article.sourceName.orEmpty(),
                    dateFormat.format(Date(article.publishedAt))
                ),
                paint = writer.subtitlePaint
            )
            if (request.includeMedia && !article.mediaUrl.isNullOrBlank()) {
                loadBitmap(article.mediaUrl)?.let(writer::drawImage)
            }
            if (article.content.isNotBlank()) {
                writer.drawWrappedText(article.content, writer.bodyPaint)
            }
            writer.addVerticalSpace(writer.bodyLineHeight)
        }
    }

    private fun loadBitmap(url: String): Bitmap? {
        return runCatching {
            URL(url).openStream().use(BitmapFactory::decodeStream)
        }.getOrNull()
    }
}
