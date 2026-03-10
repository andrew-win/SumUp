package com.andrewwin.sumup.data.repository

import android.content.Context
import com.andrewwin.sumup.domain.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

class ModelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) : ModelRepository {

    private val MODEL_URL = "https://huggingface.co/onnx-community/distiluse-base-multilingual-v2-merged-onnx/resolve/main/combined_tokenizer_embedded_model.onnx?download=true"
    private val MODEL_FILE_NAME = "dedup_model.onnx"

    override fun downloadModel(): Flow<Int> = flow {
        val file = getModelFile()
        val request = Request.Builder().url(MODEL_URL).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to download model: ${response.code}")
            val body = response.body ?: throw Exception("Empty response body")
            val totalBytes = body.contentLength()
            
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        downloaded += read
                        if (totalBytes > 0) {
                            emit((downloaded * 100 / totalBytes).toInt())
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun deleteModel() {
        val file = getModelFile()
        if (file.exists()) {
            file.delete()
        }
    }

    override fun isModelExists(): Boolean {
        return getModelFile().exists()
    }

    override fun getModelPath(): String {
        return getModelFile().absolutePath
    }

    private fun getModelFile(): File {
        return File(context.filesDir, MODEL_FILE_NAME)
    }
}
