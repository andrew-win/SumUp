package com.andrewwin.sumup.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andrewwin.sumup.data.local.entities.SourceHttpCache

@Dao
interface SourceHttpCacheDao {
    @Query("SELECT * FROM source_http_cache WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): SourceHttpCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SourceHttpCache)
}
