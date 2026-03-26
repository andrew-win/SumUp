package com.andrewwin.sumup.domain.usecase.common

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.domain.support.DispatcherProvider
import com.andrewwin.sumup.domain.repository.SummaryRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface SaveSummaryUseCase {
    suspend operator fun invoke(content: String, strategy: AiStrategy)
}

class SaveSummaryUseCaseImpl @Inject constructor(
    private val summaryRepository: SummaryRepository,
    private val dispatcherProvider: DispatcherProvider
) : SaveSummaryUseCase {
    override suspend fun invoke(content: String, strategy: AiStrategy) = withContext(dispatcherProvider.io) {
        summaryRepository.insertSummary(Summary(content = content, strategy = strategy))
    }
}









