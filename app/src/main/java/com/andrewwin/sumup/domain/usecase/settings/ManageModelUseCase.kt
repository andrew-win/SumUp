package com.andrewwin.sumup.domain.usecase.settings

import com.andrewwin.sumup.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface ManageModelUseCase {
    fun downloadModel(): Flow<Int>
    fun deleteModel()
    fun isModelExists(): Boolean
    fun getModelPath(): String
}

class ManageModelUseCaseImpl @Inject constructor(
    private val modelRepository: ModelRepository
) : ManageModelUseCase {
    override fun downloadModel(): Flow<Int> = modelRepository.downloadModel()
    override fun deleteModel() = modelRepository.deleteModel()
    override fun isModelExists(): Boolean = modelRepository.isModelExists()
    override fun getModelPath(): String = modelRepository.getModelPath()
}
