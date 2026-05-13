package com.andrewwin.sumup.domain.ai

interface CloudEmbeddingProvider {
    suspend fun generateEmbedding(text: String, debugRunId: Long? = null): FloatArray?
    suspend fun generateEmbeddings(texts: List<String>, debugRunId: Long? = null): List<FloatArray?>
}
