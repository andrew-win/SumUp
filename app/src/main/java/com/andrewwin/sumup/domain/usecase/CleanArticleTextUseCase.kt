package com.andrewwin.sumup.domain.usecase

import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.FooterCleaner
import com.andrewwin.sumup.domain.TextCleaner
import javax.inject.Inject

class CleanArticleTextUseCase @Inject constructor() {

    /**
     * Централізовано очищує текст статті.
     * @param text Сирий текст (HTML або текст з футерами)
     * @param type Тип джерела для специфічних правил
     * @param footerPattern Знайдений патерн футера (опціонально)
     */
    operator fun invoke(
        text: String,
        type: SourceType,
        footerPattern: String? = null
    ): String {
        if (text.isBlank()) return ""

        // 1. Базове очищення від HTML/захист переносів
        var cleaned = TextCleaner.clean(text)

        // 2. Видалення динамічно знайденого футера
        if (footerPattern != null) {
            cleaned = FooterCleaner.removeFooter(cleaned, footerPattern)
        }

        // 3. Видалення хештегів для всіх джерел + позначка спаму
        var spamFlag = false
        val hashtagsResult = removeHashtags(cleaned)
        cleaned = hashtagsResult.cleaned
        if (hashtagsResult.hasSpamTag) spamFlag = true

        // 4. Видалення телефонних номерів для всіх джерел + позначка спаму
        val phonesResult = removePhoneNumbers(cleaned)
        cleaned = phonesResult.cleaned
        if (phonesResult.hasPhone) spamFlag = true

        if (spamFlag) {
            cleaned = "$SPAM_MARKER\n$cleaned"
        }

        // 5. Специфічні правила для джерел
        cleaned = when (type) {
            SourceType.TELEGRAM -> cleanTelegramSpecifics(cleaned)
            SourceType.YOUTUBE -> cleanYouTubeSpecifics(cleaned)
            SourceType.RSS -> cleanRssSpecifics(cleaned)
        }

        return cleaned.trim()
    }

    private fun cleanTelegramSpecifics(text: String): String {
        // Видалення типових Telegram "Join us", "Subscribe" і т.д., якщо вони не потрапили в footerPattern
        return text.lines().filterNot { line ->
            val lower = line.lowercase()
            lower.contains("підписатися на наш") || 
            lower.contains("приєднатися до") ||
            lower.contains("t.me/") && line.length < 50
        }.joinToString("\n").trim()
    }

    private fun cleanYouTubeSpecifics(text: String): String {
        // Очищення описів YouTube від посилань на соцмережі в кінці
        return text.lines().takeWhile { line ->
            !line.lowercase().contains("instagram:") && 
            !line.lowercase().contains("facebook:") &&
            !line.lowercase().contains("twitter:")
        }.joinToString("\n").trim()
    }

    private fun cleanRssSpecifics(text: String): String {
        // RSS зазвичай уже добре очищений Readability4J, але можна відфільтрувати "Читати далі..."
        return text.replace(Regex("Читати далі.*", RegexOption.IGNORE_CASE), "").trim()
    }

    private fun removeHashtags(text: String): HashtagResult {
        val urls = mutableListOf<String>()
        var processed = text.replace(Regex("https?://\\S+")) { match ->
            val token = "__URL_${urls.size}__"
            urls.add(match.value)
            token
        }

        val tagRegex = Regex("(?<!\\w)#[\\p{L}\\p{N}_]+")
        val tags = tagRegex.findAll(processed).map { it.value.drop(1).lowercase() }.toList()
        val hasSpamTag = tags.any { it in SPAM_HASHTAGS }
        processed = processed.replace(tagRegex, " ")
        processed = processed
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .trim()

        urls.forEachIndexed { index, url ->
            processed = processed.replace("__URL_${index}__", url)
        }
        return HashtagResult(processed, hasSpamTag)
    }

    private fun removePhoneNumbers(text: String): PhoneResult {
        val phoneRegex = Regex("\\+?\\d[\\d\\s().-]{7,}\\d")
        var found = false
        val cleaned = text
            .lines()
            .map { line ->
                val replaced = line.replace(phoneRegex) { _ ->
                    found = true
                    " "
                }
                replaced.replace(Regex("[ \\t]{2,}"), " ").trim()
            }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()
        return PhoneResult(cleaned, found)
    }

    private data class HashtagResult(val cleaned: String, val hasSpamTag: Boolean)
    private data class PhoneResult(val cleaned: String, val hasPhone: Boolean)

    private companion object {
        private const val SPAM_MARKER = "[ad]"
        private val SPAM_HASHTAGS = setOf("реклама", "промо", "промокод", "ads", "ad", "advertising")
    }
}
