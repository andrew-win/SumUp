package com.andrewwin.sumup.domain.source.usecase

import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import com.andrewwin.sumup.domain.source.repository.SourceRepository
import com.andrewwin.sumup.domain.source.model.SourceType
import javax.inject.Inject

data class AddSourceRequest(
    val groupId: Long,
    val name: String,
    val url: String,
    val type: SourceType,
    val titleSelector: String? = null,
    val postLinkSelector: String? = null,
    val descriptionSelector: String? = null,
    val dateSelector: String? = null,
    val useHeadlessBrowser: Boolean = false
)

class AddSourceUseCase @Inject constructor(
    private val sourceRepository: SourceRepository,
    private val articleRepository: ArticleRepository
) {
    suspend operator fun invoke(request: AddSourceRequest) {
        sourceRepository.addSource(
            groupId = request.groupId,
            name = request.name,
            url = request.url,
            type = request.type,
            titleSelector = request.titleSelector,
            postLinkSelector = request.postLinkSelector,
            descriptionSelector = request.descriptionSelector,
            dateSelector = request.dateSelector,
            useHeadlessBrowser = request.useHeadlessBrowser,
            detectFooterPattern = false
        )
        articleRepository.requestFeedRefresh()
    }
}
