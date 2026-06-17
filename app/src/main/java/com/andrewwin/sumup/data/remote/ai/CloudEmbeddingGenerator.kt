package com.andrewwin.sumup.data.remote.ai

import com.andrewwin.sumup.domain.ai.model.AiModelConfig
import com.andrewwin.sumup.domain.ai.model.AiModelType
import com.andrewwin.sumup.domain.ai.embedding.CloudEmbeddingProvider
import com.andrewwin.sumup.domain.ai.repository.AiModelConfigRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class CloudEmbeddingGenerator @Inject constructor(
    private val aiModelConfigRepository: AiModelConfigRepository,
    private val aiService: AiService
) : CloudEmbeddingProvider {
    override suspend fun generateEmbedding(text: String, debugRunId: Long?): FloatArray? {
        return generateEmbeddings(listOf(text), debugRunId).firstOrNull()
    }

    override suspend fun generateEmbeddings(texts: List<String>, debugRunId: Long?): List<FloatArray?> {
        if (texts.isEmpty()) return emptyList()
        val enabledConfigs = aiModelConfigRepository.getEnabledConfigsByType(AiModelType.EMBEDDING)
        if (enabledConfigs.isEmpty()) return List(texts.size) { null }

        for (config in enabledConfigs) {
            val batchEmbeddings = try {
                val batchEmbeddings = aiService.generateEmbeddings(config, texts, debugRunId)
                batchEmbeddings
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                continue
            }

            val result = fillMissingEmbeddingsWithSingleRequests(config, texts, batchEmbeddings, debugRunId)
            return result
        }
        return List(texts.size) { null }
    }

    private suspend fun fillMissingEmbeddingsWithSingleRequests(
        config: AiModelConfig,
        texts: List<String>,
        batchEmbeddings: List<FloatArray?>,
        debugRunId: Long?
    ): List<FloatArray?> {
        val result = MutableList<FloatArray?>(texts.size) { index -> batchEmbeddings.getOrNull(index) }
        val missingIndexes = result.indices.filter { result[it] == null }
        if (missingIndexes.isEmpty()) return result

        missingIndexes.forEach { index ->
            try {
                result[index] = aiService.generateEmbedding(config, texts[index], debugRunId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
        return result
    }
}
