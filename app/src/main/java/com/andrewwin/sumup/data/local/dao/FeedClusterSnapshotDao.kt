package com.andrewwin.sumup.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andrewwin.sumup.data.local.entities.FeedClusterSnapshotEntity

@Dao
interface FeedClusterSnapshotDao {
    @Query("SELECT * FROM feed_cluster_snapshots WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getSnapshot(cacheKey: String): FeedClusterSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: FeedClusterSnapshotEntity)

    @Query("DELETE FROM feed_cluster_snapshots")
    suspend fun deleteAll()
}
