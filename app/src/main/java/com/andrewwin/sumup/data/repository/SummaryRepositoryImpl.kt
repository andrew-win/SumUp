package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.local.dao.PreparedScheduledSummaryDao
import com.andrewwin.sumup.data.local.dao.SummaryDao
import com.andrewwin.sumup.data.local.entities.PreparedScheduledSummary
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.domain.repository.SummaryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SummaryRepositoryImpl @Inject constructor(
    private val summaryDao: SummaryDao,
    private val preparedScheduledSummaryDao: PreparedScheduledSummaryDao
) : SummaryRepository {

    override val allSummaries: Flow<List<Summary>> = summaryDao.getAllSummaries()

    override suspend fun insertSummary(summary: Summary) {
        summaryDao.insertSummary(summary)
    }

    override suspend fun getPreparedScheduledSummary(scheduledAt: Long): PreparedScheduledSummary? =
        preparedScheduledSummaryDao.getPreparedSummary(scheduledAt)

    override suspend fun upsertPreparedScheduledSummary(summary: PreparedScheduledSummary) {
        preparedScheduledSummaryDao.upsertPreparedSummary(summary)
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






