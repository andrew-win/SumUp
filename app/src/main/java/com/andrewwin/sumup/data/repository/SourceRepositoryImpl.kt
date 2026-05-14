package com.andrewwin.sumup.data.repository

import android.net.Uri
import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceGroupOrigin
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.RemoteArticleDataSource
import com.andrewwin.sumup.domain.source.SourceUrlNormalizer
import com.andrewwin.sumup.domain.source.SourceUrlValidator
import com.andrewwin.sumup.domain.news.ArticleContentCleaner
import com.andrewwin.sumup.domain.repository.ImportedSource
import com.andrewwin.sumup.domain.repository.ImportedSourceGroup
import com.andrewwin.sumup.domain.repository.SourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SourceRepositoryImpl @Inject constructor(
    private val sourceDao: SourceDao,
    private val remoteArticleDataSource: RemoteArticleDataSource,
    private val cleanArticleTextUseCase: ArticleContentCleaner
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
        val targetGroup = sourceDao.findGroupById(groupId) ?: return
        if (targetGroup.origin == SourceGroupOrigin.PUBLIC_SUBSCRIPTION) return

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
                cleanArticleTextUseCase.detectFooterPattern(sampleArticles.map { it.content })
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

    override suspend fun fetchGeneratedSourceName(url: String, type: SourceType): String {
        if (!SourceUrlValidator.isValid(url, type)) return ""
        val normalizedUrl = normalizeUrl(url, type)
        if (normalizedUrl.isBlank()) return ""
        return generateSourceName(normalizedUrl, type)
    }

    override suspend fun deleteSource(source: Source) {
        sourceDao.deleteSource(source)
    }

    override suspend fun getGroupsWithSourcesSnapshot(): List<GroupWithSources> =
        sourceDao.getGroupsWithSourcesOnce()

    override suspend fun subscribeToImportedGroup(
        group: ImportedSourceGroup,
        displayName: String
    ) {
        val normalizedGroupName = displayName.trim()
        if (normalizedGroupName.isBlank()) return

        val groupsSnapshot = sourceDao.getGroupsWithSourcesOnce()
        val existingGroupWithSources = if (group.sources.isEmpty()) {
            null
        } else {
            groupsSnapshot.firstOrNull { it.hasAllImportedSources(group) }
        }
        val targetGroupId = existingGroupWithSources?.group?.id ?: run {
            val existingGroup = sourceDao.findGroupByName(normalizedGroupName)
            if (existingGroup != null) {
                sourceDao.updateGroup(existingGroup.asPublicSubscription(group))
                existingGroup.id
            } else sourceDao.insertGroup(
                SourceGroup(
                    name = normalizedGroupName,
                    isEnabled = group.isEnabled,
                    isDeletable = group.isDeletable,
                    origin = SourceGroupOrigin.PUBLIC_SUBSCRIPTION,
                    subscriptionId = group.id
                )
            ).takeIf { it > 0L } ?: sourceDao.findGroupByName(normalizedGroupName)?.id ?: return
        }
        existingGroupWithSources?.group?.let { existingGroup ->
            if (existingGroup.origin != SourceGroupOrigin.PUBLIC_SUBSCRIPTION ||
                existingGroup.subscriptionId != group.id
            ) {
                sourceDao.updateGroup(existingGroup.asPublicSubscription(group))
            }
        }

        for (importedSource in group.sources) {
            upsertImportedSource(targetGroupId, importedSource)
        }
    }

    override suspend fun markImportedGroupsAsSubscriptions(groups: List<ImportedSourceGroup>) {
        if (groups.isEmpty()) return
        val groupsSnapshot = sourceDao.getGroupsWithSourcesOnce()
        groups.forEach { importedGroup ->
            val matchingGroup = groupsSnapshot.firstOrNull { groupWithSources ->
                groupWithSources.hasAllImportedSources(importedGroup)
            }?.group ?: return@forEach
            if (matchingGroup.origin != SourceGroupOrigin.PUBLIC_SUBSCRIPTION ||
                matchingGroup.subscriptionId != importedGroup.id
            ) {
                sourceDao.updateGroup(matchingGroup.asPublicSubscription(importedGroup))
            }
        }
    }

    override suspend fun syncSubscribedImportedGroups(groups: List<ImportedSourceGroup>) {
        if (groups.isEmpty()) return
        val importedGroupsById = groups.associateBy { it.id.trim().lowercase() }
        if (importedGroupsById.isEmpty()) return

        val groupsSnapshot = sourceDao.getGroupsWithSourcesOnce()
        val syncedGroupIds = mutableSetOf<Long>()

        groupsSnapshot.forEach { groupWithSources ->
            val subscriptionId = groupWithSources.group.subscriptionId?.trim()?.lowercase() ?: return@forEach
            val importedGroup = importedGroupsById[subscriptionId] ?: return@forEach
            replaceExistingGroupWithImportedSubscription(groupWithSources, importedGroup)
            syncedGroupIds += groupWithSources.group.id
        }

        groups.forEach { importedGroup ->
            val matchingGroup = groupsSnapshot.firstOrNull { groupWithSources ->
                groupWithSources.group.id !in syncedGroupIds &&
                    groupWithSources.isLegacyMatchForImportedGroup(importedGroup)
            } ?: return@forEach
            replaceExistingGroupWithImportedSubscription(matchingGroup, importedGroup)
            syncedGroupIds += matchingGroup.group.id
        }
    }

    override suspend fun unsubscribeFromImportedGroup(group: ImportedSourceGroup) {
        if (group.sources.isEmpty()) return

        val sourcesToRemove = group.sources
            .mapNotNull { importedSource ->
                val normalizedUrl = normalizeUrl(importedSource.url, importedSource.type)
                normalizedUrl.takeIf { it.isNotBlank() }?.let { importedSource.type to it }
            }
            .toSet()
        if (sourcesToRemove.isEmpty()) return

        val groupsSnapshot = sourceDao.getGroupsWithSourcesOnce()
        val matchingGroups = groupsSnapshot.filter { groupWithSources ->
            groupWithSources.sources.any { source ->
                source.type to source.url in sourcesToRemove
            }
        }

        matchingGroups.forEach { groupWithSources ->
            groupWithSources.sources
                .filter { source -> source.type to source.url in sourcesToRemove }
                .forEach { source -> sourceDao.deleteSource(source) }

            val remainingSources = sourceDao.getSourcesByGroupIdOnce(groupWithSources.group.id)
            if (remainingSources.isEmpty()) {
                sourceDao.deleteGroupById(groupWithSources.group.id)
            }
        }
    }

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
                        isDeletable = existingGroup.isDeletable,
                        origin = group.origin ?: existingGroup.origin,
                        subscriptionId = group.subscriptionId ?: existingGroup.subscriptionId
                    )
                )
                existingGroup.id
            } else {
                val insertedId = sourceDao.insertGroup(
                    SourceGroup(
                        name = normalizedGroupName,
                        isEnabled = group.isEnabled,
                        isDeletable = group.isDeletable,
                        origin = group.origin ?: SourceGroupOrigin.USER,
                        subscriptionId = group.subscriptionId
                    )
                )
                if (insertedId > 0L) insertedId else sourceDao.findGroupByName(normalizedGroupName)?.id ?: continue
            }

            for (importedSource in group.sources) {
                upsertImportedSource(targetGroupId, importedSource)
            }
        }
    }

    private suspend fun replaceExistingGroupWithImportedSubscription(
        existingGroupWithSources: GroupWithSources,
        importedGroup: ImportedSourceGroup
    ) {
        val existingGroup = existingGroupWithSources.group
        if (existingGroup.origin != SourceGroupOrigin.PUBLIC_SUBSCRIPTION ||
            existingGroup.subscriptionId != importedGroup.id ||
            existingGroup.isDeletable != importedGroup.isDeletable
        ) {
            sourceDao.updateGroup(existingGroup.asPublicSubscription(importedGroup))
        }

        val importedSourceKeys = importedGroup.sources
            .mapNotNull { it.normalizedSourceKey() }
            .toSet()
        existingGroupWithSources.sources
            .filter { it.type to it.url !in importedSourceKeys }
            .forEach { sourceDao.deleteSource(it) }

        importedGroup.sources.forEach { importedSource ->
            upsertImportedSource(existingGroup.id, importedSource)
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
            SourceType.RSS -> generateRssName(trimmed)
        }
    }

    private fun SourceGroup.asPublicSubscription(group: ImportedSourceGroup): SourceGroup =
        copy(
            isDeletable = group.isDeletable,
            origin = SourceGroupOrigin.PUBLIC_SUBSCRIPTION,
            subscriptionId = group.id
        )

    private fun GroupWithSources.hasAllImportedSources(group: ImportedSourceGroup): Boolean {
        if (group.sources.isEmpty()) return false
        return group.sources.all { importedSource ->
            val normalizedImportedUrl = normalizeUrl(importedSource.url, importedSource.type)
            sources.any { existing ->
                existing.type == importedSource.type &&
                    existing.url.equals(normalizedImportedUrl, ignoreCase = true)
            }
        }
    }

    private fun GroupWithSources.isLegacyMatchForImportedGroup(group: ImportedSourceGroup): Boolean {
        val normalizedGroupName = this.group.name.normalizedGroupName()
        val publicGroupNames = group.publicGroupNames()
        if (normalizedGroupName !in publicGroupNames) return false

        val importedSourceKeys = group.sources
            .mapNotNull { it.normalizedSourceKey() }
            .toSet()
        return importedSourceKeys.isNotEmpty() &&
            sources.any { source -> source.type to source.url in importedSourceKeys }
    }

    private fun ImportedSource.normalizedSourceKey(): Pair<SourceType, String>? {
        val normalizedUrl = normalizeUrl(url, type)
        return normalizedUrl.takeIf { it.isNotBlank() }?.let { type to it }
    }

    private fun ImportedSourceGroup.publicGroupNames(): Set<String> =
        listOf(name, nameUk, nameEn)
            .map { it.normalizedGroupName() }
            .filter { it.isNotBlank() }
            .toSet()

    private fun String.normalizedGroupName(): String = trim().lowercase()

    private suspend fun generateTelegramName(url: String): String {
        remoteArticleDataSource.fetchTelegramChannelDisplayName(url)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

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

    private suspend fun generateRssName(url: String): String {
        remoteArticleDataSource.fetchRssChannelDisplayName(url)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return generateHostBasedName(url)
    }

    private fun generateHostBasedName(url: String): String {
        val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
            .removePrefix("www.")
            .trim()
        return host.ifBlank { url.trim() }.ifBlank { "RSS" }
    }

}






