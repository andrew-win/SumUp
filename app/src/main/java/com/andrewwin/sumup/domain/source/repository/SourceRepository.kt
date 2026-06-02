package com.andrewwin.sumup.domain.source.repository

import com.andrewwin.sumup.domain.settings.model.AppLanguage
import com.andrewwin.sumup.domain.source.model.Source
import com.andrewwin.sumup.domain.source.model.SourceGroup
import com.andrewwin.sumup.domain.source.model.SourceGroupOrigin
import com.andrewwin.sumup.domain.source.model.SourceGroupWithSources
import com.andrewwin.sumup.domain.source.model.SourceType
import kotlinx.coroutines.flow.Flow

interface SourceRepository {
    val groupsWithSources: Flow<List<SourceGroupWithSources>>
    suspend fun getSourcesByGroupId(groupId: Long): List<Source>
    suspend fun getSourcesByIds(sourceIds: List<Long>): List<Source>
    suspend fun addGroup(name: String)
    suspend fun updateGroup(group: SourceGroup)
    suspend fun toggleGroup(group: SourceGroup, isEnabled: Boolean)
    suspend fun deleteGroup(group: SourceGroup)
    suspend fun addSource(
        groupId: Long,
        name: String,
        url: String,
        type: SourceType,
        titleSelector: String? = null,
        postLinkSelector: String? = null,
        descriptionSelector: String? = null,
        dateSelector: String? = null,
        useHeadlessBrowser: Boolean = false,
        detectFooterPattern: Boolean = true
    )
    suspend fun fetchGeneratedSourceName(url: String, type: SourceType): String
    suspend fun updateSource(source: Source)
    suspend fun deleteSource(source: Source)
    suspend fun getGroupsWithSourcesSnapshot(): List<SourceGroupWithSources>
    suspend fun subscribeToImportedGroup(
        group: ImportedSourceGroup,
        displayName: String
    )
    suspend fun markImportedGroupsAsSubscriptions(groups: List<ImportedSourceGroup>)
    suspend fun syncSubscribedImportedGroups(groups: List<ImportedSourceGroup>)
    suspend fun unsubscribeFromImportedGroup(group: ImportedSourceGroup)
    suspend fun importGroupsWithSources(
        groups: List<ImportedSourceGroup>,
        merge: Boolean
    )
}

data class ImportedSourceGroup(
    val id: String,
    val name: String,
    val nameUk: String,
    val nameEn: String,
    val isEnabled: Boolean,
    val isDeletable: Boolean,
    val origin: SourceGroupOrigin? = null,
    val subscriptionId: String? = null,
    val sources: List<ImportedSource>,
    val recommendationAnchors: List<String> = emptyList(),
    val sortOrder: Int = 0
) {
    fun displayName(language: AppLanguage): String = when (language) {
        AppLanguage.UK -> nameUk.ifBlank { name }.ifBlank { nameEn }
        AppLanguage.EN -> nameEn.ifBlank { name }.ifBlank { nameUk }
    }
}

data class ImportedSource(
    val name: String,
    val url: String,
    val type: SourceType,
    val isEnabled: Boolean,
    val footerPattern: String?,
    val footerPatternCheckedAt: Long = 0L,
    val titleSelector: String?,
    val postLinkSelector: String?,
    val descriptionSelector: String?,
    val dateSelector: String?,
    val useHeadlessBrowser: Boolean
)






