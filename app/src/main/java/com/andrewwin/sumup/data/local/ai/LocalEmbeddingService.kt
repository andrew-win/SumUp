package com.andrewwin.sumup.data.local.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.andrewwin.sumup.domain.ai.embedding.LocalEmbeddingProvider
import com.andrewwin.sumup.domain.article.deduplication.EmbeddingUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer

class LocalEmbeddingService(
    private val context: Context
) : LocalEmbeddingProvider {
    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val sessionLock = Any()
    private var tokenizerSession: OrtSession? = null
    private var modelSession: OrtSession? = null
    private var initialized = false

    override val embeddingCacheType: String = EMBEDDING_CACHE_TYPE

    override suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            synchronized(sessionLock) {
                if (initialized && tokenizerSession != null && modelSession != null) {
                    return@synchronized true
                }

                Log.d(LOCAL_EMBEDDING_INIT_LOG_TAG, "local_embedding_init_start")
                val startMs = SystemClock.elapsedRealtime()
                runCatching {
                    closeSessions()

                    val tokenizerFile = getCachedAssetFile(TOKENIZER_ASSET_NAME)
                    val modelFile = getCachedAssetFile(MODEL_ASSET_NAME)

                    val tokenizerOptions = OrtSession.SessionOptions().apply {
                        setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
                        setIntraOpNumThreads(TOKENIZER_INTRA_OP_THREADS)
                        setInterOpNumThreads(TOKENIZER_INTER_OP_THREADS)
                        setMemoryPatternOptimization(true)
                        registerCustomOpLibrary(OrtxPackage.getLibraryPath())
                    }

                    val modelOptions = OrtSession.SessionOptions().apply {
                        setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
                        setIntraOpNumThreads(MODEL_INTRA_OP_THREADS)
                        setInterOpNumThreads(MODEL_INTER_OP_THREADS)
                        setMemoryPatternOptimization(true)
                    }

                    tokenizerSession = ortEnv.createSession(tokenizerFile.absolutePath, tokenizerOptions)
                    modelSession = ortEnv.createSession(modelFile.absolutePath, modelOptions)
                    initialized = true
                    true
                }.onFailure { error ->
                    closeSessions()
                    initialized = false
                    Log.e(LOCAL_EMBEDDING_INIT_LOG_TAG, "local_embedding_init_failed", error)
                }.getOrElse { false }.also { success ->
                    val durationMs = SystemClock.elapsedRealtime() - startMs
                    Log.d(
                        LOCAL_EMBEDDING_INIT_LOG_TAG,
                        "local_embedding_init_complete success=$success duration_ms=$durationMs"
                    )
                }
            }
        }
    }

    private fun getCachedAssetFile(assetName: String): File {
        val targetFile = File(context.cacheDir, assetName)
        if (targetFile.exists() && targetFile.length() > 0L) {
            return targetFile
        }

        targetFile.parentFile?.mkdirs()
        context.assets.open(assetName).use { input ->
            val tempFile = File(targetFile.absolutePath + TEMP_FILE_SUFFIX)
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
            if (targetFile.exists()) {
                targetFile.delete()
            }
            tempFile.renameTo(targetFile)
        }
        return targetFile
    }

    override suspend fun computeLocalEmbedding(text: String): FloatArray =
        withContext(Dispatchers.Default) {
            val tokenizer = synchronized(sessionLock) { tokenizerSession }
                ?: return@withContext FloatArray(EmbeddingUtils.LOCAL_EMBEDDING_DIM)
            val model = synchronized(sessionLock) { modelSession }
                ?: return@withContext FloatArray(EmbeddingUtils.LOCAL_EMBEDDING_DIM)
            runCatching {
                val tokenizedInput = tokenize(tokenizer, "$QUERY_PREFIX$text")
                val rawEmbedding = runModel(model, tokenizedInput)
                EmbeddingUtils.normalize(rawEmbedding)
            }.getOrDefault(FloatArray(EmbeddingUtils.LOCAL_EMBEDDING_DIM))
        }

    override fun close() {
        synchronized(sessionLock) {
            closeSessions()
            initialized = false
        }
    }

    private fun tokenize(session: OrtSession, text: String): TokenizedLocalInput {
        val inputName = session.inputNames.first()

        OnnxTensor.createTensor(ortEnv, arrayOf(text)).use { textTensor ->
            session.run(mapOf(inputName to textTensor)).use { result ->
                val outputNames = session.outputNames.toList()
                val tokensIndex = outputNames.indexOf(TOKENS_OUTPUT_NAME)
                if (tokensIndex < 0) {
                    error("Tokenizer output '$TOKENS_OUTPUT_NAME' not found. Actual outputs: $outputNames")
                }

                val tokenIds = result[tokensIndex].value.toLongArrayFlat()
                val inputIds = LongArray(MAX_TOKEN_LENGTH) { PAD_TOKEN_ID }
                val attentionMask = LongArray(MAX_TOKEN_LENGTH)
                val tokenCount = minOf(tokenIds.size, MAX_TOKEN_LENGTH)

                for (index in 0 until tokenCount) {
                    inputIds[index] = tokenIds[index]
                    attentionMask[index] = 1L
                }

                return TokenizedLocalInput(inputIds, attentionMask)
            }
        }
    }

    private fun runModel(session: OrtSession, tokenizedInput: TokenizedLocalInput): FloatArray {
        OnnxTensor.createTensor(
            ortEnv,
            LongBuffer.wrap(tokenizedInput.inputIds),
            longArrayOf(1, tokenizedInput.inputIds.size.toLong())
        ).use { inputIdsTensor ->
            OnnxTensor.createTensor(
                ortEnv,
                LongBuffer.wrap(tokenizedInput.attentionMask),
                longArrayOf(1, tokenizedInput.attentionMask.size.toLong())
            ).use { attentionMaskTensor ->
                var tokenTypeTensor: OnnxTensor? = null
                val inputs = mutableMapOf(
                    INPUT_IDS_NAME to inputIdsTensor,
                    ATTENTION_MASK_NAME to attentionMaskTensor
                )

                if (session.inputNames.contains(TOKEN_TYPE_IDS_NAME)) {
                    tokenTypeTensor = OnnxTensor.createTensor(
                        ortEnv,
                        LongBuffer.wrap(LongArray(tokenizedInput.inputIds.size)),
                        longArrayOf(1, tokenizedInput.inputIds.size.toLong())
                    )
                    inputs[TOKEN_TYPE_IDS_NAME] = tokenTypeTensor!!
                }

                try {
                    session.run(inputs).use { result ->
                        val outputTensor = result
                            .asSequence()
                            .mapNotNull { it.value as? OnnxTensor }
                            .firstOrNull()
                            ?: return FloatArray(EmbeddingUtils.LOCAL_EMBEDDING_DIM)

                        return meanPool(outputTensor, tokenizedInput.attentionMask)
                    }
                } finally {
                    tokenTypeTensor?.close()
                }
            }
        }
    }

    private fun meanPool(tensor: OnnxTensor, attentionMask: LongArray): FloatArray {
        val dim = tensor.info.shape.lastOrNull()?.toInt() ?: return FloatArray(EmbeddingUtils.LOCAL_EMBEDDING_DIM)
        val buffer = tensor.floatBuffer
        if (dim <= 0 || buffer.capacity() == 0) {
            return FloatArray(EmbeddingUtils.LOCAL_EMBEDDING_DIM)
        }

        val tokenCount = (buffer.capacity() / dim).coerceAtLeast(1)
        val pooled = FloatArray(dim)
        var validTokenCount = 0

        for (tokenIndex in 0 until tokenCount) {
            val shouldUseToken = tokenIndex < attentionMask.size && attentionMask[tokenIndex] == 1L
            for (dimensionIndex in 0 until dim) {
                val value = buffer.get()
                if (shouldUseToken) {
                    pooled[dimensionIndex] += value
                }
            }
            if (shouldUseToken) validTokenCount++
        }

        if (validTokenCount > 0) {
            for (index in pooled.indices) {
                pooled[index] /= validTokenCount
            }
        }

        return pooled
    }

    private fun Any.toLongArrayFlat(): LongArray {
        return when (this) {
            is LongArray -> this
            is IntArray -> this.map { it.toLong() }.toLongArray()
            is Array<*> -> {
                val result = mutableListOf<Long>()

                fun collect(value: Any?) {
                    when (value) {
                        is Long -> result += value
                        is Int -> result += value.toLong()
                        is LongArray -> result += value.toList()
                        is IntArray -> result += value.map { it.toLong() }
                        is Array<*> -> value.forEach { collect(it) }
                        null -> Unit
                        else -> error("Unsupported token value: ${value.javaClass.name}")
                    }
                }

                collect(this)
                result.toLongArray()
            }
            else -> error("Unsupported tokenizer tensor value: ${this.javaClass.name}")
        }
    }

    private data class TokenizedLocalInput(
        val inputIds: LongArray,
        val attentionMask: LongArray
    )

    private fun closeSessions() {
        tokenizerSession?.close()
        modelSession?.close()
        tokenizerSession = null
        modelSession = null
    }

    companion object {
        const val EMBEDDING_CACHE_TYPE = "LOCAL_MULTILINGUAL_E5_SMALL"

        private const val MODEL_ASSET_NAME = "multilingual-e5-small.onnx"
        private const val TOKENIZER_ASSET_NAME = "multilingual-e5-small_tokenizer.onnx"
        private const val QUERY_PREFIX = ""
        private const val MAX_TOKEN_LENGTH = 40
        private const val PAD_TOKEN_ID = 1L
        private const val TOKENS_OUTPUT_NAME = "tokens"
        private const val INPUT_IDS_NAME = "input_ids"
        private const val ATTENTION_MASK_NAME = "attention_mask"
        private const val TOKEN_TYPE_IDS_NAME = "token_type_ids"
        private const val TOKENIZER_INTRA_OP_THREADS = 2
        private const val TOKENIZER_INTER_OP_THREADS = 1
        private const val MODEL_INTRA_OP_THREADS = 2
        private const val MODEL_INTER_OP_THREADS = 1
        private const val LOCAL_EMBEDDING_INIT_LOG_TAG = "LocalEmbeddingInit"
        private const val TEMP_FILE_SUFFIX = ".tmp"
    }
}
