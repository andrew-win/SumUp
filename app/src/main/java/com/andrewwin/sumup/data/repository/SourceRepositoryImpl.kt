package com.andrewwin.sumup.data.repository

import android.net.Uri
import com.andrewwin.sumup.data.mappers.toDomainModel
import com.andrewwin.sumup.data.mappers.toRoomEntity
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.entities.SourceType as DataSourceType
import com.andrewwin.sumup.data.remote.sources.RemoteArticleDataSource
import com.andrewwin.sumup.domain.article.processing.ArticleContentCleaner
import com.andrewwin.sumup.domain.source.repository.ImportedSource
import com.andrewwin.sumup.domain.source.repository.ImportedSourceGroup
import com.andrewwin.sumup.domain.source.repository.SourceRepository
import com.andrewwin.sumup.domain.source.model.Source
import com.andrewwin.sumup.domain.source.model.SourceGroup
import com.andrewwin.sumup.domain.source.model.SourceGroupOrigin
import com.andrewwin.sumup.domain.source.model.SourceGroupWithSources
import com.andrewwin.sumup.domain.source.model.SourceType
import com.andrewwin.sumup.domain.source.util.SourceUrlNormalizer
import com.andrewwin.sumup.domain.source.util.SourceUrlValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SourceRepositoryImpl @Inject constructor(
    private val sourceDao: SourceDao,
    private val remoteArticleDataSource: RemoteArticleDataSource,
    private val cleanArticleTextUseCase: ArticleContentCleaner
) : SourceRepository {

    override val groupsWithSources: Flow<List<SourceGroupWithSources>> =
        sourceDao.getGroupsWithSources().map { groups -> groups.map { it.toDomainModel() } }

    override suspend fun getSourcesByGroupId(groupId: Long): List<Source> =
        sourceDao.getSourcesByGroupId(groupId).first().map { it.toDomainModel() }

    override suspend fun getSourcesByIds(sourceIds: List<Long>): List<Source> =
        sourceDao.getSourcesByIds(sourceIds).map { it.toDomainModel() }

    override suspend fun addGroup(name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return
        if (sourceDao.groupExistsByName(normalizedName)) return
        sourceDao.insertGroup(SourceGroup(name = normalizedName).toRoomEntity())
    }

    override suspend fun updateGroup(group: SourceGroup) {
        sourceDao.updateGroup(group.toRoomEntity())
    }

    override suspend fun toggleGroup(group: SourceGroup, isEnabled: Boolean) {
        val updatedGroup = group.copy(isEnabled = isEnabled)
        sourceDao.updateGroup(updatedGroup.toRoomEntity())
        val sources = sourceDao.getSourcesByGroupId(group.id).first()
        sources.forEach { source ->
            sourceDao.updateSource(source.copy(isEnabled = isEnabled))
        }
    }

    override suspend fun deleteGroup(group: SourceGroup) {
        if (group.isDeletable) {
            sourceDao.deleteGroup(group.toRoomEntity())
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
        val targetGroup = sourceDao.findGroupById(groupId)?.toDomainModel() ?: return
        if (targetGroup.origin == SourceGroupOrigin.PUBLIC_SUBSCRIPTION) return

        val normalizedName = name.trim()
        val normalizedUrl = normalizeUrl(url, type)
        if (normalizedUrl.isBlank()) return
        if (sourceDao.sourceExistsByTypeAndUrl(type.toRoomEntity(), normalizedUrl)) return

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
        val insertedId = sourceDao.insertSource(sourceToInsert.toRoomEntity())
        if (insertedId <= 0L) return

        if (!detectFooterPattern) return

        val footerPatternCheckedAt = System.currentTimeMillis()
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
                ).toRoomEntity()
            ).take(10)
            if (sampleArticles.size >= 2) {
                cleanArticleTextUseCase.detectFooterPattern(sampleArticles.map { it.content })
            } else null
        } catch (e: Exception) {
            null
        }

        sourceDao.updateSource(
            sourceToInsert.copy(
                id = insertedId,
                footerPattern = footerPattern?.takeIf { it.isNotBlank() },
                footerPatternCheckedAt = footerPatternCheckedAt
            ).toRoomEntity()
        )
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
            ).toRoomEntity()
        )
    }

    override suspend fun fetchGeneratedSourceName(url: String, type: SourceType): String {
        if (!SourceUrlValidator.isValid(url, type)) return ""
        val normalizedUrl = normalizeUrl(url, type)
        if (normalizedUrl.isBlank()) return ""
        return generateSourceName(normalizedUrl, type)
    }

    override suspend fun deleteSource(source: Source) {
        sourceDao.deleteSource(source.toRoomEntity())
    }

    override suspend fun getGroupsWithSourcesSnapshot(): List<SourceGroupWithSources> =
        sourceDao.getGroupsWithSourcesOnce().map { it.toDomainModel() }

    override suspend fun subscribeToImportedGroup(
        group: ImportedSourceGroup,
        displayName: String
    ) {
        val normalizedGroupName = displayName.trim()
        if (normalizedGroupName.isBlank()) return

        val groupsSnapshot = sourceDao.getGroupsWithSourcesOnce().map { it.toDomainModel() }
        val existingGroupWithSources = if (group.sources.isEmpty()) {
            null
        } else {
            groupsSnapshot.firstOrNull {
                it.group.origin == SourceGroupOrigin.PUBLIC_SUBSCRIPTION &&
                    it.hasAllImportedSources(group)
            }
        }
        val targetGroupId = existingGroupWithSources?.group?.id ?: run {
            val existingGroup = sourceDao.findGroupByName(normalizedGroupName)?.toDomainModel()
            if (existingGroup?.origin == SourceGroupOrigin.PUBLIC_SUBSCRIPTION) {
                sourceDao.updateGroup(existingGroup.asPublicSubscription(group).toRoomEntity())
                existingGroup.id
            } else {
                val subscriptionGroupName = buildAvailableSubscriptionGroupName(
                    baseName = normalizedGroupName,
                    groups = groupsSnapshot
                )
                sourceDao.insertGroup(
                    SourceGroup(
                        name = subscriptionGroupName,
                        isEnabled = group.isEnabled,
                        isDeletable = group.isDeletable,
                        origin = SourceGroupOrigin.PUBLIC_SUBSCRIPTION,
                        subscriptionId = group.id
                    ).toRoomEntity()
                ).takeIf { it > 0L } ?: sourceDao.findGroupByName(subscriptionGroupName)?.id ?: return
            }
        }
        existingGroupWithSources?.group?.let { existingGroup ->
            if (existingGroup.subscriptionId != group.id ||
                existingGroup.isDeletable != group.isDeletable
            ) {
                sourceDao.updateGroup(existingGroup.asPublicSubscription(group).toRoomEntity())
            }
        }

        for (importedSource in group.sources) {
            upsertImportedSource(
                groupId = targetGroupId,
                imported = importedSource,
                preserveNonSubscriptionSources = true
            )
        }
    }

    override suspend fun markImportedGroupsAsSubscriptions(groups: List<ImportedSourceGroup>) {
        if (groups.isEmpty()) return
        val groupsSnapshot = sourceDao.getGroupsWithSourcesOnce().map { it.toDomainModel() }
        groups.forEach { importedGroup ->
            val matchingGroup = groupsSnapshot.firstOrNull { groupWithSources ->
                groupWithSources.group.origin == SourceGroupOrigin.PUBLIC_SUBSCRIPTION &&
                    groupWithSources.hasAllImportedSources(importedGroup)
            }?.group ?: return@forEach
            if (matchingGroup.subscriptionId != importedGroup.id ||
                matchingGroup.isDeletable != importedGroup.isDeletable
            ) {
                sourceDao.updateGroup(matchingGroup.asPublicSubscription(importedGroup).toRoomEntity())
            }
        }
    }

    override suspend fun syncSubscribedImportedGroups(groups: List<ImportedSourceGroup>) {
        if (groups.isEmpty()) return
        val importedGroupsById = groups.associateBy { it.id.trim().lowercase() }
        if (importedGroupsById.isEmpty()) return

        val groupsSnapshot = sourceDao.getGroupsWithSourcesOnce().map { it.toDomainModel() }
        val syncedGroupIds = mutableSetOf<Long>()

        groupsSnapshot.forEach { groupWithSources ->
            if (groupWithSources.group.origin != SourceGroupOrigin.PUBLIC_SUBSCRIPTION) return@forEach
            val subscriptionId = groupWithSources.group.subscriptionId?.trim()?.lowercase() ?: return@forEach
            val importedGroup = importedGroupsById[subscriptionId] ?: return@forEach
            replaceExistingGroupWithImportedSubscription(groupWithSources, importedGroup)
            syncedGroupIds += groupWithSources.group.id
        }

        groups.forEach { importedGroup ->
            val matchingGroup = groupsSnapshot.firstOrNull { groupWithSources ->
                groupWithSources.group.id !in syncedGroupIds &&
                    groupWithSources.group.origin == SourceGroupOrigin.PUBLIC_SUBSCRIPTION &&
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

        val groupsSnapshot = sourceDao.getGroupsWithSourcesOnce().map { it.toDomainModel() }
        val matchingGroups = groupsSnapshot.filter { groupWithSources ->
            groupWithSources.group.origin == SourceGroupOrigin.PUBLIC_SUBSCRIPTION &&
                groupWithSources.isMatchingSubscriptionGroup(group) &&
                groupWithSources.sources.any { source ->
                    source.type to source.url in sourcesToRemove
                }
        }
        val sourceIdsToDelete = matchingGroups
            .flatMap { groupWithSources ->
                groupWithSources.sources
                    .filter { source -> source.type to source.url in sourcesToRemove }
                    .map(Source::id)
            }
            .distinct()
        val groupIdsToDeleteIfEmpty = matchingGroups
            .map { it.group.id }
            .distinct()
        sourceDao.deleteSourcesAndEmptyGroups(
            sourceIds = sourceIdsToDelete,
            groupIds = groupIdsToDeleteIfEmpty
        )
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

            val existingGroup = sourceDao.findGroupByName(normalizedGroupName)?.toDomainModel()
            val targetGroupId = if (existingGroup != null) {
                sourceDao.updateGroup(
                    existingGroup.copy(
                        isEnabled = if (merge) existingGroup.isEnabled else group.isEnabled,
                        isDeletable = existingGroup.isDeletable,
                        origin = group.origin ?: existingGroup.origin,
                        subscriptionId = group.subscriptionId ?: existingGroup.subscriptionId
                    ).toRoomEntity()
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
                    ).toRoomEntity()
                )
                if (insertedId > 0L) insertedId else sourceDao.findGroupByName(normalizedGroupName)?.id ?: continue
            }

            for (importedSource in group.sources) {
                upsertImportedSource(targetGroupId, importedSource)
            }
        }
    }

    private suspend fun replaceExistingGroupWithImportedSubscription(
        existingGroupWithSources: SourceGroupWithSources,
        importedGroup: ImportedSourceGroup
    ) {
        val existingGroup = existingGroupWithSources.group
        if (existingGroup.origin != SourceGroupOrigin.PUBLIC_SUBSCRIPTION ||
            existingGroup.subscriptionId != importedGroup.id ||
            existingGroup.isDeletable != importedGroup.isDeletable
        ) {
            sourceDao.updateGroup(existingGroup.asPublicSubscription(importedGroup).toRoomEntity())
        }

        val importedSourceKeys = importedGroup.sources
            .mapNotNull { it.normalizedSourceKey() }
            .toSet()
        val sourceIdsToDelete = existingGroupWithSources.sources
            .filter { it.type to it.url !in importedSourceKeys }
            .map(Source::id)
        if (sourceIdsToDelete.isNotEmpty()) {
            sourceDao.deleteSourcesByIds(sourceIdsToDelete)
        }

        importedGroup.sources.forEach { importedSource ->
            upsertImportedSource(
                groupId = existingGroup.id,
                imported = importedSource,
                preserveNonSubscriptionSources = true
            )
        }
    }

    private suspend fun upsertImportedSource(
        groupId: Long,
        imported: ImportedSource,
        preserveNonSubscriptionSources: Boolean = false
    ) {
        val normalizedUrl = normalizeUrl(imported.url, imported.type)
        val normalizedName = imported.name.trim()
        if (normalizedUrl.isBlank() || normalizedName.isBlank()) return

        val normalizedTitleSelector = normalizeSelector(imported.titleSelector)
        val normalizedPostLinkSelector = normalizeSelector(imported.postLinkSelector)
        val normalizedDescriptionSelector = normalizeSelector(imported.descriptionSelector)
        val normalizedDateSelector = normalizeSelector(imported.dateSelector)

        val existing = findExistingImportedSource(imported.type, normalizedUrl)
        if (preserveNonSubscriptionSources && existing != null) {
            val existingGroup = sourceDao.findGroupById(existing.groupId)?.toDomainModel()
            if (existingGroup?.origin != SourceGroupOrigin.PUBLIC_SUBSCRIPTION) return
        }
        val updated = Source(
            id = existing?.id ?: 0L,
            groupId = groupId,
            name = normalizedName,
            url = normalizedUrl,
            type = imported.type,
            isEnabled = imported.isEnabled,
            footerPattern = imported.footerPattern?.trim()?.takeIf { it.isNotEmpty() },
            footerPatternCheckedAt = imported.footerPatternCheckedAt
                .takeIf { it > 0L }
                ?: existing?.footerPatternCheckedAt
                ?: 0L,
            titleSelector = normalizedTitleSelector,
            postLinkSelector = normalizedPostLinkSelector,
            descriptionSelector = normalizedDescriptionSelector,
            dateSelector = normalizedDateSelector,
            useHeadlessBrowser = imported.useHeadlessBrowser
        )

        if (existing == null) {
            sourceDao.insertSource(updated.toRoomEntity())
        } else {
            sourceDao.updateSource(updated.toRoomEntity())
        }
    }

    private suspend fun findExistingImportedSource(
        type: SourceType,
        normalizedUrl: String
    ): com.andrewwin.sumup.data.local.entities.Source? {
        val roomType = type.toRoomEntity()
        sourceDao.findSourceByTypeAndUrl(roomType, normalizedUrl)?.let { return it }
        return sourceDao.getSourcesByType(roomType).firstOrNull { existing ->
            normalizeUrl(existing.url, type).equals(normalizedUrl, ignoreCase = true)
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

    private fun SourceGroupWithSources.hasAllImportedSources(group: ImportedSourceGroup): Boolean {
        if (group.sources.isEmpty()) return false
        return group.sources.all { importedSource ->
            val normalizedImportedUrl = normalizeUrl(importedSource.url, importedSource.type)
            sources.any { existing ->
                existing.type == importedSource.type &&
                    existing.url.equals(normalizedImportedUrl, ignoreCase = true)
            }
        }
    }

    private fun SourceGroupWithSources.isLegacyMatchForImportedGroup(group: ImportedSourceGroup): Boolean {
        val normalizedGroupName = this.group.name.normalizedGroupName()
        val publicGroupNames = group.publicGroupNames()
        if (normalizedGroupName !in publicGroupNames) return false

        val importedSourceKeys = group.sources
            .mapNotNull { it.normalizedSourceKey() }
            .toSet()
        return importedSourceKeys.isNotEmpty() &&
            sources.any { source -> source.type to source.url in importedSourceKeys }
    }

    private fun SourceGroupWithSources.isMatchingSubscriptionGroup(group: ImportedSourceGroup): Boolean {
        val normalizedSubscriptionId = this.group.subscriptionId?.trim()?.lowercase()
        return normalizedSubscriptionId == group.id.trim().lowercase() ||
            isLegacyMatchForImportedGroup(group)
    }

    private fun buildAvailableSubscriptionGroupName(
        baseName: String,
        groups: List<SourceGroupWithSources>
    ): String {
        val usedNames = groups
            .map { it.group.name.normalizedGroupName() }
            .toSet()
        if (baseName.normalizedGroupName() !in usedNames) return baseName

        var index = 2
        while (true) {
            val candidate = "$baseName #$index"
            if (candidate.normalizedGroupName() !in usedNames) return candidate
            index++
        }
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
        remoteArticleDataSource.fetchDisplayName(url, DataSourceType.TELEGRAM)
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
        remoteArticleDataSource.fetchDisplayName(url, DataSourceType.YOUTUBE)
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
        remoteArticleDataSource.fetchDisplayName(url, DataSourceType.RSS)
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
