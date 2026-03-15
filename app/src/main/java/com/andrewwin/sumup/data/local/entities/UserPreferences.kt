package com.andrewwin.sumup.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AiStrategy {
    CLOUD, EXTRACTIVE, ADAPTIVE
}

@Entity(tableName = "user_preferences")
data class UserPreferences(
    @PrimaryKey val id: Int = 0,
    val aiStrategy: AiStrategy = AiStrategy.ADAPTIVE,
    val isScheduledSummaryEnabled: Boolean = false,
    val scheduledHour: Int = 8,
    val scheduledMinute: Int = 0,
    val lastWorkRunTimestamp: Long = 0,
    val isDeduplicationEnabled: Boolean = false,
    val deduplicationThreshold: Float = 0.6f,
    val minMentions: Int = 2,
    val isHideSingleNewsEnabled: Boolean = true,
    val modelPath: String? = null,
    val isImportanceFilterEnabled: Boolean = true,
    val extractiveSentencesInFeed: Int = 3,
    val extractiveSentencesInScheduled: Int = 5,
    val extractiveNewsInScheduled: Int = 10
)
