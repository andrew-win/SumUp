package com.andrewwin.sumup.domain.ai.embedding

interface LocalEmbeddingProvider {
    val embeddingCacheType: String

    suspend fun initialize(): Boolean
    suspend fun computeLocalEmbedding(text: String): FloatArray
    fun close()
}
