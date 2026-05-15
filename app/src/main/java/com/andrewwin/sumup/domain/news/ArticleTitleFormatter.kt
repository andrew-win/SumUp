package com.andrewwin.sumup.domain.news

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import java.util.Locale
import javax.inject.Inject

class ArticleTitleFormatter @Inject constructor() {

    fun format(article: Article, sourceType: SourceType): Article {
        val title = when (sourceType) {
            SourceType.TELEGRAM -> buildTelegramTitle(article.content).ifBlank {
                ArticleTitleCleaner.clean(article.title)
            }
            SourceType.RSS,
            SourceType.YOUTUBE -> ArticleTitleCleaner.clean(article.title)
        }

        return article.copy(
            title = title,
            content = removeTitlePrefix(article.content, title)
        )
    }

    private fun buildTelegramTitle(content: String): String {
        val sentences = splitTitleSentences(content.lines())
        if (sentences.isEmpty()) {
            return ArticleTitleCleaner.clean(content).takeCompleteWords(MAX_TITLE_LENGTH)
        }

        val firstSentence = sentences.first()
        if (firstSentence.length > MAX_TITLE_LENGTH) {
            return firstSentence.takeCompleteWords(MAX_TITLE_LENGTH)
        }

        val title = StringBuilder(firstSentence)
        sentences.drop(1).forEach { sentence ->
            val separator = title.titleSentenceSeparator()
            val candidateLength = title.length + separator.length + sentence.length
            if (candidateLength <= MAX_TITLE_LENGTH) {
                title.append(separator).append(sentence)
            } else {
                return@forEach
            }
        }
        return title.toString()
    }

    private fun splitTitleSentences(lines: List<String>): List<String> {
        val text = lines
            .map(ArticleTitleCleaner::clean)
            .map { it.trimStart('-', '–', '—', '•', '·').trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
        val sentences = mutableListOf<String>()
        var startIndex = 0

        text.forEachIndexed { index, char ->
            val isLineBreak = char == '\n'
            val isSentencePunctuation = char == '.' || char == '!' || char == '?' || char == '…'
            if (!isLineBreak && !isSentencePunctuation) return@forEachIndexed
            if (char == '.' && isAbbreviationBeforeDot(text, index)) return@forEachIndexed

            val endIndex = if (isLineBreak) index else index + 1
            text.substring(startIndex, endIndex)
                .cleanTitleSentence()
                .takeIf { it.isNotBlank() }
                ?.let(sentences::add)
            startIndex = index + 1
        }

        text.substring(startIndex)
            .cleanTitleSentence()
            .takeIf { it.isNotBlank() }
            ?.let(sentences::add)

        return sentences
    }

    private fun removeTitlePrefix(content: String, title: String): String {
        if (content.isBlank() || title.isBlank()) return content.trim()
        val normalizedTitle = normalizeTitlePrefixForMatching(title)
        val lines = content.lines()

        lines.indices.forEach { index ->
            val normalizedSkipped = normalizeTitlePrefixForMatching(lines.take(index + 1).joinToString(" "))
            if (normalizedSkipped.equals(normalizedTitle, ignoreCase = true)) {
                return lines.drop(index + 1).joinToString("\n").trim()
            }
            if (normalizedSkipped.isNotBlank() &&
                !normalizedTitle.startsWith(normalizedSkipped, ignoreCase = true)
            ) {
                return content.trim()
            }
        }

        return content.trim()
    }

    private fun normalizeTitlePrefixForMatching(value: String): String =
        ArticleTitleCleaner.clean(value)
            .replace(Regex("[.!?…]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.cleanTitleSentence(): String =
        ArticleTitleCleaner.clean(this).trimStart('-', '–', '—', '•', '·').trim()

    private fun String.takeCompleteWords(maxLength: Int): String {
        val normalized = trim()
        if (normalized.length <= maxLength) return normalized

        val prefix = normalized.take(maxLength).trimEnd()
        val lastSpaceIndex = prefix.indexOfLast { it.isWhitespace() }
        val completeWords = if (lastSpaceIndex > 0) {
            prefix.take(lastSpaceIndex)
        } else {
            prefix
        }
        return completeWords.trimEnd(',', ':', ';', '—', '-', '–').trim()
    }

    private fun StringBuilder.titleSentenceSeparator(): String {
        val last = lastOrNull()
        return if (last == '.' || last == '!' || last == '?' || last == '…') " " else ". "
    }

    private fun isAbbreviationBeforeDot(text: String, dotIndex: Int): Boolean {
        val prefix = text.substring(0, dotIndex).lowercase(Locale.ROOT)
        if (TITLE_MULTI_DOT_ABBREVIATIONS.any { prefix.endsWith(it) }) {
            return true
        }
        val hasNextAbbreviationPart = dotIndex + 2 < text.length &&
            text[dotIndex + 1].isLetter() &&
            text[dotIndex + 2] == '.'
        if (hasNextAbbreviationPart) {
            return true
        }

        val tokenEnd = dotIndex
        var tokenStart = tokenEnd - 1
        while (tokenStart >= 0 && (text[tokenStart].isLetter() || text[tokenStart] == '-')) {
            tokenStart--
        }
        val token = text.substring(tokenStart + 1, tokenEnd).lowercase(Locale.ROOT)
        return token in TITLE_DOT_ABBREVIATIONS
    }

    private companion object {
        private const val MAX_TITLE_LENGTH = 150
        private val TITLE_MULTI_DOT_ABBREVIATIONS = setOf("e.g", "i.e")
        private val TITLE_DOT_ABBREVIATIONS = setOf(
            "грн", "коп", "дол", "руб", "євро", "тис", "млн", "млрд", "трлн",
            "вул", "просп", "бул", "пл", "обл", "р-н", "р", "м", "с", "смт",
            "ім", "ред", "напр", "тобто", "т", "д", "тд", "тп",
            "долл", "тыс", "ул", "г", "пгт", "им",
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc",
            "no", "inc", "ltd", "co", "corp", "usd", "eur", "uah", "rub",
            "k", "mln", "bn", "bln", "trn"
        )
    }
}
