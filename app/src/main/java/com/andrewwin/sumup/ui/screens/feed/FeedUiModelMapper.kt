package com.andrewwin.sumup.ui.screens.feed

import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.ArticleCluster
import com.andrewwin.sumup.domain.usecase.FormatArticleHeadlineUseCase
import com.andrewwin.sumup.ui.screens.feed.model.ArticleClusterUiModel
import com.andrewwin.sumup.ui.screens.feed.model.ArticleUiModel
import javax.inject.Inject

class FeedUiModelMapper @Inject constructor(
    private val formatArticleHeadlineUseCase: FormatArticleHeadlineUseCase
) {
    private var lastKey: Int = 0
    private var lastSourcesMap: Map<Long, Source> = emptyMap()
    private var lastGroupMap: Map<Long, SourceGroup> = emptyMap()

    fun map(
        clusters: List<ArticleCluster>,
        groupsWithSources: List<GroupWithSources>,
        ellipsis: String
    ): List<ArticleClusterUiModel> {
        val key = buildKey(groupsWithSources)
        val sourcesMap: Map<Long, Source>
        val groupMap: Map<Long, SourceGroup>

        if (key == lastKey) {
            sourcesMap = lastSourcesMap
            groupMap = lastGroupMap
        } else {
            sourcesMap = groupsWithSources.flatMap { it.sources }.associateBy { it.id }
            groupMap = groupsWithSources.map { it.group }.associateBy { it.id }
            lastKey = key
            lastSourcesMap = sourcesMap
            lastGroupMap = groupMap
        }

        val result = clusters.map { cluster ->
            ArticleClusterUiModel(
                representative = mapToUiModel(cluster.representative, sourcesMap, groupMap, ellipsis),
                duplicates = cluster.duplicates.map { (article, score) ->
                    mapToUiModel(article, sourcesMap, groupMap, ellipsis) to score
                }
            )
        }
        return result
    }

    private fun mapToUiModel(
        article: Article,
        sources: Map<Long, Source>,
        groups: Map<Long, SourceGroup>,
        ellipsis: String
    ): ArticleUiModel {
        val source = sources[article.sourceId]
        val group = source?.groupId?.let { groups[it] }
        val sourceType = source?.type ?: SourceType.RSS

        val formatted = formatArticleHeadlineUseCase(article, sourceType)

        return ArticleUiModel(
            article = article,
            sourceType = sourceType,
            displayTitle = formatted.displayTitle,
            displayContent = formatDescription(formatted.displayContent, ellipsis),
            sourceName = source?.name,
            groupName = group?.name
        )
    }

    private fun formatDescription(content: String, ellipsis: String): String {
        if (content.isBlank()) return ""
        val allLines = content.lines()

        var nonBlankCount = 0
        val limitedLines = ArrayList<String>()

        for (line in allLines) {
            if (line.isNotBlank()) {
                nonBlankCount++
            }
            if (nonBlankCount > MAX_DESCRIPTION_LINES) break
            limitedLines.add(line)
        }

        val result = limitedLines.joinToString("\n").trim()
        return if (nonBlankCount > MAX_DESCRIPTION_LINES || limitedLines.size < allLines.size) {
            "$result\n$ellipsis"
        } else {
            result
        }
    }

    companion object {
        private const val MAX_DESCRIPTION_LINES = 12
    }

    private fun buildKey(groupsWithSources: List<GroupWithSources>): Int {
        var result = groupsWithSources.size
        for (groupWithSources in groupsWithSources) {
            result = 31 * result + groupWithSources.group.id.hashCode()
            result = 31 * result + groupWithSources.group.isEnabled.hashCode()
            result = 31 * result + groupWithSources.sources.size
            for (source in groupWithSources.sources) {
                result = 31 * result + source.id.hashCode()
                result = 31 * result + source.isEnabled.hashCode()
                result = 31 * result + source.type.hashCode()
            }
        }
        return result
    }
}
