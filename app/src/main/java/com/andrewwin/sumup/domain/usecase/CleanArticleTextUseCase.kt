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

        // 3. Специфічні правила для джерел
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
}
