package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.datasource.RemoteArticleDataSource
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.usecase.CleanArticleTextUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SourceRepositoryImpl @Inject constructor(
    private val sourceDao: SourceDao,
    private val remoteArticleDataSource: RemoteArticleDataSource,
    private val cleanArticleTextUseCase: CleanArticleTextUseCase
) : SourceRepository {

    override val groupsWithSources: Flow<List<GroupWithSources>> = sourceDao.getGroupsWithSources()

    override suspend fun getSourcesByGroupId(groupId: Long): List<Source> =
        sourceDao.getSourcesByGroupId(groupId).first()

    override suspend fun getSourcesByIds(sourceIds: List<Long>): List<Source> =
        sourceDao.getSourcesByIds(sourceIds)

    override suspend fun addGroup(name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return
        if (sourceDao.groupExistsByName(normalizedName)) return
        sourceDao.insertGroup(SourceGroup(name = normalizedName))
    }

    override suspend fun updateGroup(group: SourceGroup) {
        sourceDao.updateGroup(group)
    }

    override suspend fun toggleGroup(group: SourceGroup, isEnabled: Boolean) {
        val updatedGroup = group.copy(isEnabled = isEnabled)
        sourceDao.updateGroup(updatedGroup)
        val sources = sourceDao.getSourcesByGroupId(group.id).first()
        sources.forEach { source ->
            sourceDao.updateSource(source.copy(isEnabled = isEnabled))
        }
    }

    override suspend fun deleteGroup(group: SourceGroup) {
        if (group.isDeletable) {
            sourceDao.deleteGroup(group)
        }
    }

    override suspend fun addSource(
        groupId: Long,
        name: String,
        url: String,
        type: SourceType,
        titleSelector: String?,
        postLinkSelector: String?,
        descriptionSelector: String?,
        dateSelector: String?,
        useHeadlessBrowser: Boolean
    ) {
        val normalizedName = name.trim()
        val normalizedUrl = normalizeUrl(url, type)
        if (normalizedName.isBlank() || normalizedUrl.isBlank()) return
        if (sourceDao.sourceExistsByTypeAndUrl(type, normalizedUrl)) return

        val normalizedTitleSelector = normalizeSelector(titleSelector)
        val normalizedPostLinkSelector = normalizeSelector(postLinkSelector)
        val normalizedDescriptionSelector = normalizeSelector(descriptionSelector)
        val normalizedDateSelector = normalizeSelector(dateSelector)
        val sourceToInsert = Source(
            groupId = groupId,
            name = normalizedName,
            url = normalizedUrl,
            type = type,
            footerPattern = null,
            titleSelector = normalizedTitleSelector,
            postLinkSelector = normalizedPostLinkSelector,
            descriptionSelector = normalizedDescriptionSelector,
            dateSelector = normalizedDateSelector,
            useHeadlessBrowser = useHeadlessBrowser
        )
        val insertedId = sourceDao.insertSource(sourceToInsert)
        if (insertedId <= 0L) return

        val footerPattern = try {
            val sampleArticles = remoteArticleDataSource.fetchArticles(
                Source(
                    id = insertedId,
                    groupId = groupId,
                    name = normalizedName,
                    url = normalizedUrl,
                    type = type,
                    titleSelector = normalizedTitleSelector,
                    postLinkSelector = normalizedPostLinkSelector,
                    descriptionSelector = normalizedDescriptionSelector,
                    dateSelector = normalizedDateSelector,
                    useHeadlessBrowser = useHeadlessBrowser
                )
            ).take(10)
            if (sampleArticles.size >= 2) {
                cleanArticleTextUseCase(sampleArticles.map { it.content })
            } else null
        } catch (e: Exception) {
            null
        }

        if (!footerPattern.isNullOrBlank()) {
            sourceDao.updateSource(sourceToInsert.copy(id = insertedId, footerPattern = footerPattern))
        }
    }

    override suspend fun updateSource(source: Source) {
        sourceDao.updateSource(
            source.copy(
                url = normalizeUrl(source.url, source.type),
                titleSelector = normalizeSelector(source.titleSelector),
                postLinkSelector = normalizeSelector(source.postLinkSelector),
                descriptionSelector = normalizeSelector(source.descriptionSelector),
                dateSelector = normalizeSelector(source.dateSelector)
            )
        )
    }

    override suspend fun deleteSource(source: Source) {
        sourceDao.deleteSource(source)
    }

    private fun normalizeUrl(url: String, type: SourceType): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return trimmed
        if (type != SourceType.RSS && type != SourceType.WEBSITE) return trimmed

        return when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> "https://${trimmed.removePrefix("http://")}"
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> "https://$trimmed"
        }
    }

    private fun normalizeSelector(selector: String?): String? =
        selector?.trim()?.takeIf { it.isNotEmpty() }
}
