package com.andrewwin.sumup.data.remote.sources

import com.andrewwin.sumup.domain.ai.model.RemoteContentFetchStatus

data class RemoteFullContent(
    val text: String?,
    val status: RemoteContentFetchStatus
)
