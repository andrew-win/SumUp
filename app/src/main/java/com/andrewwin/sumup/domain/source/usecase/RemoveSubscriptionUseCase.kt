package com.andrewwin.sumup.domain.source.usecase

import com.andrewwin.sumup.domain.source.repository.ImportedSourceGroup
import com.andrewwin.sumup.domain.source.repository.SourceRepository
import javax.inject.Inject

class RemoveSubscriptionUseCase @Inject constructor(
    private val sourceRepository: SourceRepository
) {
    suspend operator fun invoke(group: ImportedSourceGroup) {
        sourceRepository.unsubscribeFromImportedGroup(group)
    }
}
