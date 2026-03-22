package com.andrewwin.sumup.domain.usecase

import com.andrewwin.sumup.data.local.entities.SourceType
import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import javax.inject.Inject

class CleanArticleTextUseCase @Inject constructor() {
    operator fun invoke(texts: List<String>): String? {
        if (texts.size < MIN_POSTS_FOR_ANALYSIS) return null
        val prepared = texts.map(::cleanBase).filter { it.isNotBlank() }
        if (prepared.size < MIN_POSTS_FOR_ANALYSIS) return null
        return findCommonFooterPattern(prepared)
    }

    fun extractMainContent(url: String, rawContent: String, type: SourceType): String {
        if (rawContent.isBlank()) return ""
        if (type != SourceType.RSS) return rawContent
        return runCatching {
            val article = Readability4J(url, rawContent).parse()
            (article.content ?: article.textContent).orEmpty()
        }.getOrDefault(rawContent)
    }

    operator fun invoke(
        text: String,
        type: SourceType,
        footerPattern: String? = null
    ): String {
        if (text.isBlank()) return ""

        var cleaned = cleanBase(text)
        cleaned = removeFooter(cleaned, footerPattern)
        val hashtagsResult = removeHashtags(cleaned)
        cleaned = hashtagsResult.cleaned
        val phonesResult = removePhoneNumbers(cleaned)
        cleaned = phonesResult.cleaned
        val isSpam = hashtagsResult.flagged || phonesResult.flagged
        if (isSpam) {
            cleaned = "$SPAM_MARKER\n$cleaned"
        }

        cleaned = when (type) {
            SourceType.TELEGRAM, SourceType.YOUTUBE -> cleanSocialSpecifics(cleaned)
            SourceType.RSS -> cleanRssSpecifics(cleaned)
        }

        return cleaned.trim()
    }

    private fun cleanBase(text: String): String {
        if (text.isBlank()) return ""
        val unescaped = Parser.unescapeEntities(text, false)
            .replace("\n", " $NEWLINE_PLACEHOLDER ")

        val doc = Jsoup.parse(unescaped)
        doc.select("br").append(" $NEWLINE_PLACEHOLDER ")
        doc.select("p, div, li, h1, h2, h3, h4, h5, h6, tr")
            .prepend(" $NEWLINE_PLACEHOLDER ")
            .append(" $NEWLINE_PLACEHOLDER ")

        return doc.text()
            .replace(NEWLINE_PLACEHOLDER, "\n")
            .lines()
            .map { it.replace(WHITESPACE_REGEX, " ").trim() }
            .joinToString("\n")
            .replace(MULTIPLE_NEWLINES_REGEX, "\n\n")
            .trim()
    }

    private fun findCommonFooterPattern(texts: List<String>): String? {
        val normalizedPosts = texts.map { text ->
            text.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .takeLast(MAX_LINES_TO_SCAN)
                .map { normalizeFooterLine(it) }
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
            .map { normalizeFooterLine(it) }
            .filter { it.isNotBlank() }
        if (footerLines.isEmpty()) return text

        val originalLines = text.lines()
        val contentWithIndexes = originalLines
            .mapIndexed { index, line -> index to normalizeFooterLine(line) }
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

    private fun cleanSocialSpecifics(text: String): String {
        val lines = text.lines()
        var cutIndex = lines.size
        for (i in lines.lastIndex downTo 0) {
            val line = lines[i].trim()
            val lower = line.lowercase()
            val isSocialLine = lower.contains("instagram:") ||
                lower.contains("facebook:") ||
                lower.contains("twitter:") ||
                lower.contains("t.me/") ||
                lower.contains("підписатися") ||
                lower.contains("приєднатися") ||
                URL_REGEX.containsMatchIn(lower)
            if (!isSocialLine && line.isNotBlank()) break
            cutIndex = i
        }
        return lines.subList(0, cutIndex).joinToString("\n").trim()
    }

    private fun cleanRssSpecifics(text: String): String {
        return text.replace(Regex("Читати далі.*", RegexOption.IGNORE_CASE), "").trim()
    }

    private fun removeHashtags(text: String): CleanResult {
        val tagRegex = Regex("(?<!\\w)#[\\p{L}\\p{N}_]+")
        val tags = tagRegex.findAll(text).map { it.value.drop(1).lowercase() }.toList()
        val hasSpamTag = tags.any { it in SPAM_HASHTAGS }
        val cleaned = normalizeText(text.replace(tagRegex, " "))
        return CleanResult(cleaned, hasSpamTag)
    }

    private fun removePhoneNumbers(text: String): CleanResult {
        val phoneRegex = Regex("\\+?\\d[\\d\\s().-]{7,}\\d")
        val found = phoneRegex.containsMatchIn(text)
        val cleaned = normalizeText(text.replace(phoneRegex, " "))
        return CleanResult(cleaned, found)
    }

    private fun normalizeText(text: String): String {
        return text.lines()
            .joinToString("\n") { it.replace(WHITESPACE_REGEX, " ").trim() }
            .replace(MULTIPLE_NEWLINES_REGEX, "\n\n")
            .trim()
    }

    private data class CleanResult(val cleaned: String, val flagged: Boolean)

    private fun normalizeFooterLine(line: String): String {
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
        private const val MIN_FOOTER_OCCURRENCE_RATIO = 0.5
        private const val MAX_LINES_TO_SCAN = 14
        private const val MIN_PATTERN_LENGTH = 3
        private val URL_REGEX = Regex("https?://\\S+|t\\.me/\\S+")
        private val NUMBER_REGEX = Regex("\\d+")
        private const val SPAM_MARKER = "[ad]"
        private val SPAM_HASHTAGS = setOf("реклама", "промо", "промокод", "ads", "ad", "advertising")
    }
}
