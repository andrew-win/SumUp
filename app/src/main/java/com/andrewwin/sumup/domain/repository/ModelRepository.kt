package com.andrewwin.sumup.domain.repository

interface ModelRepository {
    fun isModelExists(): Boolean
    fun getModelPath(): String
}






