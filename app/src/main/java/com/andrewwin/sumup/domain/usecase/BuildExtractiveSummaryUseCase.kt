package com.andrewwin.sumup.domain.usecase

import android.content.Context
import com.andrewwin.sumup.R
import com.andrewwin.sumup.domain.ExtractiveSummarizer
import com.andrewwin.sumup.domain.usecase.ai.FormatExtractiveSummaryUseCase
import javax.inject.Inject

class BuildExtractiveSummaryUseCase @Inject constructor(
    private val context: Context,
    private val formatExtractiveSummaryUseCase: FormatExtractiveSummaryUseCase
) {
    operator fun invoke(
        headlines: List<String>,
        contentMap: Map<String, String>,
        topCount: Int = 5,
        sentencesPerArticle: Int = 5
    ): String {
        val centralHeadlines = ExtractiveSummarizer.getCentralHeadlines(headlines, topCount)
        val intro = context.getString(R.string.summary_extractive_intro)
        
        val summaryBody = centralHeadlines.mapIndexed { index, title ->
            val content = contentMap[title].orEmpty()
            val sentences = ExtractiveSummarizer.summarize(content, sentencesPerArticle)
            
            formatExtractiveSummaryUseCase.formatItem(
                title = title,
                sentences = sentences,
                isScheduledReport = true,
                index = index + 1
            )
        }.joinToString("\n\n")

        return "$intro\n\n$summaryBody"
    }
}
