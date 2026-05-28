package com.andrewwin.sumup.domain.usecase.sources

import com.andrewwin.sumup.domain.repository.ImportedSourceGroup
import com.andrewwin.sumup.domain.repository.SourceRepository
import javax.inject.Inject

class RemoveSubscriptionUseCase @Inject constructor(
    private val sourceRepository: SourceRepository
) {
    suspend operator fun invoke(group: ImportedSourceGroup) {
        sourceRepository.unsubscribeFromImportedGroup(group)
    }
}
