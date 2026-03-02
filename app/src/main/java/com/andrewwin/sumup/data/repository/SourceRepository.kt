package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SourceRepository(private val sourceDao: SourceDao) {
    val groupsWithSources: Flow<List<GroupWithSources>> = sourceDao.getGroupsWithSources()

    suspend fun addGroup(name: String) {
        sourceDao.insertGroup(SourceGroup(name = name))
    }

    suspend fun updateGroup(group: SourceGroup) {
        sourceDao.updateGroup(group)
    }

    suspend fun toggleGroup(group: SourceGroup, isEnabled: Boolean) {
        val updatedGroup = group.copy(isEnabled = isEnabled)
        sourceDao.updateGroup(updatedGroup)
        val sources = sourceDao.getSourcesByGroupId(group.id).first()
        sources.forEach { source ->
            sourceDao.updateSource(source.copy(isEnabled = isEnabled))
        }
    }

    suspend fun deleteGroup(group: SourceGroup) {
        if (group.isDeletable) {
            sourceDao.deleteGroup(group)
        }
    }

    suspend fun addSource(groupId: Long, name: String, url: String, type: SourceType) {
        sourceDao.insertSource(Source(groupId = groupId, name = name, url = url, type = type))
    }

    suspend fun updateSource(source: Source) {
        sourceDao.updateSource(source)
    }

    suspend fun deleteSource(source: Source) {
        sourceDao.deleteSource(source)
    }
}
