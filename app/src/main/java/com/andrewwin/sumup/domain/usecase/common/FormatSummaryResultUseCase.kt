package com.andrewwin.sumup.domain.usecase.common

import com.andrewwin.sumup.domain.usecase.ai.SummaryResult
import com.andrewwin.sumup.domain.support.SummarySourceMeta
import javax.inject.Inject

class FormatSummaryResultUseCase @Inject constructor() {
    operator fun invoke(result: SummaryResult): String {
        return buildString {
            when (result) {
                is SummaryResult.Digest -> {
                    result.themes.forEach { theme ->
                        append("${theme.title}\n")
                        theme.summary?.let { append("$it\n") }
                        theme.items.forEach { item ->
                            append("— ${item.text}\n")
                            item.sources.forEach { source ->
                                append("${SummarySourceMeta.PREFIX}${source.name}|${source.url}\n")
                            }
                        }
                        append("\n")
                    }
                }
                is SummaryResult.Compare -> {
                    appendMainAndDetails(result.main, result.points)
                }
                is SummaryResult.Single -> {
                    result.title?.let { append("${it}\n") }
                    appendMainAndDetails(result.main, result.points)
                    result.sources.forEach { source ->
                        append("${SummarySourceMeta.PREFIX}${source.name}|${source.url}\n")
                    }
                }
                is SummaryResult.QA -> {
                    result.question?.let { append("${it}\n\n") }
                    append("${result.shortAnswer}\n\n")
                    if (result.details.isNotEmpty()) {
                        append("Детальніше\n")
                        result.details.forEach { detail ->
                            append("— ${detail.text}\n")
                            detail.sources.forEach { source ->
                                append("${SummarySourceMeta.PREFIX}${source.name}|${source.url}\n")
                            }
                        }
                    }
                    result.sources.forEach { source ->
                        append("${SummarySourceMeta.PREFIX}${source.name}|${source.url}\n")
                    }
                }
                is SummaryResult.Error -> {
                    append("Помилка: ${result.message}")
                }
            }
        }.trim()
    }

    private fun StringBuilder.appendMainAndDetails(main: String?, details: List<com.andrewwin.sumup.domain.usecase.ai.SummaryItem>) {
        main?.takeIf { it.isNotBlank() }?.let {
            append("Основне\n")
            append("$it\n\n")
        }
        if (details.isNotEmpty()) {
            append("Детальніше\n")
            details.forEach { detail ->
                append("— ${detail.text}\n")
                detail.sources.forEach { source ->
                    append("${SummarySourceMeta.PREFIX}${source.name}|${source.url}\n")
                }
            }
        }
    }
}
