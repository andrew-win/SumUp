package com.andrewwin.sumup.domain.entities.ai

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
        fun from(status: RemoteContentFetchStatus?): YoutubeSubtitleFetchSummary =
            when (status) {
                RemoteContentFetchStatus.YT_GENERATED -> YoutubeSubtitleFetchSummary(generatedCount = 1)
                RemoteContentFetchStatus.YT_MANUAL -> YoutubeSubtitleFetchSummary(manualCount = 1)
                RemoteContentFetchStatus.YT_FAILED -> YoutubeSubtitleFetchSummary(failedCount = 1)
                else -> YoutubeSubtitleFetchSummary()
            }
    }
}
