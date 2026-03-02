package com.andrewwin.sumup.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.andrewwin.sumup.data.local.entities.Summary
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {
    @Query("SELECT * FROM summaries ORDER BY createdAt DESC")
    fun getAllSummaries(): Flow<List<Summary>>

    @Insert
    suspend fun insertSummary(summary: Summary)

    @Query("DELETE FROM summaries")
    suspend fun deleteAllSummaries()
}
