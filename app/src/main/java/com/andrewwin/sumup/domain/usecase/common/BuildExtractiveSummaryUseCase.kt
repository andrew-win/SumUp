package com.andrewwin.sumup.domain.usecase.common

import com.andrewwin.sumup.domain.service.ExtractiveSummarizer
import com.andrewwin.sumup.domain.usecase.ai.FormatExtractiveSummaryUseCase
import com.andrewwin.sumup.domain.support.DispatcherProvider
import kotlinx.coroutines.withContext
import javax.inject.Inject

class BuildExtractiveSummaryUseCase @Inject constructor(
    private val formatExtractiveSummaryUseCase: FormatExtractiveSummaryUseCase,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(
        headlines: List<String>,
        contentMap: Map<String, String>,
        topCount: Int = 5,
        sentencesPerArticle: Int = 5
    ): String = withContext(dispatcherProvider.default) {
        val orderedHeadlines = headlines.take(topCount)
        val summaryBody = orderedHeadlines.map { title ->
            val content = contentMap[title].orEmpty()
            val sentences = ExtractiveSummarizer.summarize(content, sentencesPerArticle)
            
            formatExtractiveSummaryUseCase.formatItem(
                title = title,
                sentences = sentences,
                isScheduledReport = true,
                maxBullets = sentencesPerArticle
            )
        }.joinToString("\n\n")

        summaryBody
    }
}









