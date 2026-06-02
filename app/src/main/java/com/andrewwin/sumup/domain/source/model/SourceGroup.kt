package com.andrewwin.sumup.domain.source.model

data class SourceGroup(
    val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = true,
    val isDeletable: Boolean = true,
    val origin: SourceGroupOrigin = SourceGroupOrigin.USER,
    val subscriptionId: String? = null
)
