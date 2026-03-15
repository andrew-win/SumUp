package com.andrewwin.sumup.domain

object FooterCleaner {

    private const val MIN_FOOTER_OCCURRENCE_RATIO = 0.5 

    /**
     * Знаходить спільний блок рядків у кінці постів.
     * Ігнорує порожні рядки при пошуку патерна, щоб він був стійким до різної кількості відступів.
     */
    fun findCommonFooter(texts: List<String>): String? {
        if (texts.size < 3) return null

        // 1. Отримуємо лише змістовні рядки для кожного поста
        val postsLines = texts.map { it.lines().map { line -> line.trim() }.filter { line -> line.isNotBlank() } }
        val maxLinesInAnyPost = postsLines.maxOf { it.size }

        val commonFooterLines = mutableListOf<String>()

        for (i in 1..maxLinesInAnyPost) {
            val linesAtThisPosition = mutableMapOf<String, Int>()
            
            postsLines.forEach { lines ->
                if (lines.size >= i) {
                    val line = lines[lines.size - i]
                    linesAtThisPosition[line] = linesAtThisPosition.getOrDefault(line, 0) + 1
                }
            }

            val mostFrequentEntry = linesAtThisPosition.maxByOrNull { it.value } ?: break
            val frequency = mostFrequentEntry.value.toDouble() / texts.size

            if (frequency >= MIN_FOOTER_OCCURRENCE_RATIO) {
                commonFooterLines.add(0, mostFrequentEntry.key)
            } else {
                break
            }
        }

        return if (commonFooterLines.isNotEmpty()) {
            commonFooterLines.joinToString("\n")
        } else {
            null
        }
    }

    /**
     * Видаляє футер, ігноруючи порожні рядки між основним текстом та футером.
     */
    fun removeFooter(text: String, footerPattern: String?): String {
        if (footerPattern.isNullOrBlank()) return text
        
        val footerLines = footerPattern.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (footerLines.isEmpty()) return text

        val originalLines = text.lines().toMutableList()
        // Створюємо копію для пошуку, де відфільтровані порожні рядки, але зберігаємо індекси
        val contentWithIndexes = originalLines.mapIndexed { index, s -> index to s.trim() }
            .filter { it.second.isNotBlank() }

        if (contentWithIndexes.size < footerLines.size) return text

        // Перевіряємо збіг з кінця
        var matches = true
        for (i in 1..footerLines.size) {
            val textLine = contentWithIndexes[contentWithIndexes.size - i].second
            val footerLine = footerLines[footerLines.size - i]
            if (textLine != footerLine) {
                matches = false
                break
            }
        }

        return if (matches) {
            // Знаходимо індекс першого рядка футера в оригінальному списку
            val firstLineIndexInOriginal = contentWithIndexes[contentWithIndexes.size - footerLines.size].first
            // Видаляємо все починаючи з цього індексу до кінця
            val resultLines = originalLines.subList(0, firstLineIndexInOriginal)
            resultLines.joinToString("\n").trim()
        } else {
            text
        }
    }
}
