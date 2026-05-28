package com.andrewwin.sumup.domain.repository

import com.andrewwin.sumup.domain.ai.AiModelConfig
import com.andrewwin.sumup.domain.ai.AiModelType
import com.andrewwin.sumup.domain.ai.AiProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AiModelConfigRepository {
    val allConfigs: Flow<List<AiModelConfig>>
    val lastUsedSummaryModelName: StateFlow<String?>

    fun getConfigsByType(type: AiModelType): Flow<List<AiModelConfig>>
    suspend fun getEnabledConfigsByType(type: AiModelType): List<AiModelConfig>
    
    suspend fun fetchAvailableModels(provider: AiProvider, apiKey: String, type: AiModelType): List<String>
    
    suspend fun addConfig(config: AiModelConfig)
    suspend fun updateConfig(config: AiModelConfig)
    suspend fun deleteConfig(config: AiModelConfig)
    suspend fun moveConfig(config: AiModelConfig, direction: Int)
    
    suspend fun migrateLegacyApiKeys()
    
    fun setLastUsedSummaryModelName(name: String?)
}
