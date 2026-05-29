package com.andrewwin.sumup.domain.usecase.sources

import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.entities.source.SourceType
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
