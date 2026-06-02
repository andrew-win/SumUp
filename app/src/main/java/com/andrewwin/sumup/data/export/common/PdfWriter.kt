package com.andrewwin.sumup.data.export.common

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument

class PdfWriter(
    private val document: PdfDocument
) {
    companion object {
        const val A4_PAGE_WIDTH = 595
        const val A4_PAGE_HEIGHT = 842
        const val PAGE_MARGIN = 32f
        const val TITLE_TEXT_SIZE = 18f
        const val BODY_TEXT_SIZE = 12f
        const val SUBTITLE_TEXT_SIZE = 10f
        const val HEADER_SPACING = 6f
        const val HEADER_BOTTOM_SPACING = 12f
    }

    val titlePaint = Paint().apply {
        textSize = TITLE_TEXT_SIZE
        isFakeBoldText = true
    }
    val bodyPaint = Paint().apply {
        textSize = BODY_TEXT_SIZE
    }
    val subtitlePaint = Paint().apply {
        textSize = SUBTITLE_TEXT_SIZE
    }
    val bodyLineHeight: Float = bodyPaint.fontSpacing

    private var pageNumber = 1
    private var page = createPage(pageNumber)
    private var canvas = page.canvas
    private var y = PAGE_MARGIN

    fun drawSingleLineText(text: String, paint: Paint) {
        ensureSpace(paint.fontSpacing)
        canvas.drawText(text, PAGE_MARGIN, y, paint)
        y += paint.fontSpacing
    }

    fun drawWrappedText(text: String, paint: Paint) {
        text.lines().forEach { line ->
            if (line.isBlank()) {
                addVerticalSpace(paint.fontSpacing)
            } else {
                var start = 0
                while (start < line.length) {
                    val count = paint.breakText(
                        line,
                        start,
                        line.length,
                        true,
                        A4_PAGE_WIDTH - PAGE_MARGIN * 2,
                        null
                    )
                    ensureSpace(paint.fontSpacing)
                    canvas.drawText(line, start, start + count, PAGE_MARGIN, y, paint)
                    y += paint.fontSpacing
                    start += count
                }
            }
        }
    }

    fun drawImage(bitmap: Bitmap) {
        val maxWidth = A4_PAGE_WIDTH - PAGE_MARGIN * 2
        val scale = maxWidth / bitmap.width.toFloat()
        val scaledHeight = bitmap.height * scale
        ensureSpace(scaledHeight)
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(PAGE_MARGIN, y, PAGE_MARGIN + maxWidth, y + scaledHeight),
            null
        )
        y += scaledHeight
        addVerticalSpace(bodyLineHeight)
    }

    fun addVerticalSpace(space: Float) {
        ensureSpace(space)
        y += space
    }

    fun drawDocumentHeader(title: String, generatedAtText: String) {
        drawSingleLineText(title, titlePaint)
        addVerticalSpace(HEADER_SPACING)
        drawSingleLineText(generatedAtText, subtitlePaint)
        addVerticalSpace(HEADER_BOTTOM_SPACING)
    }

    fun finish() {
        document.finishPage(page)
    }

    private fun ensureSpace(requiredHeight: Float) {
        if (y + requiredHeight <= A4_PAGE_HEIGHT - PAGE_MARGIN) return
        startNewPage()
    }

    private fun startNewPage() {
        document.finishPage(page)
        pageNumber += 1
        page = createPage(pageNumber)
        canvas = page.canvas
        y = PAGE_MARGIN
    }

    private fun createPage(number: Int): PdfDocument.Page {
        return document.startPage(
            PdfDocument.PageInfo.Builder(A4_PAGE_WIDTH, A4_PAGE_HEIGHT, number).create()
        )
    }
}
