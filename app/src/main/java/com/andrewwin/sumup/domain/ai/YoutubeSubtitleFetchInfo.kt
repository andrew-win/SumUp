package com.andrewwin.sumup.domain.ai

enum class YoutubeSubtitleFetchStatus {
    GENERATED,
    MANUAL,
    FAILED
}

data class YoutubeSubtitleFetchSummary(
    val generatedCount: Int = 0,
    val manualCount: Int = 0,
    val failedCount: Int = 0
) {
    val isEmpty: Boolean
        get() = generatedCount == 0 && manualCount == 0 && failedCount == 0

    operator fun plus(other: YoutubeSubtitleFetchSummary): YoutubeSubtitleFetchSummary =
        YoutubeSubtitleFetchSummary(
            generatedCount = generatedCount + other.generatedCount,
            manualCount = manualCount + other.manualCount,
            failedCount = failedCount + other.failedCount
        )

    companion object {
        fun from(status: YoutubeSubtitleFetchStatus?): YoutubeSubtitleFetchSummary =
            when (status) {
                YoutubeSubtitleFetchStatus.GENERATED -> YoutubeSubtitleFetchSummary(generatedCount = 1)
                YoutubeSubtitleFetchStatus.MANUAL -> YoutubeSubtitleFetchSummary(manualCount = 1)
                YoutubeSubtitleFetchStatus.FAILED -> YoutubeSubtitleFetchSummary(failedCount = 1)
                null -> YoutubeSubtitleFetchSummary()
            }
    }
}
