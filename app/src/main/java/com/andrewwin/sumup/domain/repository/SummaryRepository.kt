package com.andrewwin.sumup.domain.repository

import com.andrewwin.sumup.data.local.entities.Summary
import kotlinx.coroutines.flow.Flow

interface SummaryRepository {
    val allSummaries: Flow<List<Summary>>
    suspend fun insertSummary(summary: Summary)
}






