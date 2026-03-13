package com.andrewwin.sumup.domain

import org.jsoup.Jsoup
import org.jsoup.parser.Parser

object TextCleaner {
    private const val NEWLINE_PLACEHOLDER = "___NWL___"

    /**
     * Очищує текст від HTML-тегів, зберігаючи структуру абзаців та переноси рядків.
     */
    fun clean(text: String): String {
        if (text.isBlank()) return ""
        
        // 1. Декодування HTML-сутностей та захист існуючих переносів
        val unescaped = Parser.unescapeEntities(text, false)
            .replace("\n", " $NEWLINE_PLACEHOLDER ")
        
        val doc = Jsoup.parse(unescaped)
        
        // 2. Додавання маркерів переносу для структурних тегів
        doc.select("br").append(" $NEWLINE_PLACEHOLDER ")
        doc.select("p, div, li, h1, h2, h3, h4, h5, h6, tr").prepend(" $NEWLINE_PLACEHOLDER ").append(" $NEWLINE_PLACEHOLDER ")
        
        // 3. Отримання тексту з відновленням переносів
        val rawText = doc.text()
            .replace(NEWLINE_PLACEHOLDER, "\n")
        
        // 4. Фінальна нормалізація: видалення зайвих пробілів у рядках та зайвих порожніх рядків
        return rawText
            .lines()
            .map { it.replace(Regex("[ \t]+"), " ").trim() }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n") // Максимум два переноси поспіль (один порожній рядок)
            .trim()
    }
}
