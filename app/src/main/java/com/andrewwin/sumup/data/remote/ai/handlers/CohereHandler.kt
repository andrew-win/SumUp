package com.andrewwin.sumup.data.remote.ai.handlers

import com.andrewwin.sumup.domain.ai.model.AiModelConfig
import com.andrewwin.sumup.domain.ai.model.AiModelType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class CohereHandler(okHttpClient: OkHttpClient) : BaseAiHandler(okHttpClient) {

    override suspend fun fetchModels(apiKey: String, type: AiModelType): List<String> {
        val url = "$BASE_URL$MODELS_PATH"
        val request = Request.Builder()
            .url(url)
            .addHeader(HEADER_AUTHORIZATION, "$BEARER_PREFIX $apiKey")
            .get()
            .build()
            
        return executeGetRequest(request) { body ->
            val json = JSONObject(body)
            val models = json.getJSONArray(JSON_KEY_MODELS)
            val result = mutableListOf<String>()
            for (i in 0 until models.length()) {
                val name = models.getJSONObject(i).getString(JSON_KEY_NAME)
                if (isModelMatchingType(name, type)) {
                    result.add(name)
                }
            }
            result.sorted()
        }
    }

    override suspend fun generateResponse(
        config: AiModelConfig,
        prompt: String,
        content: String,
        expectJson: Boolean
    ): String {
        val url = "$BASE_URL$CHAT_PATH"
        val json = JSONObject().apply {
            put(JSON_KEY_MODEL, config.modelName)
            put(JSON_KEY_MESSAGE, "$prompt\n\n$content")
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader(HEADER_AUTHORIZATION, "$BEARER_PREFIX ${config.apiKey}")
            
        return executeRequest(requestBuilder, json) { response ->
            JSONObject(response).getString(JSON_KEY_TEXT)
        }
    }

    override suspend fun generateEmbeddings(
        config: AiModelConfig,
        texts: List<String>
    ): List<FloatArray?> {
        val url = "$BASE_URL$EMBED_PATH"
        val json = JSONObject().apply {
            put(JSON_KEY_MODEL, config.modelName)
            put(JSON_KEY_TEXTS, JSONArray().apply {
                texts.forEach { put(it) }
            })
            put(JSON_KEY_INPUT_TYPE, EMBED_INPUT_TYPE)
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader(HEADER_AUTHORIZATION, "$BEARER_PREFIX ${config.apiKey}")
            
        return executeRequest(requestBuilder, json) { response ->
            val embeddings = JSONObject(response).getJSONArray(JSON_KEY_EMBEDDINGS)
            List(texts.size) { index ->
                embeddings.optJSONArray(index)?.let { embedding ->
                    FloatArray(embedding.length()) { i -> embedding.getDouble(i).toFloat() }
                }
            }
        }
    }

    private fun isModelMatchingType(modelId: String, type: AiModelType): Boolean {
        val id = modelId.lowercase()
        val isEmbedding = id.contains("emb")
        val isExcluded = id.contains("whisper") || id.contains("tts") || id.contains("dall-e")
        return when (type) {
            AiModelType.EMBEDDING -> isEmbedding && !isExcluded
            AiModelType.SUMMARY -> !isEmbedding && !isExcluded
        }
    }

    private companion object {
        private const val BASE_URL = "https://api.cohere.com/v1"
        private const val MODELS_PATH = "/models"
        private const val CHAT_PATH = "/chat"
        private const val EMBED_PATH = "/embed"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val BEARER_PREFIX = "Bearer"
        private const val JSON_KEY_MODELS = "models"
        private const val JSON_KEY_NAME = "name"
        private const val JSON_KEY_MODEL = "model"
        private const val JSON_KEY_MESSAGE = "message"
        private const val JSON_KEY_TEXT = "text"
        private const val JSON_KEY_TEXTS = "texts"
        private const val JSON_KEY_INPUT_TYPE = "input_type"
        private const val JSON_KEY_EMBEDDINGS = "embeddings"
        private const val EMBED_INPUT_TYPE = "classification"
    }
}
