package com.andrewwin.sumup.domain.settings.model

data class DeduplicationSettings(
    val isEnabled: Boolean,
    val strategy: DeduplicationStrategy,
    val localThreshold: Float,
    val cloudThreshold: Float,
    val minMentions: Int,
    val isHideSingleNewsEnabled: Boolean
)
