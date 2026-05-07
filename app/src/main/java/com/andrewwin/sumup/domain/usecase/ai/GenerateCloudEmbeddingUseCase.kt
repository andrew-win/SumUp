package com.andrewwin.sumup.domain.usecase.ai

import android.util.Log
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.remote.AiService
import com.andrewwin.sumup.domain.repository.AiModelConfigRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class GenerateCloudEmbeddingUseCase @Inject constructor(
    private val aiModelConfigRepository: AiModelConfigRepository,
    private val aiService: AiService
) {
    suspend operator fun invoke(text: String, debugRunId: Long? = null): FloatArray? {
        return invoke(listOf(text), debugRunId).firstOrNull()
    }

    suspend operator fun invoke(texts: List<String>, debugRunId: Long? = null): List<FloatArray?> {
        if (texts.isEmpty()) return emptyList()
        val enabledConfigs = aiModelConfigRepository.getEnabledConfigsByType(AiModelType.EMBEDDING)
        if (enabledConfigs.isEmpty()) return List(texts.size) { null }

        for (config in enabledConfigs) {
            val batchEmbeddings = try {
                val batchEmbeddings = aiService.generateEmbeddings(config, texts, debugRunId)
                batchEmbeddings
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.d(
                    CLOUD_EMBEDDING_DEBUG_LOG_TAG,
                    "batch_failed runId=$debugRunId provider=${config.provider} model=${config.modelName} texts=${texts.size} " +
                        "error=${e::class.simpleName}:${e.message}"
                )
                continue
            }

            val result = fillMissingEmbeddingsWithSingleRequests(config, texts, batchEmbeddings, debugRunId)
            logEmbeddingResult(debugRunId, "cloud_usecase_result", result)
            return result
        }
        return List(texts.size) { null }
    }

    private suspend fun fillMissingEmbeddingsWithSingleRequests(
        config: com.andrewwin.sumup.data.local.entities.AiModelConfig,
        texts: List<String>,
        batchEmbeddings: List<FloatArray?>,
        debugRunId: Long?
    ): List<FloatArray?> {
        if (batchEmbeddings.size != texts.size) {
            Log.d(
                CLOUD_EMBEDDING_DEBUG_LOG_TAG,
                "batch_size_mismatch runId=$debugRunId provider=${config.provider} model=${config.modelName} " +
                    "texts=${texts.size} embeddings=${batchEmbeddings.size}"
            )
        }

        val result = MutableList<FloatArray?>(texts.size) { index -> batchEmbeddings.getOrNull(index) }
        val missingIndexes = result.indices.filter { result[it] == null }
        if (missingIndexes.isEmpty()) return result

        Log.d(
            CLOUD_EMBEDDING_DEBUG_LOG_TAG,
            "single_fallback_start runId=$debugRunId provider=${config.provider} model=${config.modelName} missing=${missingIndexes.size}"
        )

        missingIndexes.forEach { index ->
            try {
                result[index] = aiService.generateEmbedding(config, texts[index], debugRunId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.d(
                    CLOUD_EMBEDDING_DEBUG_LOG_TAG,
                    "single_fallback_failed runId=$debugRunId provider=${config.provider} model=${config.modelName} index=$index " +
                        "error=${e::class.simpleName}:${e.message}"
                )
            }
        }
        return result
    }

    private fun logEmbeddingResult(debugRunId: Long?, marker: String, embeddings: List<FloatArray?>) {
        Log.d(
            CLOUD_EMBEDDING_DEBUG_LOG_TAG,
            "$marker runId=$debugRunId total=${embeddings.size} " +
                "present=${embeddings.count { it != null }} nulls=${embeddings.count { it == null }} " +
                "dims=${embeddings.mapNotNull { it?.size }.distinct().sorted()}"
        )
    }

    private companion object {
        private const val CLOUD_EMBEDDING_DEBUG_LOG_TAG = "CloudEmbeddingDebug"
    }
}
