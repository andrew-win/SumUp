package com.andrewwin.sumup.domain.repository

import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType
import kotlinx.coroutines.flow.Flow

interface SourceRepository {
    val groupsWithSources: Flow<List<GroupWithSources>>
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
        useHeadlessBrowser: Boolean = false
    )
    suspend fun updateSource(source: Source)
    suspend fun deleteSource(source: Source)
    suspend fun getGroupsWithSourcesSnapshot(): List<GroupWithSources>
    suspend fun importGroupsWithSources(
        groups: List<ImportedSourceGroup>,
        merge: Boolean
    )
}

data class ImportedSourceGroup(
    val name: String,
    val isEnabled: Boolean,
    val isDeletable: Boolean,
    val sources: List<ImportedSource>
)

data class ImportedSource(
    val name: String,
    val url: String,
    val type: SourceType,
    val isEnabled: Boolean,
    val footerPattern: String?,
    val titleSelector: String?,
    val postLinkSelector: String?,
    val descriptionSelector: String?,
    val dateSelector: String?,
    val useHeadlessBrowser: Boolean
)






