package com.andrewwin.sumup.data.repository

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import com.andrewwin.sumup.domain.repository.EmbeddingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class EmbeddingServiceImpl : EmbeddingService {
    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null

    companion object {
        private const val EMBEDDING_DIM = 768
        private const val OUTPUT_TENSOR_NAME = "last_hidden_state"
    }

    override suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (ortSession != null) return@withContext true
            val modelFile = File(modelPath)
            if (!modelFile.exists()) return@withContext false
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.registerCustomOpLibrary(OrtxPackage.getLibraryPath())
            ortSession = ortEnv.createSession(modelPath, sessionOptions)
            true
        }.getOrDefault(false)
    }

    override suspend fun getEmbedding(text: String): FloatArray = withContext(Dispatchers.Default) {
        val session = ortSession ?: return@withContext FloatArray(EMBEDDING_DIM)
        
        runCatching {
            val inputName = session.inputNames.first()
            OnnxTensor.createTensor(ortEnv, arrayOf(text)).use { tensor ->
                session.run(mapOf(inputName to tensor)).use { results ->
                    var outTensor: OnnxTensor? = null
                    for (entry in results) {
                        if (entry.value is OnnxTensor) {
                            outTensor = entry.value as OnnxTensor
                            if (entry.key == OUTPUT_TENSOR_NAME) break
                        }
                    }
                    outTensor ?: return@runCatching FloatArray(EMBEDDING_DIM)

                    val buffer = outTensor.floatBuffer
                    val floats = FloatArray(buffer.capacity()).also { buffer.get(it) }
                    val lastDim = outTensor.info.shape.last().toInt()
                    
                    if (lastDim == 0 || floats.isEmpty()) return@runCatching FloatArray(EMBEDDING_DIM)

                    val tokenCount = floats.size / lastDim
                    FloatArray(lastDim) { j ->
                        var sum = 0f
                        for (i in 0 until tokenCount) sum += floats[i * lastDim + j]
                        sum / tokenCount
                    }
                }
            }
        }.getOrDefault(FloatArray(EMBEDDING_DIM))
    }

    override fun close() {
        ortSession?.close()
        ortSession = null
    }
}
