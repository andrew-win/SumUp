package com.andrewwin.sumup.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_model_configs")
data class AiModelConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val provider: AiProvider,
    val apiKey: String,
    val modelName: String,
    val isEnabled: Boolean = true
)
