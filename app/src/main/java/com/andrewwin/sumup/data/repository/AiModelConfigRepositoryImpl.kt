package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.local.dao.AiModelDao
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.normalizedStableKey
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.remote.AiService
import com.andrewwin.sumup.data.security.SecretEncryptionManager
import com.andrewwin.sumup.domain.repository.AiModelConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class AiModelConfigRepositoryImpl @Inject constructor(
    private val aiModelDao: AiModelDao,
    private val aiService: AiService,
    private val secretEncryptionManager: SecretEncryptionManager
) : AiModelConfigRepository {
    private val configWriteMutex = Mutex()

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

    override suspend fun addConfig(config: AiModelConfig) {
        configWriteMutex.withLock {
            val normalizedConfig = normalizeConfig(config)
            val existingConfigs = getAllDecryptedConfigsSnapshot()
            val existingConflict = findConfigConflict(existingConfigs, normalizedConfig)
            if (existingConflict == null) {
                aiModelDao.insertConfig(encryptConfig(normalizedConfig))
            } else {
                aiModelDao.updateConfig(
                    encryptConfig(
                        normalizedConfig.copy(
                            id = existingConflict.id,
                            isUseNow = normalizedConfig.isUseNow || existingConflict.isUseNow
                        )
                    )
                )
            }
        }
    }

    override suspend fun updateConfig(config: AiModelConfig) {
        configWriteMutex.withLock {
            val normalizedConfig = normalizeConfig(config)
            val existingConfigs = getAllDecryptedConfigsSnapshot()
            val existingConflict = findConfigConflict(
                configs = existingConfigs,
                target = normalizedConfig,
                ignoreId = normalizedConfig.id
            )
            if (existingConflict == null) {
                aiModelDao.updateConfig(encryptConfig(normalizedConfig))
            } else {
                aiModelDao.updateConfig(
                    encryptConfig(
                        normalizedConfig.copy(
                            id = existingConflict.id,
                            isUseNow = normalizedConfig.isUseNow || existingConflict.isUseNow
                        )
                    )
                )
                existingConfigs
                    .firstOrNull { it.id == normalizedConfig.id }
                    ?.takeIf { it.id != existingConflict.id }
                    ?.let { aiModelDao.deleteConfig(encryptConfig(it)) }
            }
        }
    }

    override suspend fun deleteConfig(config: AiModelConfig) =
        configWriteMutex.withLock {
            aiModelDao.deleteConfig(encryptConfig(config))
        }

    override suspend fun migrateLegacyApiKeys() {
        configWriteMutex.withLock {
            aiModelDao.getAllConfigs().first().forEach { config ->
                if (!secretEncryptionManager.isLocallyEncrypted(config.apiKey) && config.apiKey.isNotBlank()) {
                    aiModelDao.updateConfig(encryptConfig(config))
                }
            }
            deduplicateStoredConfigsLocked()
        }
    }

    override fun setLastUsedSummaryModelName(name: String?) {
        _lastUsedSummaryModelName.value = name
    }

    private suspend fun deduplicateStoredConfigsLocked() {
        val existingConfigs = getAllDecryptedConfigsSnapshot()
        val keptConfigs = mutableListOf<AiModelConfig>()
        val deletedConfigs = mutableListOf<AiModelConfig>()

        existingConfigs
            .sortedBy { it.id }
            .forEach { config ->
                val normalizedConfig = normalizeConfig(config)
                val existingConflict = findConfigConflict(keptConfigs, normalizedConfig)
                if (existingConflict == null) {
                    keptConfigs += normalizedConfig
                } else {
                    val mergedConfig = normalizedConfig.copy(
                        id = existingConflict.id,
                        isUseNow = normalizedConfig.isUseNow || existingConflict.isUseNow
                    )
                    val existingIndex = keptConfigs.indexOfFirst { it.id == existingConflict.id }
                    if (existingIndex >= 0) {
                        keptConfigs[existingIndex] = mergedConfig
                    }
                    deletedConfigs += config
                }
            }

        keptConfigs.forEach { aiModelDao.updateConfig(encryptConfig(it)) }
        deletedConfigs.forEach { aiModelDao.deleteConfig(encryptConfig(it)) }
    }

    private suspend fun getAllDecryptedConfigsSnapshot(): List<AiModelConfig> =
        aiModelDao.getAllConfigs().first().map(::decryptConfig)

    private fun findConfigConflict(
        configs: List<AiModelConfig>,
        target: AiModelConfig,
        ignoreId: Long? = null
    ): AiModelConfig? {
        val normalizedName = normalizeConfigName(target.name)
        return configs.firstOrNull {
            it.id != ignoreId && it.normalizedStableKey() == target.normalizedStableKey()
        } ?: configs.firstOrNull {
            normalizedName.isNotBlank() &&
                it.id != ignoreId &&
                it.type == target.type &&
                normalizeConfigName(it.name) == normalizedName
        }
    }

    private fun normalizeConfig(config: AiModelConfig): AiModelConfig {
        return config.copy(
            name = config.name.trim(),
            apiKey = normalizeApiKey(config.apiKey)
        )
    }

    private fun normalizeApiKey(apiKey: String): String = apiKey.trim()

    private fun normalizeConfigName(name: String): String = name.trim().lowercase()

    private fun encryptConfig(config: AiModelConfig): AiModelConfig =
        config.copy(apiKey = secretEncryptionManager.encryptLocal(config.apiKey))

    private fun decryptConfig(config: AiModelConfig): AiModelConfig =
        config.copy(apiKey = secretEncryptionManager.decryptLocal(config.apiKey))
}
