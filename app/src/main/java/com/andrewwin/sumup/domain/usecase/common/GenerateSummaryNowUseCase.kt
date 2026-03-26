package com.andrewwin.sumup.domain.usecase.common

import com.andrewwin.sumup.domain.support.DispatcherProvider
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface GenerateSummaryNowUseCase {
    suspend operator fun invoke(): Result<String>
}

class GenerateSummaryNowUseCaseImpl @Inject constructor(
    private val refreshArticlesUseCase: RefreshArticlesUseCase,
    private val generateSummaryUseCase: GenerateSummaryUseCase,
    private val dispatcherProvider: DispatcherProvider
) : GenerateSummaryNowUseCase {
    override suspend fun invoke(): Result<String> = withContext(dispatcherProvider.io) {
        runCatching {
            refreshArticlesUseCase()
            generateSummaryUseCase()
        }
    }
}









