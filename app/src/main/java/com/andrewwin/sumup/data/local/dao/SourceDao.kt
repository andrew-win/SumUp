package com.andrewwin.sumup.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import kotlinx.coroutines.flow.Flow

data class GroupWithSources(
    @Embedded val group: SourceGroup,
    @Relation(
        parentColumn = "id",
        entityColumn = "groupId"
    )
    val sources: List<Source>
)

@Dao
interface SourceDao {
    @Query("SELECT * FROM source_groups")
    fun getAllGroups(): Flow<List<SourceGroup>>

    @Query("SELECT EXISTS(SELECT 1 FROM source_groups WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)))")
    suspend fun groupExistsByName(name: String): Boolean

    @Query("SELECT * FROM sources WHERE groupId = :groupId")
    fun getSourcesByGroupId(groupId: Long): Flow<List<Source>>

    @Query("SELECT * FROM sources WHERE groupId = :groupId")
    suspend fun getSourcesByGroupIdOnce(groupId: Long): List<Source>

    @Query("SELECT * FROM sources WHERE id = :sourceId")
    suspend fun getSourceById(sourceId: Long): Source?

    @Query("SELECT * FROM sources WHERE id IN (:sourceIds)")
    suspend fun getSourcesByIds(sourceIds: List<Long>): List<Source>

    @Query("SELECT * FROM sources WHERE type = :type")
    suspend fun getSourcesByType(type: com.andrewwin.sumup.data.local.entities.SourceType): List<Source>

    @Query("SELECT EXISTS(SELECT 1 FROM sources WHERE type = :type AND LOWER(TRIM(url)) = LOWER(TRIM(:url)))")
    suspend fun sourceExistsByTypeAndUrl(type: com.andrewwin.sumup.data.local.entities.SourceType, url: String): Boolean

    @Transaction
    @Query("SELECT * FROM source_groups")
    fun getGroupsWithSources(): Flow<List<GroupWithSources>>

    @Transaction
    @Query("SELECT * FROM source_groups")
    suspend fun getGroupsWithSourcesOnce(): List<GroupWithSources>

    @Query("SELECT * FROM source_groups WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun findGroupByName(name: String): SourceGroup?

    @Query("SELECT * FROM source_groups WHERE id = :groupId LIMIT 1")
    suspend fun findGroupById(groupId: Long): SourceGroup?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroup(group: SourceGroup): Long

    @Update
    suspend fun updateGroup(group: SourceGroup)

    @Delete
    suspend fun deleteGroup(group: SourceGroup)

    @Query("DELETE FROM source_groups WHERE id = :groupId")
    suspend fun deleteGroupById(groupId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(source: Source): Long

    @Query("SELECT * FROM sources WHERE type = :type AND LOWER(TRIM(url)) = LOWER(TRIM(:url)) LIMIT 1")
    suspend fun findSourceByTypeAndUrl(type: com.andrewwin.sumup.data.local.entities.SourceType, url: String): Source?

    @Update
    suspend fun updateSource(source: Source)

    @Delete
    suspend fun deleteSource(source: Source)

    @Query("DELETE FROM sources WHERE id IN (:sourceIds)")
    suspend fun deleteSourcesByIds(sourceIds: List<Long>)

    @Query("DELETE FROM sources")
    suspend fun deleteAllSources()

    @Query("DELETE FROM source_groups WHERE isDeletable = 1")
    suspend fun deleteDeletableGroups()

    @Query(
        """
        DELETE FROM source_groups
        WHERE id IN (:groupIds)
        AND NOT EXISTS (
            SELECT 1 FROM sources
            WHERE sources.groupId = source_groups.id
        )
        """
    )
    suspend fun deleteEmptyGroupsByIds(groupIds: List<Long>)

    @Transaction
    suspend fun deleteSourcesAndEmptyGroups(
        sourceIds: List<Long>,
        groupIds: List<Long>
    ) {
        if (sourceIds.isEmpty()) return
        deleteSourcesByIds(sourceIds)
        if (groupIds.isNotEmpty()) {
            deleteEmptyGroupsByIds(groupIds)
        }
    }
}






