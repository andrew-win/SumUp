package com.andrewwin.sumup.domain

import org.jsoup.Jsoup
import org.jsoup.parser.Parser

object TextCleaner {
    fun clean(text: String): String {
        if (text.isBlank()) return ""
        
        // 1. Декодування HTML-сутностей (напр. &#8230; -> …)
        val unescaped = Parser.unescapeEntities(text, false)
        
        // 2. Видалення HTML-тегів
        val noHtml = Jsoup.parse(unescaped).text()
        
        // 3. Видалення зайвих пробілів та порожніх рядків
        return noHtml
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
