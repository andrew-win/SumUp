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

    @Query("SELECT * FROM sources WHERE groupId = :groupId")
    fun getSourcesByGroupId(groupId: Long): Flow<List<Source>>

    @Query("SELECT * FROM sources WHERE id = :sourceId")
    suspend fun getSourceById(sourceId: Long): Source?

    @Transaction
    @Query("SELECT * FROM source_groups")
    fun getGroupsWithSources(): Flow<List<GroupWithSources>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: SourceGroup): Long

    @Update
    suspend fun updateGroup(group: SourceGroup)

    @Delete
    suspend fun deleteGroup(group: SourceGroup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: Source)

    @Update
    suspend fun updateSource(source: Source)

    @Delete
    suspend fun deleteSource(source: Source)
}
