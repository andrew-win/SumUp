package com.andrewwin.sumup.domain.entities.settings

data class DeduplicationSettings(
    val isEnabled: Boolean,
    val strategy: DeduplicationStrategy,
    val localThreshold: Float,
    val cloudThreshold: Float,
    val minMentions: Int,
    val isHideSingleNewsEnabled: Boolean
)
