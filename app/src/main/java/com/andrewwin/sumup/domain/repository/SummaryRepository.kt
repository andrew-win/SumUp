package com.andrewwin.sumup.domain.repository

import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.data.local.entities.PreparedScheduledSummary
import kotlinx.coroutines.flow.Flow

interface SummaryRepository {
    val allSummaries: Flow<List<Summary>>
    suspend fun insertSummary(summary: Summary)
    suspend fun getPreparedScheduledSummary(scheduledAt: Long): PreparedScheduledSummary?
    suspend fun upsertPreparedScheduledSummary(summary: PreparedScheduledSummary)
    suspend fun deletePreparedScheduledSummary(scheduledAt: Long)
    suspend fun deletePreparedScheduledSummariesBefore(scheduledAt: Long)
    suspend fun deleteSummaryById(summaryId: Long)
    suspend fun deleteSummariesByIds(summaryIds: List<Long>)
    suspend fun deleteAllSummaries()
    suspend fun setFavorite(summaryId: Long, isFavorite: Boolean)
}






