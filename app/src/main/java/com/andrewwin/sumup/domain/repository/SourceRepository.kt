package com.andrewwin.sumup.domain.repository

import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType
import kotlinx.coroutines.flow.Flow

interface SourceRepository {
    val groupsWithSources: Flow<List<GroupWithSources>>
    suspend fun getSourcesByGroupId(groupId: Long): List<Source>
    suspend fun addGroup(name: String)
    suspend fun updateGroup(group: SourceGroup)
    suspend fun toggleGroup(group: SourceGroup, isEnabled: Boolean)
    suspend fun deleteGroup(group: SourceGroup)
    suspend fun addSource(groupId: Long, name: String, url: String, type: SourceType)
    suspend fun updateSource(source: Source)
    suspend fun deleteSource(source: Source)
}
