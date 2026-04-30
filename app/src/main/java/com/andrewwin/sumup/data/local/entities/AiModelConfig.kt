package com.andrewwin.sumup.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AiModelType {
    SUMMARY, EMBEDDING
}

enum class AiConfigPriority {
    LOW, MEDIUM, HIGH
}

@Entity(tableName = "ai_model_configs")
data class AiModelConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val provider: AiProvider,
    val apiKey: String,
    val modelName: String,
    val isEnabled: Boolean = true,
    val type: AiModelType = AiModelType.SUMMARY,
    val priority: AiConfigPriority = AiConfigPriority.MEDIUM,
    val isUseNow: Boolean = false
) {
    companion object
}

fun AiModelConfig.normalizedStableKey(): String = "${type.name}:${apiKey.trim()}"





