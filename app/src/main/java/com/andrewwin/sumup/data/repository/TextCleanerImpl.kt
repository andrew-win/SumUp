package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.domain.repository.TextCleaner
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

class TextCleanerImpl : TextCleaner {

    companion object {
        private const val NEWLINE_PLACEHOLDER = "___NWL___"
        private val WHITESPACE_REGEX = Regex("[ \t]+")
        private val MULTIPLE_NEWLINES_REGEX = Regex("\n{3,}")
    }

    override fun clean(text: String): String {
        if (text.isBlank()) return ""

        val unescaped = Parser.unescapeEntities(text, false)
            .replace("\n", " $NEWLINE_PLACEHOLDER ")

        val doc = Jsoup.parse(unescaped)

        doc.select("br").append(" $NEWLINE_PLACEHOLDER ")
        doc.select("p, div, li, h1, h2, h3, h4, h5, h6, tr")
            .prepend(" $NEWLINE_PLACEHOLDER ")
            .append(" $NEWLINE_PLACEHOLDER ")

        val rawText = doc.text()
            .replace(NEWLINE_PLACEHOLDER, "\n")

        return rawText.lines()
            .map { it.replace(WHITESPACE_REGEX, " ").trim() }
            .joinToString("\n")
            .replace(MULTIPLE_NEWLINES_REGEX, "\n\n")
            .trim()
    }
}
