package com.andrewwin.sumup.data.remote.sources.youtube

import com.andrewwin.sumup.domain.ai.model.RemoteContentFetchStatus
import io.github.thoroldvix.api.TranscriptContent

data class YouTubeTranscriptFetchResult(
    val transcriptContent: TranscriptContent,
    val status: RemoteContentFetchStatus
)
