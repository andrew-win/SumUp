package com.andrewwin.sumup.domain.repository

interface EmbeddingService {
    suspend fun initialize(modelPath: String): Boolean
    suspend fun getEmbedding(text: String): FloatArray
    fun close()
}






