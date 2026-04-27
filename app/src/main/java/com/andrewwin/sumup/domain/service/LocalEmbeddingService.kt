package com.andrewwin.sumup.domain.service

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalEmbeddingService {
    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    private val ortThreadCount: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

    suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (ortSession != null) return@withContext true

            val modelFile = File(modelPath)
            if (!modelFile.exists()) return@withContext false

            val opts = OrtSession.SessionOptions().apply {
                setExecutionMode(OrtSession.SessionOptions.ExecutionMode.PARALLEL)
                setIntraOpNumThreads(ortThreadCount)
                setInterOpNumThreads(ortThreadCount)
                setMemoryPatternOptimization(true)
                registerCustomOpLibrary(OrtxPackage.getLibraryPath())
            }

            ortSession = ortEnv.createSession(modelPath, opts)
            true
        }.onFailure { e ->
            android.util.Log.e("LocalEmbeddingService", "Failed to initialize ONNX session", e)
        }.getOrDefault(false)
    }

    fun computeLocalEmbedding(text: String): FloatArray {
        val session = ortSession ?: return FloatArray(EmbeddingUtils.EMBEDDING_DIM)
        return runCatching {
            val inputName = session.inputNames.first()

            OnnxTensor.createTensor(ortEnv, arrayOf(text)).use { tensor ->
                session.run(mapOf(inputName to tensor)).use { results ->
                    var outTensor: OnnxTensor? = null

                    for (entry in results) {
                        if (entry.value is OnnxTensor) {
                            outTensor = entry.value as OnnxTensor
                            if (entry.key == "last_hidden_state") break
                        }
                    }

                    outTensor ?: return@runCatching FloatArray(EmbeddingUtils.EMBEDDING_DIM)

                    val buf = outTensor.floatBuffer
                    val dim = outTensor.info.shape.last().toInt()

                    if (dim == 0 || buf.capacity() == 0) {
                        return@runCatching FloatArray(EmbeddingUtils.EMBEDDING_DIM)
                    }

                    val tokens = (buf.capacity() / dim).coerceAtLeast(1)
                    val pooled = FloatArray(dim)

                    for (i in 0 until tokens) {
                        for (j in 0 until dim) {
                            pooled[j] += buf.get()
                        }
                    }

                    for (j in pooled.indices) {
                        pooled[j] /= tokens
                    }

                    pooled
                }
            }
        }.getOrDefault(FloatArray(EmbeddingUtils.EMBEDDING_DIM))
    }

    fun close() {
        ortSession?.close()
        ortSession = null
    }
}
