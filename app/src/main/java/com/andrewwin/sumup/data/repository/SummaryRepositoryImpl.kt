package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.mappers.toDomainModel
import com.andrewwin.sumup.data.mappers.toRoomEntity
import com.andrewwin.sumup.data.local.dao.PreparedScheduledSummaryDao
import com.andrewwin.sumup.data.local.dao.SummaryDao
import com.andrewwin.sumup.domain.repository.SummaryRepository
import com.andrewwin.sumup.domain.summary.ScheduledSummaryDraft
import com.andrewwin.sumup.domain.summary.SummaryRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SummaryRepositoryImpl @Inject constructor(
    private val summaryDao: SummaryDao,
    private val preparedScheduledSummaryDao: PreparedScheduledSummaryDao
) : SummaryRepository {

    override val allSummaries: Flow<List<SummaryRecord>> = summaryDao.getAllSummaries().map { items ->
        items.map { it.toDomainModel() }
    }

    override suspend fun insertSummary(summary: SummaryRecord) {
        summaryDao.insertSummary(summary.toRoomEntity())
    }

    override suspend fun getPreparedScheduledSummary(scheduledAt: Long): ScheduledSummaryDraft? =
        preparedScheduledSummaryDao.getPreparedSummary(scheduledAt)?.toDomainModel()

    override suspend fun upsertPreparedScheduledSummary(summary: ScheduledSummaryDraft) {
        preparedScheduledSummaryDao.upsertPreparedSummary(summary.toRoomEntity())
    }

    override suspend fun deletePreparedScheduledSummary(scheduledAt: Long) {
        preparedScheduledSummaryDao.deletePreparedSummary(scheduledAt)
    }

    override suspend fun deletePreparedScheduledSummariesBefore(scheduledAt: Long) {
        preparedScheduledSummaryDao.deletePreparedSummariesBefore(scheduledAt)
    }

    override suspend fun deleteSummaryById(summaryId: Long) {
        summaryDao.deleteSummaryById(summaryId)
    }

    override suspend fun deleteSummariesByIds(summaryIds: List<Long>) {
        if (summaryIds.isEmpty()) return
        summaryDao.deleteSummariesByIds(summaryIds)
    }

    override suspend fun deleteAllSummaries() {
        summaryDao.deleteAllSummaries()
    }

    override suspend fun setFavorite(summaryId: Long, isFavorite: Boolean) {
        summaryDao.setFavorite(summaryId, isFavorite)
    }
}






