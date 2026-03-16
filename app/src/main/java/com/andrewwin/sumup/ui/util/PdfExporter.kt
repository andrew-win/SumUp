package com.andrewwin.sumup.ui.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.andrewwin.sumup.R
import com.andrewwin.sumup.ui.screens.feed.model.ArticleUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {
    suspend fun exportFeedToPdf(
        context: Context,
        articles: List<ArticleUiModel>,
        uri: Uri,
        includeMedia: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val document = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val margin = 32f
            val titlePaint = Paint().apply {
                textSize = 18f
                isFakeBoldText = true
            }
            val textPaint = Paint().apply {
                textSize = 12f
            }
            val subtitlePaint = Paint().apply {
                textSize = 10f
            }
            val lineHeight = textPaint.fontSpacing

            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas
            var y = margin

            val dateFormat = SimpleDateFormat("HH:mm, dd MMMM yyyy", Locale("uk", "UA"))
            val nowText = dateFormat.format(Date())
            val title = context.getString(R.string.feed_pdf_title)
            val generated = context.getString(R.string.feed_pdf_generated, nowText)

            canvas.drawText(title, margin, y, titlePaint)
            y += titlePaint.fontSpacing + 6f
            canvas.drawText(generated, margin, y, subtitlePaint)
            y += subtitlePaint.fontSpacing + 12f

            fun newPage() {
                document.finishPage(page)
                pageNumber += 1
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = margin
            }

            fun drawWrapped(text: String, paint: Paint) {
                var start = 0
                while (start < text.length) {
                    val count = paint.breakText(text, start, text.length, true, pageWidth - margin * 2, null)
                    canvas.drawText(text, start, start + count, margin, y, paint)
                    y += paint.fontSpacing
                    if (y > pageHeight - margin) newPage()
                    start += count
                }
            }

            fun drawImage(url: String) {
                runCatching {
                    val bitmap = URL(url).openStream().use { BitmapFactory.decodeStream(it) } ?: return@runCatching
                    val maxWidth = pageWidth - margin * 2
                    val scale = maxWidth / bitmap.width.toFloat()
                    val scaledHeight = bitmap.height * scale
                    if (y + scaledHeight > pageHeight - margin) newPage()
                    val left = margin
                    val top = y
                    val right = margin + maxWidth
                    val bottom = y + scaledHeight
                    canvas.drawBitmap(bitmap, null, android.graphics.RectF(left, top, right, bottom), null)
                    y += scaledHeight + lineHeight
                }
            }

            articles.forEach { article ->
                val header = context.getString(
                    R.string.feed_pdf_item_header,
                    article.displayTitle
                )
                drawWrapped(header, titlePaint)
                val source = article.sourceName.orEmpty()
                val time = dateFormat.format(Date(article.article.publishedAt))
                val meta = context.getString(R.string.feed_pdf_item_meta, source, time)
                drawWrapped(meta, subtitlePaint)
                if (includeMedia && !article.article.mediaUrl.isNullOrBlank()) {
                    drawImage(article.article.mediaUrl!!)
                }
                if (article.displayContent.isNotBlank()) {
                    drawWrapped(article.displayContent, textPaint)
                }
                y += lineHeight
                if (y > pageHeight - margin) newPage()
            }

            document.finishPage(page)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                document.writeTo(out)
            }
            document.close()
        }
    }
}
