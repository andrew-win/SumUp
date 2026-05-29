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
    val isDeletable: Boolean = true,
    val origin: String = SourceGroupOrigin.USER,
    val subscriptionId: String? = null
)

object SourceGroupOrigin {
    const val USER = "USER"
    const val PUBLIC_SUBSCRIPTION = "PUBLIC_SUBSCRIPTION"
    const val SYSTEM = "SYSTEM"
}






