package com.andrewwin.sumup.domain.ai

interface LocalEmbeddingProvider {
    val embeddingCacheType: String

    suspend fun initialize(): Boolean
    suspend fun computeLocalEmbedding(text: String): FloatArray
    fun close()
}