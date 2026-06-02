package com.andrewwin.sumup.domain.ai.repository

interface ModelRepository {
    fun isModelExists(): Boolean
    fun getModelPath(): String
}






