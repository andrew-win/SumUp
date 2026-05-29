package com.andrewwin.sumup.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andrewwin.sumup.data.local.entities.FeedClusterSnapshot

@Dao
interface FeedClusterSnapshotDao {
    @Query("SELECT * FROM feed_cluster_snapshots WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getSnapshot(cacheKey: String): FeedClusterSnapshot?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: FeedClusterSnapshot)

    @Query("DELETE FROM feed_cluster_snapshots")
    suspend fun deleteAll()
}
