package com.andrewwin.sumup.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "summaries")
data class Summary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val strategy: AiStrategy = AiStrategy.ADAPTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val isFavorite: Boolean = false
)






