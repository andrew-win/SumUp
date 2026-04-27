package com.andrewwin.sumup.domain.usecase.common

import com.andrewwin.sumup.domain.usecase.ai.SummaryResult
import javax.inject.Inject

class FormatSummaryResultUseCase @Inject constructor() {
    operator fun invoke(result: SummaryResult): String {
        return buildString {
            when (result) {
                is SummaryResult.Digest -> {
                    result.themes.forEach { theme ->
                        val emojiStr = theme.emojis.joinToString(" ")
                        append("### $emojiStr ${theme.title}\n\n")
                        theme.summary?.let { append("$it\n\n") }
                        theme.items.forEach { item ->
                            append("- ${item.text}\n")
                            if (item.sources.isNotEmpty()) {
                                val links = item.sources.joinToString(", ") { "[${it.name}](${it.url})" }
                                append("  *Джерела: $links*\n")
                            }
                        }
                        append("\n")
                    }
                }
                is SummaryResult.Compare -> {
                    if (result.common.isNotEmpty()) {
                        append("### Спільне\n\n")
                        result.common.forEach { fact ->
                            append("- ${fact.text}\n")
                            if (fact.sources.isNotEmpty()) {
                                val links = fact.sources.joinToString(", ") { "[${it.name}](${it.url})" }
                                append("  *Джерела: $links*\n")
                            }
                        }
                        append("\n")
                    }
                    if (result.unique.isNotEmpty()) {
                        append("### Унікальне\n\n")
                        result.unique.forEach { fact ->
                            append("- ${fact.text}\n")
                            if (fact.sources.isNotEmpty()) {
                                val links = fact.sources.joinToString(", ") { "[${it.name}](${it.url})" }
                                append("  *Джерела: $links*\n")
                            }
                        }
                        append("\n")
                    }
                }
                is SummaryResult.Single -> {
                    result.title?.let { append("## $it\n\n") }
                    result.points.forEach { point ->
                        append("- ${point.text}\n")
                        if (point.sources.isNotEmpty()) {
                            val links = point.sources.joinToString(", ") { "[${it.name}](${it.url})" }
                            append("  *Джерела: $links*\n")
                        }
                    }
                }
                is SummaryResult.QA -> {
                    append("### ${result.question}\n\n")
                    append("${result.shortAnswer}\n\n")
                    result.details.forEach { detail ->
                        append("- ${detail.text}\n")
                        if (detail.sources.isNotEmpty()) {
                            val links = detail.sources.joinToString(", ") { "[${it.name}](${it.url})" }
                            append("  *Джерела: $links*\n")
                        }
                    }
                }
                is SummaryResult.Error -> {
                    append("Помилка: ${result.message}")
                }
            }
        }.trim()
    }
}
