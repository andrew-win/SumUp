package com.andrewwin.sumup.domain.usecase.sources

import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.ImportedSourceGroup
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.settings.AppLanguage
import javax.inject.Inject

class AddSubscriptionUseCase @Inject constructor(
    private val sourceRepository: SourceRepository,
    private val articleRepository: ArticleRepository
) {
    suspend operator fun invoke(group: ImportedSourceGroup, language: AppLanguage) {
        sourceRepository.subscribeToImportedGroup(
            group = group,
            displayName = group.displayName(language)
        )
        articleRepository.requestFeedRefresh()
    }
}
