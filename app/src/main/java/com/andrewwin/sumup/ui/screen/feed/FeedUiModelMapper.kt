package com.andrewwin.sumup.ui.screen.feed

import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.service.ArticleCluster
import com.andrewwin.sumup.domain.usecase.common.FormatArticleHeadlineUseCase
import com.andrewwin.sumup.ui.screen.feed.model.ArticleClusterUiModel
import com.andrewwin.sumup.ui.screen.feed.model.ArticleUiModel
import javax.inject.Inject

class FeedUiModelMapper @Inject constructor(
    private val formatArticleHeadlineUseCase: FormatArticleHeadlineUseCase
) {
    fun map(
        clusters: List<ArticleCluster>,
        groupsWithSources: List<GroupWithSources>,
        ellipsis: String
    ): List<ArticleClusterUiModel> {
        val sourcesMap = groupsWithSources.flatMap { it.sources }.associateBy { it.id }
        val groupMap = groupsWithSources.map { it.group }.associateBy { it.id }

        return clusters.map { cluster ->
            ArticleClusterUiModel(
                representative = mapToUiModel(
                    article = cluster.representative,
                    sources = sourcesMap,
                    groups = groupMap,
                    ellipsis = ellipsis,
                    includeGroup = true
                ),
                duplicates = cluster.duplicates.map { (article, score) ->
                    mapToUiModel(
                        article = article,
                        sources = sourcesMap,
                        groups = groupMap,
                        ellipsis = ellipsis,
                        includeGroup = false
                    ) to score
                }
            )
        }
    }

    private fun mapToUiModel(
        article: Article,
        sources: Map<Long, Source>,
        groups: Map<Long, SourceGroup>,
        ellipsis: String,
        includeGroup: Boolean
    ): ArticleUiModel {
        val source = sources[article.sourceId]
        val group = source?.groupId?.let { groups[it] }
        val sourceType = source?.type ?: SourceType.RSS

        val formatted = formatArticleHeadlineUseCase(article, sourceType)

        return ArticleUiModel(
            article = article,
            sourceType = sourceType,
            displayTitle = formatted.displayTitle,
            displayContent = if (sourceType == SourceType.WEBSITE) "" else formatDescription(formatted.displayContent, ellipsis),
            sourceName = source?.name,
            groupName = if (includeGroup) group?.name else null
        )
    }

    private fun formatDescription(content: String, ellipsis: String): String {
        if (content.isBlank()) return ""
        val lines = content.lines()
        var count = 0
        val limited = lines.takeWhile { line ->
            if (line.isNotBlank()) count++
            count <= MAX_DESCRIPTION_LINES
        }
        val result = limited.joinToString("\n").trim()
        return if (count > MAX_DESCRIPTION_LINES || limited.size < lines.size) {
            "$result\n$ellipsis"
        } else {
            result
        }
    }

    companion object {
        private const val MAX_DESCRIPTION_LINES = 12
    }
}







