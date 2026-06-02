package com.andrewwin.sumup.domain.source.usecase

import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import com.andrewwin.sumup.domain.source.repository.ImportedSourceGroup
import com.andrewwin.sumup.domain.source.repository.SourceRepository
import com.andrewwin.sumup.domain.settings.model.AppLanguage
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
