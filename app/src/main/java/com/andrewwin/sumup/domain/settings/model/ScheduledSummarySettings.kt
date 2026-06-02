package com.andrewwin.sumup.domain.settings.model

data class ScheduledSummarySettings(
    val isEnabled: Boolean,
    val isPushEnabled: Boolean,
    val hour: Int,
    val minute: Int,
    val times: List<ScheduledSummaryTime>,
    val lastWorkRunTimestamp: Long
)
