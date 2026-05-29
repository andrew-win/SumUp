package com.andrewwin.sumup.domain.entities.settings

data class ModelSettings(
    val strategy: AiStrategy,
    val modelPath: String?
)
