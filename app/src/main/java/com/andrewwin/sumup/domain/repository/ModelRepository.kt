package com.andrewwin.sumup.domain.repository

import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    fun downloadModel(): Flow<Int>
    fun deleteModel()
    fun isModelExists(): Boolean
    fun getModelPath(): String
}






