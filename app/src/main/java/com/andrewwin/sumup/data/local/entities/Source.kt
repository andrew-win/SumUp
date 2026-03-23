package com.andrewwin.sumup.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sources",
    foreignKeys = [
        ForeignKey(
            entity = SourceGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId")]
)
data class Source(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val name: String,
    val url: String,
    val type: SourceType,
    val isEnabled: Boolean = true,
    val footerPattern: String? = null,
    val titleSelector: String? = null,
    val postLinkSelector: String? = null,
    val descriptionSelector: String? = null,
    val dateSelector: String? = null,
    val useHeadlessBrowser: Boolean = false
)
