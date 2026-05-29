package com.andrewwin.sumup.data.repository

import android.content.Context
import com.andrewwin.sumup.domain.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ModelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ModelRepository {

    override fun isModelExists(): Boolean = runCatching {
        context.assets.list("")?.contains(MODEL_FILE_NAME) == true
    }.getOrDefault(false)

    override fun getModelPath(): String = "assets://$MODEL_FILE_NAME"

    companion object {
        private const val MODEL_FILE_NAME = "multilingual-e5-small.onnx"
    }
}
