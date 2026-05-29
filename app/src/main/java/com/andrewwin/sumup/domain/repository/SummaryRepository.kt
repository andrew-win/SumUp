package com.andrewwin.sumup.domain.repository

import com.andrewwin.sumup.domain.entities.summary.ScheduledSummaryDraft
import com.andrewwin.sumup.domain.entities.summary.SummaryRecord
import kotlinx.coroutines.flow.Flow

interface SummaryRepository {
    val allSummaries: Flow<List<SummaryRecord>>
    suspend fun insertSummary(summary: SummaryRecord)
    suspend fun getPreparedScheduledSummary(scheduledAt: Long): ScheduledSummaryDraft?
    suspend fun upsertPreparedScheduledSummary(summary: ScheduledSummaryDraft)
    suspend fun deletePreparedScheduledSummary(scheduledAt: Long)
    suspend fun deletePreparedScheduledSummariesBefore(scheduledAt: Long)
    suspend fun deleteSummaryById(summaryId: Long)
    suspend fun deleteSummariesByIds(summaryIds: List<Long>)
    suspend fun deleteAllSummaries()
    suspend fun setFavorite(summaryId: Long, isFavorite: Boolean)
}





