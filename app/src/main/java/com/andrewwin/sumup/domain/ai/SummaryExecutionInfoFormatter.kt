package com.andrewwin.sumup.domain.ai

import android.content.Context
import com.andrewwin.sumup.R
import com.andrewwin.sumup.domain.settings.AiStrategy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SummaryExecutionInfoFormatter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun buildCloudInfo(
        strategy: AiStrategy,
        cloudResponse: CloudAiResponse,
        youtubeSubtitleSummary: YoutubeSubtitleFetchSummary = YoutubeSubtitleFetchSummary()
    ): SummaryExecutionInfo =
        SummaryExecutionInfo(
            label = buildLabel(strategy, cloudResponse.modelName, isCloud = true),
            note = appendYoutubeSubtitleSummary(
                note = buildCloudNote(cloudResponse.failedAttempts),
                youtubeSubtitleSummary = youtubeSubtitleSummary
            )
        )

    fun buildLocalInfo(
        strategy: AiStrategy,
        reason: LocalSummaryReason,
        youtubeSubtitleSummary: YoutubeSubtitleFetchSummary = YoutubeSubtitleFetchSummary()
    ): SummaryExecutionInfo =
        SummaryExecutionInfo(
            label = buildLabel(strategy, modelName = null, isCloud = false),
            note = appendYoutubeSubtitleSummary(
                note = context.getString(R.string.summary_execution_note_prefix, reason.text()),
                youtubeSubtitleSummary = youtubeSubtitleSummary
            )
        )

    fun buildLocalFallbackInfo(
        strategy: AiStrategy,
        failures: List<AiModelFailure>,
        youtubeSubtitleSummary: YoutubeSubtitleFetchSummary = YoutubeSubtitleFetchSummary()
    ): SummaryExecutionInfo =
        SummaryExecutionInfo(
            label = buildLabel(strategy, modelName = null, isCloud = false),
            note = appendYoutubeSubtitleSummary(
                note = buildCloudFailureNote(failures),
                youtubeSubtitleSummary = youtubeSubtitleSummary
            )
        )

    fun buildCloudFailureInfo(
        strategy: AiStrategy,
        failures: List<AiModelFailure>
    ): SummaryExecutionInfo =
        SummaryExecutionInfo(
            label = buildLabel(strategy, failures.firstOrNull()?.modelName, isCloud = true),
            note = ""
        )

    fun buildCloudFailureText(failures: List<AiModelFailure>): String =
        buildCloudFailureContent(failures)

    private fun buildLabel(strategy: AiStrategy, modelName: String?, isCloud: Boolean): String {
        val compactModel = modelName?.substringAfter('/')?.takeIf { it.isNotBlank() }
        return when (strategy) {
            AiStrategy.LOCAL -> context.getString(R.string.ai_execution_local)
            AiStrategy.CLOUD -> if (isCloud) {
                compactModel?.let { context.getString(R.string.ai_execution_cloud_model, it) }
                    ?: context.getString(R.string.ai_strategy_cloud)
            } else {
                context.getString(R.string.ai_execution_local)
            }
            AiStrategy.ADAPTIVE -> if (isCloud && compactModel != null) {
                context.getString(R.string.ai_execution_adaptive_cloud_model, compactModel)
            } else {
                context.getString(R.string.ai_execution_adaptive_local)
            }
        }
    }

    private fun buildCloudNote(failures: List<AiModelFailure>): String =
        if (failures.isEmpty()) {
            context.getString(
                R.string.summary_execution_note_prefix,
                context.getString(R.string.summary_execution_note_cloud_success)
            )
        } else {
            buildCloudFailureNote(failures)
        }

    private fun buildCloudFailureNote(failures: List<AiModelFailure>): String {
        if (failures.isEmpty()) {
            return listOf(
                context.getString(R.string.summary_execution_note_title),
                "${NOTE_LIST_MARKER} ${context.getString(R.string.summary_execution_note_cloud_failed_unknown)}"
            ).joinToString("\n")
        }
        return listOf(
            context.getString(R.string.summary_execution_note_title),
            buildCloudFailureDetails(failures)
        ).joinToString("\n")
    }

    private fun buildCloudFailureContent(failures: List<AiModelFailure>): String {
        if (failures.isEmpty()) {
            return "${NOTE_LIST_MARKER} ${context.getString(R.string.summary_execution_note_cloud_failed_unknown)}"
        }
        return buildCloudFailureDetails(failures)
    }

    private fun buildCloudFailureDetails(failures: List<AiModelFailure>): String =
        failures.joinToString("\n") { failure ->
            "${NOTE_LIST_MARKER} ${failure.configName}: ${failure.shortMessage()}"
        }

    private fun AiModelFailure.shortMessage(): String =
        when (code) {
            PAYMENT_REQUIRED_CODE -> context.getString(R.string.ai_error_short_payment_required)
            PAYLOAD_TOO_LARGE_CODE -> context.getString(R.string.ai_error_short_payload_too_large)
            RATE_LIMIT_CODE -> context.getString(R.string.ai_error_short_rate_limit)
            null -> context.getString(R.string.summary_execution_note_cloud_failed_unknown)
            else -> context.getString(R.string.ai_error_short_unknown_code, code)
        }

    private fun appendYoutubeSubtitleSummary(
        note: String,
        youtubeSubtitleSummary: YoutubeSubtitleFetchSummary
    ): String {
        val subtitleLines = buildYoutubeSubtitleLines(youtubeSubtitleSummary)
        if (subtitleLines.isEmpty()) return note
        return listOf(note, subtitleLines.joinToString("\n"))
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun buildYoutubeSubtitleLines(summary: YoutubeSubtitleFetchSummary): List<String> =
        buildList {
            if (summary.generatedCount > 0) {
                add(
                    "$NOTE_LIST_MARKER ${
                        context.getString(
                            R.string.youtube_subtitles_generated_count,
                            summary.generatedCount
                        )
                    }"
                )
            }
            if (summary.manualCount > 0) {
                add(
                    "$NOTE_LIST_MARKER ${
                        context.getString(
                            R.string.youtube_subtitles_manual_count,
                            summary.manualCount
                        )
                    }"
                )
            }
            if (summary.failedCount > 0) {
                add(
                    "$NOTE_LIST_MARKER ${
                        context.getString(
                            R.string.youtube_subtitles_failed_count,
                            summary.failedCount
                        )
                    }"
                )
            }
        }

    private companion object {
        private const val NOTE_LIST_MARKER = "—"
        private const val PAYMENT_REQUIRED_CODE = 402
        private const val PAYLOAD_TOO_LARGE_CODE = 413
        private const val RATE_LIMIT_CODE = 429
    }

    private fun LocalSummaryReason.text(): String =
        when (this) {
            LocalSummaryReason.SELECTED_LOCAL -> context.getString(R.string.summary_execution_note_local_selected)
            LocalSummaryReason.TEXT_TOO_SHORT -> context.getString(R.string.summary_execution_note_text_too_short)
            LocalSummaryReason.NO_API_KEYS -> context.getString(R.string.summary_execution_note_no_api_keys)
        }
}

enum class LocalSummaryReason {
    SELECTED_LOCAL,
    TEXT_TOO_SHORT,
    NO_API_KEYS
}
