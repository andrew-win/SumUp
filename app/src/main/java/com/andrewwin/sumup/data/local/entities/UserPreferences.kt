package com.andrewwin.sumup.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preferences")
data class UserPreferences(
    @PrimaryKey val id: Int = 0,
    val isScheduledSummaryEnabled: Boolean = false,
    val scheduledHour: Int = 8,
    val scheduledMinute: Int = 0,
    val lastWorkRunTimestamp: Long = 0,
    val isDeduplicationEnabled: Boolean = false,
    val deduplicationThreshold: Float = 0.85f,
    val minMentions: Int = 2,
    val modelPath: String? = null
)
