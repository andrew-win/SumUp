package com.andrewwin.sumup.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "source_groups",
    indices = [Index(value = ["name"], unique = true)]
)
data class SourceGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = true,
    val isDeletable: Boolean = true
)
