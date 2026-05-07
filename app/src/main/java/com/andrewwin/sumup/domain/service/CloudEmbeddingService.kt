package com.andrewwin.sumup.domain.service

import android.util.Log
import com.andrewwin.sumup.domain.usecase.ai.GenerateCloudEmbeddingUseCase

class CloudEmbeddingService(
    private val generateCloudEmbeddingUseCase: GenerateCloudEmbeddingUseCase
) {
    suspend fun getCloudEmbedding(text: String, debugRunId: Long? = null): FloatArray? {
        val cloudEmbedding = generateCloudEmbeddingUseCase(text, debugRunId) ?: return null
        return EmbeddingUtils.normalize(cloudEmbedding)
    }

    suspend fun getCloudEmbeddings(texts: List<String>, debugRunId: Long? = null): List<FloatArray?> {
        if (texts.isEmpty()) return emptyList()
        val normalizedEmbeddings = generateCloudEmbeddingUseCase(texts, debugRunId).map { embedding ->
            embedding?.let(EmbeddingUtils::normalize)
        }
        Log.d(
            CLOUD_EMBEDDING_DEBUG_LOG_TAG,
            "cloud_service_result runId=$debugRunId total=${normalizedEmbeddings.size} " +
                "present=${normalizedEmbeddings.count { it != null }} nulls=${normalizedEmbeddings.count { it == null }} " +
                "dims=${normalizedEmbeddings.mapNotNull { it?.size }.distinct().sorted()}"
        )
        return normalizedEmbeddings
    }

    private companion object {
        private const val CLOUD_EMBEDDING_DEBUG_LOG_TAG = "CloudEmbeddingDebug"
    }
}
