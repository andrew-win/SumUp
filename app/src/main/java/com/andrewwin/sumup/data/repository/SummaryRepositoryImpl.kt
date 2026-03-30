package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.local.dao.SummaryDao
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.domain.repository.SummaryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SummaryRepositoryImpl @Inject constructor(
    private val summaryDao: SummaryDao
) : SummaryRepository {

    override val allSummaries: Flow<List<Summary>> = summaryDao.getAllSummaries()

    override suspend fun insertSummary(summary: Summary) {
        summaryDao.insertSummary(summary)
    }

    override suspend fun deleteSummaryById(summaryId: Long) {
        summaryDao.deleteSummaryById(summaryId)
    }

    override suspend fun deleteSummariesByIds(summaryIds: List<Long>) {
        if (summaryIds.isEmpty()) return
        summaryDao.deleteSummariesByIds(summaryIds)
    }
}






