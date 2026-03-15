package com.andrewwin.sumup.domain.usecase.ai

import android.content.Context
import com.andrewwin.sumup.R
import javax.inject.Inject

/**
 * Use case to format extractive summary items.
 * Handles title deduplication, consistent colon placement, and conditional dash usage.
 */
class FormatExtractiveSummaryUseCase @Inject constructor(
    private val context: Context
) {

    /**
     * @param title The article title
     * @param sentences Extracted sentences
     * @param isScheduledReport Whether this is for a scheduled report (affects dash usage)
     * @param index Optional index for numbering (e.g., "1. Title")
     */
    fun formatItem(
        title: String,
        sentences: List<String>,
        isScheduledReport: Boolean,
        index: Int? = null
    ): String {
        if (sentences.isEmpty()) return ""

        // 1. Deduplication: Skip sentences that are nearly identical to the title
        val filteredSentences = sentences.filter { sentence ->
            val cleanSentence = sentence.lowercase().trim().removeSuffix(".")
            val cleanTitle = title.lowercase().trim().removeSuffix(".")
            
            // If sentence is title or title is within sentence/vice versa, it might be a repeat
            !(cleanSentence == cleanTitle || 
              cleanSentence.contains(cleanTitle) && cleanTitle.length > 20 ||
              cleanTitle.contains(cleanSentence) && cleanSentence.length > 20)
        }.ifEmpty { 
            // If all were filtered, keep the longest one as a fallback
            listOf(sentences.maxBy { it.length }) 
        }

        // 2. Format Header: "1. Title:"
        val header = if (index != null) {
            context.getString(R.string.summary_extractive_item_header, index, title)
        } else {
            "$title:"
        }

        // 3. Format Body: Points from new line
        val useDash = isScheduledReport || filteredSentences.size > 1
        
        val body = if (useDash) {
            filteredSentences.joinToString("\n") { "- $it" }
        } else {
            filteredSentences.first()
        }

        return "$header\n$body"
    }
}
