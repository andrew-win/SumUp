package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.local.dao.AiModelDao
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.remote.AiService
import com.andrewwin.sumup.data.security.SecretEncryptionManager
import com.andrewwin.sumup.domain.repository.AiModelConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AiModelConfigRepositoryImpl @Inject constructor(
    private val aiModelDao: AiModelDao,
    private val aiService: AiService,
    private val secretEncryptionManager: SecretEncryptionManager
) : AiModelConfigRepository {

    private val _lastUsedSummaryModelName = MutableStateFlow<String?>(null)
    override val lastUsedSummaryModelName: StateFlow<String?> = _lastUsedSummaryModelName

    override val allConfigs: Flow<List<AiModelConfig>> = aiModelDao.getAllConfigs().map { configs ->
        configs.map(::decryptConfig)
    }

    override fun getConfigsByType(type: AiModelType): Flow<List<AiModelConfig>> =
        aiModelDao.getConfigsByType(type).map { configs -> configs.map(::decryptConfig) }

    override suspend fun getEnabledConfigsByType(type: AiModelType): List<AiModelConfig> =
        aiModelDao.getEnabledConfigsByType(type).map(::decryptConfig)

    override suspend fun fetchAvailableModels(provider: AiProvider, apiKey: String, type: AiModelType): List<String> =
        aiService.fetchModels(provider, apiKey, type)

    override suspend fun addConfig(config: AiModelConfig) = aiModelDao.insertConfig(encryptConfig(config))
    
    override suspend fun updateConfig(config: AiModelConfig) = aiModelDao.updateConfig(encryptConfig(config))
    
    override suspend fun deleteConfig(config: AiModelConfig) = aiModelDao.deleteConfig(config)
    
    override suspend fun migrateLegacyApiKeys() {
        aiModelDao.getAllConfigs().first().forEach { config ->
            if (!secretEncryptionManager.isLocallyEncrypted(config.apiKey) && config.apiKey.isNotBlank()) {
                aiModelDao.updateConfig(encryptConfig(config))
            }
        }
    }

    override fun setLastUsedSummaryModelName(name: String?) {
        _lastUsedSummaryModelName.value = name
    }

    private fun encryptConfig(config: AiModelConfig): AiModelConfig =
        config.copy(apiKey = secretEncryptionManager.encryptLocal(config.apiKey))

    private fun decryptConfig(config: AiModelConfig): AiModelConfig =
        config.copy(apiKey = secretEncryptionManager.decryptLocal(config.apiKey))
}
