package com.andrewwin.sumup.data.news

import com.andrewwin.sumup.domain.news.ArticleContentCleaner
import com.andrewwin.sumup.domain.entities.source.SourceType
import com.andrewwin.sumup.domain.support.DispatcherProvider
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import javax.inject.Inject

class ArticleTextCleaner @Inject constructor(
    private val dispatcherProvider: DispatcherProvider
) : ArticleContentCleaner {

    override suspend fun detectFooterPattern(texts: List<String>): String? = withContext(dispatcherProvider.default) {
        if (texts.size < MIN_POSTS_FOR_ANALYSIS) return@withContext null
        val prepared = texts.map { cleanGaps(cleanHtml(it)) }.filter { it.isNotBlank() }
        if (prepared.size < MIN_POSTS_FOR_ANALYSIS) return@withContext null
        findCommonFooter(prepared)
    }

    override suspend fun extractMainContent(url: String, rawContent: String, type: SourceType): String = withContext(dispatcherProvider.default) {
        if (rawContent.isBlank()) return@withContext ""
        if (type != SourceType.RSS) return@withContext rawContent
        runCatching {
            val article = Readability4J(url, rawContent).parse()
            (article.content ?: article.textContent).orEmpty()
        }.getOrDefault(rawContent)
    }

    override suspend fun clean(
        text: String,
        type: SourceType,
        footerPattern: String?
    ): String = withContext(dispatcherProvider.default) {
        if (text.isBlank()) return@withContext ""

        val htmlCleaned = cleanHtml(text)
        val gapsCleaned = cleanGaps(htmlCleaned)
        val withoutFooter = removeFooter(gapsCleaned, footerPattern)

        withoutFooter.trim()
    }

    private fun cleanHtml(text: String): String {
        if (text.isBlank()) return ""
        val unescaped = Parser.unescapeEntities(text, false)
            .replace("\n", " $NEWLINE_PLACEHOLDER ")

        val doc = Jsoup.parse(unescaped)
        doc.select("br").append(" $NEWLINE_PLACEHOLDER ")
        doc.select("p, div, li, h1, h2, h3, h4, h5, h6, tr")
            .prepend(" $NEWLINE_PLACEHOLDER ")
            .append(" $NEWLINE_PLACEHOLDER ")

        return doc.text().replace(NEWLINE_PLACEHOLDER, "\n")
    }

    private fun cleanGaps(text: String): String {
        return text.lines()
            .map { it.replace(WHITESPACE_REGEX, " ").trim() }
            .joinToString("\n")
            .replace(MULTIPLE_NEWLINES_REGEX, "\n\n")
            .trim()
    }

    private fun findCommonFooter(texts: List<String>): String? {
        val normalizedPosts = texts.map { text ->
            text.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .takeLast(MAX_LINES_TO_SCAN)
                .map { normalizeFooter(it) }
                .filter { it.isNotBlank() }
        }
        if (normalizedPosts.all { it.isEmpty() }) return null

        val reversedPosts = normalizedPosts.map { it.asReversed() }
        val maxLines = reversedPosts.maxOf { it.size }
        val footerLines = mutableListOf<String>()
        for (offset in 0 until maxLines) {
            val linesAtPosition = mutableMapOf<String, Int>()
            var availablePosts = 0
            reversedPosts.forEach { lines ->
                val line = lines.getOrNull(offset) ?: return@forEach
                availablePosts++
                linesAtPosition[line] = linesAtPosition.getOrDefault(line, 0) + 1
            }
            if (availablePosts < MIN_POSTS_FOR_ANALYSIS) break
            val best = linesAtPosition.maxByOrNull { it.value } ?: break
            val frequency = best.value.toDouble() / availablePosts
            if (frequency >= MIN_FOOTER_OCCURRENCE_RATIO) {
                footerLines.add(0, best.key)
            } else {
                break
            }
        }
        return footerLines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun removeFooter(text: String, footerPattern: String?): String {
        if (text.isBlank() || footerPattern.isNullOrBlank()) return text
        val footerLines = footerPattern.lines()
            .map { normalizeFooter(it) }
            .filter { it.isNotBlank() }
        if (footerLines.isEmpty()) return text

        val originalLines = text.lines()
        val contentWithIndexes = originalLines
            .mapIndexed { index, line -> index to normalizeFooter(line) }
            .filter { it.second.isNotBlank() }
        if (contentWithIndexes.size < footerLines.size) return text

        for (offset in footerLines.indices) {
            val textLine = contentWithIndexes[contentWithIndexes.lastIndex - offset].second
            val footerLine = footerLines[footerLines.lastIndex - offset]
            if (textLine != footerLine) return text
        }

        val firstFooterIndex = contentWithIndexes[contentWithIndexes.size - footerLines.size].first
        return originalLines.subList(0, firstFooterIndex).joinToString("\n").trim()
    }

    private fun normalizeFooter(line: String): String {
        if (line.isBlank()) return ""
        val lowercase = line.lowercase()
        val masked = URL_REGEX.replace(lowercase, "url")
            .let { NUMBER_REGEX.replace(it, "num") }
        val normalized = masked.filter { it.isLetter() || it.isDigit() }
        return if (normalized.length >= MIN_PATTERN_LENGTH) normalized else ""
    }

    private companion object {
        private const val NEWLINE_PLACEHOLDER = "___NWL___"
        private val WHITESPACE_REGEX = Regex("[ \t]+")
        private val MULTIPLE_NEWLINES_REGEX = Regex("\n{3,}")
        private const val MIN_POSTS_FOR_ANALYSIS = 2
        private const val MIN_FOOTER_OCCURRENCE_RATIO = 0.3
        private const val MAX_LINES_TO_SCAN = 3
        private const val MIN_PATTERN_LENGTH = 3
        private val URL_REGEX = Regex("https?://\\S+|t\\.me/\\S+")
        private val NUMBER_REGEX = Regex("\\d+")
    }
}
