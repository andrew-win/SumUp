package com.andrewwin.sumup.data.repository

import android.net.Uri
import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.RemoteArticleDataSource
import com.andrewwin.sumup.domain.repository.ImportedSource
import com.andrewwin.sumup.domain.repository.ImportedSourceGroup
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.source.SourceUrlNormalizer
import com.andrewwin.sumup.domain.usecase.common.CleanArticleTextUseCase
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
        useHeadlessBrowser: Boolean,
        detectFooterPattern: Boolean
    ) {
        val normalizedName = name.trim()
        val normalizedUrl = normalizeUrl(url, type)
        if (normalizedUrl.isBlank()) return
        if (sourceDao.sourceExistsByTypeAndUrl(type, normalizedUrl)) return

        val existingNames = sourceDao.getGroupsWithSourcesOnce()
            .flatMap { it.sources }
            .map { it.name }
        val effectiveName = generateEffectiveName(
            explicitName = normalizedName,
            normalizedUrl = normalizedUrl,
            type = type,
            existingNames = existingNames
        )
        if (effectiveName.isBlank()) return

        val normalizedTitleSelector = normalizeSelector(titleSelector)
        val normalizedPostLinkSelector = normalizeSelector(postLinkSelector)
        val normalizedDescriptionSelector = normalizeSelector(descriptionSelector)
        val normalizedDateSelector = normalizeSelector(dateSelector)
        val sourceToInsert = Source(
            groupId = groupId,
            name = effectiveName,
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

        if (!detectFooterPattern) return

        val footerPattern = try {
            val sampleArticles = remoteArticleDataSource.fetchArticles(
                Source(
                    id = insertedId,
                    groupId = groupId,
                    name = effectiveName,
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
        val normalizedUrl = normalizeUrl(source.url, source.type)
        val existingNames = sourceDao.getGroupsWithSourcesOnce()
            .flatMap { it.sources }
            .filter { it.id != source.id }
            .map { it.name }
        val effectiveName = generateEffectiveName(
            explicitName = source.name.trim(),
            normalizedUrl = normalizedUrl,
            type = source.type,
            existingNames = existingNames
        )
        sourceDao.updateSource(
            source.copy(
                name = effectiveName,
                url = normalizedUrl,
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

    override suspend fun getGroupsWithSourcesSnapshot(): List<GroupWithSources> =
        sourceDao.getGroupsWithSourcesOnce()

    override suspend fun importGroupsWithSources(
        groups: List<ImportedSourceGroup>,
        merge: Boolean
    ) {
        if (!merge) {
            sourceDao.deleteAllSources()
            sourceDao.deleteDeletableGroups()
        }

        for (group in groups) {
            val normalizedGroupName = group.name.trim()
            if (normalizedGroupName.isBlank()) continue

            val existingGroup = sourceDao.findGroupByName(normalizedGroupName)
            val targetGroupId = if (existingGroup != null) {
                sourceDao.updateGroup(
                    existingGroup.copy(
                        isEnabled = group.isEnabled,
                        isDeletable = existingGroup.isDeletable
                    )
                )
                existingGroup.id
            } else {
                val insertedId = sourceDao.insertGroup(
                    SourceGroup(
                        name = normalizedGroupName,
                        isEnabled = group.isEnabled,
                        isDeletable = group.isDeletable
                    )
                )
                if (insertedId > 0L) insertedId else sourceDao.findGroupByName(normalizedGroupName)?.id ?: continue
            }

            for (importedSource in group.sources) {
                upsertImportedSource(targetGroupId, importedSource)
            }
        }
    }

    private suspend fun upsertImportedSource(groupId: Long, imported: ImportedSource) {
        val normalizedUrl = normalizeUrl(imported.url, imported.type)
        val normalizedName = imported.name.trim()
        if (normalizedUrl.isBlank() || normalizedName.isBlank()) return

        val normalizedTitleSelector = normalizeSelector(imported.titleSelector)
        val normalizedPostLinkSelector = normalizeSelector(imported.postLinkSelector)
        val normalizedDescriptionSelector = normalizeSelector(imported.descriptionSelector)
        val normalizedDateSelector = normalizeSelector(imported.dateSelector)

        val existing = sourceDao.findSourceByTypeAndUrl(imported.type, normalizedUrl)
        val updated = Source(
            id = existing?.id ?: 0L,
            groupId = groupId,
            name = normalizedName,
            url = normalizedUrl,
            type = imported.type,
            isEnabled = imported.isEnabled,
            footerPattern = imported.footerPattern?.trim()?.takeIf { it.isNotEmpty() },
            titleSelector = normalizedTitleSelector,
            postLinkSelector = normalizedPostLinkSelector,
            descriptionSelector = normalizedDescriptionSelector,
            dateSelector = normalizedDateSelector,
            useHeadlessBrowser = imported.useHeadlessBrowser
        )

        if (existing == null) {
            sourceDao.insertSource(updated)
        } else {
            sourceDao.updateSource(updated)
        }
    }

    private fun normalizeUrl(url: String, type: SourceType): String =
        SourceUrlNormalizer.normalize(url, type)

    private fun normalizeSelector(selector: String?): String? =
        selector?.trim()?.takeIf { it.isNotEmpty() }

    private suspend fun generateEffectiveName(
        explicitName: String,
        normalizedUrl: String,
        type: SourceType,
        existingNames: List<String>
    ): String {
        val baseName = explicitName.ifBlank {
            generateSourceName(normalizedUrl, type)
        }.trim()
        if (baseName.isBlank()) return ""

        val normalizedExisting = existingNames.map { it.trim().lowercase() }.toSet()
        if (baseName.lowercase() !in normalizedExisting) return baseName

        var index = 2
        while (true) {
            val candidate = "$baseName #$index"
            if (candidate.lowercase() !in normalizedExisting) return candidate
            index++
        }
    }

    private suspend fun generateSourceName(url: String, type: SourceType): String {
        val trimmed = url.trim()
        return when (type) {
            SourceType.TELEGRAM -> generateTelegramName(trimmed)
            SourceType.YOUTUBE -> generateYouTubeName(trimmed)
            SourceType.RSS -> generateHostBasedName(trimmed)
        }
    }

    private fun generateTelegramName(url: String): String {
        val cleaned = url
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("t.me/")
            .removePrefix("telegram.me/")
            .trim('/')
            .substringBefore('/')
            .removePrefix("@")
        return cleaned.ifBlank { "Telegram" }
    }

    private suspend fun generateYouTubeName(url: String): String {
        remoteArticleDataSource.fetchYouTubeChannelDisplayName(url)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val cleaned = url
            .removePrefix("https://")
            .removePrefix("http://")
            .trim('/')
        val tail = when {
            cleaned.startsWith("@") -> cleaned
            "youtube.com/" in cleaned -> cleaned.substringAfter("youtube.com/").trim('/').substringBefore('/')
            "youtu.be/" in cleaned -> cleaned.substringAfter("youtu.be/").trim('/').substringBefore('/')
            else -> cleaned.substringAfterLast('/').ifBlank { cleaned }
        }.trim()
        return tail.ifBlank { "YouTube" }
    }

    private fun generateHostBasedName(url: String): String {
        val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
            .removePrefix("www.")
            .trim()
        return host.ifBlank { url.trim() }.ifBlank { "RSS" }
    }

}






