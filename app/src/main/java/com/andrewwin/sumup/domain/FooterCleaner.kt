package com.andrewwin.sumup.domain

object FooterCleaner {

    private const val MIN_FOOTER_OCCURRENCE_RATIO = 0.5 // Рядок має бути хоча б у половині постів

    /**
     * Знаходить спільний блок рядків у кінці постів.
     * Використовує статистичний підхід для ігнорування постів-винятків (реклама тощо).
     */
    fun findCommonFooter(texts: List<String>): String? {
        if (texts.size < 2) return null

        // Розбиваємо кожен пост на рядки (відфільтровуємо порожні)
        val postsLines = texts.map { it.lines().filter { line -> line.isNotBlank() } }
        if (postsLines.isEmpty()) return null

        val commonFooterLines = mutableListOf<String>()
        val maxLinesInAnyPost = postsLines.maxOf { it.size }

        // Йдемо з кінця постів (індекс i - це зміщення від останнього рядка)
        for (i in 1..maxLinesInAnyPost) {
            val linesAtThisPosition = mutableMapOf<String, Int>()
            
            postsLines.forEach { lines ->
                if (lines.size >= i) {
                    val line = lines[lines.size - i].trim()
                    linesAtThisPosition[line] = linesAtThisPosition.getOrDefault(line, 0) + 1
                }
            }

            // Знаходимо найпопулярніший рядок на цій позиції
            val mostFrequentEntry = linesAtThisPosition.maxByOrNull { it.value } ?: break
            val frequency = mostFrequentEntry.value.toDouble() / texts.size

            // Якщо рядок зустрічається досить часто - додаємо його до футера
            if (frequency >= MIN_FOOTER_OCCURRENCE_RATIO) {
                commonFooterLines.add(0, mostFrequentEntry.key)
            } else {
                // Як тільки ланцюжок однакових кінцівок перервався - зупиняємось
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
     * Видаляє футер, якщо він знайдений у кінці тексту.
     */
    fun removeFooter(text: String, footerPattern: String?): String {
        if (footerPattern.isNullOrBlank()) return text
        
        val trimmedText = text.trim()
        val footerLines = footerPattern.lines().filter { it.isNotBlank() }
        val textLines = trimmedText.lines().toMutableList()

        if (textLines.size < footerLines.size) return text

        // Перевіряємо, чи останні рядки тексту збігаються з паттерном футера
        var matches = true
        for (i in 1..footerLines.size) {
            val textLine = textLines[textLines.size - i].trim()
            val footerLine = footerLines[footerLines.size - i].trim()
            if (textLine != footerLine) {
                matches = false
                break
            }
        }

        return if (matches) {
            // Видаляємо відповідну кількість рядків з кінця
            repeat(footerLines.size) { 
                if (textLines.isNotEmpty() && textLines.last().isBlank() || matches) {
                    textLines.removeAt(textLines.size - 1) 
                }
            }
            textLines.joinToString("\n").trim()
        } else {
            text
        }
    }
}
