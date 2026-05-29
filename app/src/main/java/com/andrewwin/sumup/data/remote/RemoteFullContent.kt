package com.andrewwin.sumup.data.remote

import com.andrewwin.sumup.domain.entities.ai.RemoteContentFetchStatus

data class RemoteFullContent(
    val text: String?,
    val status: RemoteContentFetchStatus
)
