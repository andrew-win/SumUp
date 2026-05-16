package com.andrewwin.sumup.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andrewwin.sumup.data.local.entities.PreparedScheduledSummary

@Dao
interface PreparedScheduledSummaryDao {
    @Query("SELECT * FROM prepared_scheduled_summaries WHERE scheduledAt = :scheduledAt LIMIT 1")
    suspend fun getPreparedSummary(scheduledAt: Long): PreparedScheduledSummary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreparedSummary(summary: PreparedScheduledSummary)

    @Query("DELETE FROM prepared_scheduled_summaries WHERE scheduledAt = :scheduledAt")
    suspend fun deletePreparedSummary(scheduledAt: Long)

    @Query("DELETE FROM prepared_scheduled_summaries WHERE scheduledAt < :scheduledAt")
    suspend fun deletePreparedSummariesBefore(scheduledAt: Long)
}
