package com.andrewwin.sumup.domain.ai

import com.andrewwin.sumup.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface LocalModelManager {
    fun downloadModel(): Flow<Int>
    fun deleteModel()
    fun isModelExists(): Boolean
    fun getModelPath(): String
}

class LocalModelManagerImpl @Inject constructor(
    private val modelRepository: ModelRepository
) : LocalModelManager {
    override fun downloadModel(): Flow<Int> = modelRepository.downloadModel()
    override fun deleteModel() = modelRepository.deleteModel()
    override fun isModelExists(): Boolean = modelRepository.isModelExists()
    override fun getModelPath(): String = modelRepository.getModelPath()
}









