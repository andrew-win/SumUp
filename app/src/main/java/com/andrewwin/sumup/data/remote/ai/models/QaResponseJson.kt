package com.andrewwin.sumup.data.remote.ai.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QaResponseJson(
    @SerialName("short_answer") val shortAnswer: String? = null,
    val details: List<QaStatementJson> = emptyList()
)
