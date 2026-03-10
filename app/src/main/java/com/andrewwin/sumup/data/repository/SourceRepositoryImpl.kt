package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.repository.SourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SourceRepositoryImpl @Inject constructor(
    private val sourceDao: SourceDao
) : SourceRepository {

    override val groupsWithSources: Flow<List<GroupWithSources>> = sourceDao.getGroupsWithSources()

    override suspend fun getSourcesByGroupId(groupId: Long): List<Source> =
        sourceDao.getSourcesByGroupId(groupId).first()

    override suspend fun addGroup(name: String) {
        sourceDao.insertGroup(SourceGroup(name = name))
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

    override suspend fun addSource(groupId: Long, name: String, url: String, type: SourceType) {
        sourceDao.insertSource(Source(groupId = groupId, name = name, url = url, type = type))
    }

    override suspend fun updateSource(source: Source) {
        sourceDao.updateSource(source)
    }

    override suspend fun deleteSource(source: Source) {
        sourceDao.deleteSource(source)
    }
}
