package com.andrewwin.sumup.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prepared_scheduled_summaries")
data class PreparedScheduledSummary(
    @PrimaryKey val scheduledAt: Long,
    val content: String,
    val strategy: AiStrategy = AiStrategy.ADAPTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)
