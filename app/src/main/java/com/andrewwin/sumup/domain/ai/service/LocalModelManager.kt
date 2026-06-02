package com.andrewwin.sumup.domain.ai.service

import com.andrewwin.sumup.domain.ai.repository.ModelRepository
import javax.inject.Inject

interface LocalModelManager {
    fun isModelExists(): Boolean
    fun getModelPath(): String
}

class LocalModelManagerImpl @Inject constructor(
    private val modelRepository: ModelRepository
) : LocalModelManager {
    override fun isModelExists(): Boolean = modelRepository.isModelExists()
    override fun getModelPath(): String = modelRepository.getModelPath()
}









