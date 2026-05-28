package com.andrewwin.sumup.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "source_http_cache")
data class SourceHttpCache(
    @PrimaryKey val url: String,
    val etag: String?,
    val lastModified: String?,
    val updatedAt: Long = System.currentTimeMillis()
)
