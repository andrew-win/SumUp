package com.andrewwin.sumup.data.ml

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class BPEmbEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var isLoaded = false

    private val loadMutex = Mutex()

    @Volatile
    private var tokenizer: SentencePieceTokenizer? = null

    @Volatile
    private var vocab: HashMap<String, Int>? = null

    @Volatile
    private var embeddings: Array<FloatArray>? = null

    suspend fun load() = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext
        loadMutex.withLock {
            if (isLoaded) return@withLock
            val loadedVocab = loadVocab()
            val loadedEmbeddings = loadEmbeddings()
            val modelBytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
            val loadedTokenizer = SentencePieceTokenizer(modelBytes).also {
                it.setVocabulary(loadedVocab.keys)
            }
            vocab = loadedVocab
            embeddings = loadedEmbeddings
            tokenizer = loadedTokenizer
            isLoaded = true
        }
    }

    fun embed(text: String): FloatArray {
        check(isLoaded) { "BPEmbEngine is not loaded" }

        val localTokenizer = tokenizer ?: return FloatArray(EMBEDDING_DIM)
        val localVocab = vocab ?: return FloatArray(EMBEDDING_DIM)
        val localEmbeddings = embeddings ?: return FloatArray(EMBEDDING_DIM)

        val pieces = localTokenizer.encode(text.trim())
        if (pieces.isEmpty()) return FloatArray(EMBEDDING_DIM)

        val result = FloatArray(EMBEDDING_DIM)
        var count = 0

        for (piece in pieces) {
            val index = localVocab[piece] ?: continue
            if (index < 0 || index >= localEmbeddings.size) continue
            val vector = localEmbeddings[index]
            for (i in 0 until EMBEDDING_DIM) {
                result[i] += vector[i]
            }
            count++
        }

        if (count == 0) return FloatArray(EMBEDDING_DIM)
        val invCount = 1f / count
        for (i in 0 until EMBEDDING_DIM) {
            result[i] *= invCount
        }
        return result
    }

    fun similarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        var i = 0

        while (i <= EMBEDDING_DIM - 4) {
            val a0 = a[i]
            val a1 = a[i + 1]
            val a2 = a[i + 2]
            val a3 = a[i + 3]
            val b0 = b[i]
            val b1 = b[i + 1]
            val b2 = b[i + 2]
            val b3 = b[i + 3]

            dot += a0 * b0 + a1 * b1 + a2 * b2 + a3 * b3
            normA += a0 * a0 + a1 * a1 + a2 * a2 + a3 * a3
            normB += b0 * b0 + b1 * b1 + b2 * b2 + b3 * b3
            i += 4
        }

        while (i < EMBEDDING_DIM) {
            val av = a[i]
            val bv = b[i]
            dot += av * bv
            normA += av * av
            normB += bv * bv
            i++
        }

        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0f) dot / denom else 0f
    }

    fun findSimilar(
        query: String,
        headlines: List<String>,
        threshold: Float = 0.47f,
        topK: Int = 5
    ): List<Pair<Float, String>> {
        if (!isLoaded || headlines.isEmpty()) return emptyList()
        val queryEmbedding = embed(query)
        return headlines
            .asSequence()
            .map { headline -> similarity(queryEmbedding, embed(headline)) to headline }
            .filter { it.first >= threshold }
            .sortedByDescending { it.first }
            .take(topK)
            .toList()
    }

    fun release() {
        tokenizer = null
        vocab = null
        embeddings = null
        isLoaded = false
        System.gc()
    }

    private fun loadVocab(): HashMap<String, Int> {
        return context.assets.open(VOCAB_FILE).bufferedReader().useLines { lines ->
            HashMap<String, Int>().apply {
                lines.forEachIndexed { index, token ->
                    if (token.isNotEmpty()) {
                        put(token, index)
                    }
                }
            }
        }
    }

    private fun loadEmbeddings(): Array<FloatArray> {
        return context.assets.open(EMBEDDINGS_FILE).use { input ->
            DataInputStream(BufferedInputStream(input)).use { data ->
                val header = ByteArray(8)
                data.readFully(header)
                val headerBuffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val vocabSize = headerBuffer.int
                val dim = headerBuffer.int
                require(dim == EMBEDDING_DIM) { "Unexpected embedding dim: $dim" }

                Array(vocabSize) {
                    val vector = FloatArray(dim)
                    val bytes = ByteArray(dim * 2)
                    data.readFully(bytes)
                    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until dim) {
                        vector[i] = halfToFloat(buffer.short)
                    }
                    vector
                }
            }
        }
    }

    private fun halfToFloat(half: Short): Float {
        val h = half.toInt() and 0xFFFF
        val exp = (h shr 10) and 0x1F
        val mant = h and 0x3FF
        return when {
            exp == 0 -> (if (h < 0) -1f else 1f) * mant / 16777216f
            exp == 31 -> if (mant == 0) Float.POSITIVE_INFINITY else Float.NaN
            else -> java.lang.Float.intBitsToFloat(
                ((h and 0x8000) shl 16) or ((exp + 112) shl 23) or (mant shl 13)
            )
        }
    }

    companion object {
        private const val EMBEDDING_DIM = 200
        private const val MODEL_FILE = "uk_bpe.model"
        private const val EMBEDDINGS_FILE = "uk_embeddings.bin"
        private const val VOCAB_FILE = "uk_vocab.txt"
    }
}
