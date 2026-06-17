package com.andrewwin.sumup.data.remote.ai.handlers

import com.andrewwin.sumup.domain.ai.model.AiModelConfig
import com.andrewwin.sumup.domain.ai.model.AiModelType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

abstract class OpenAiCompatibleHandler(
    okHttpClient: OkHttpClient,
    private val baseUrl: String
) : BaseAiHandler(okHttpClient) {

    override suspend fun fetchModels(apiKey: String, type: AiModelType): List<String> {
        val request = Request.Builder()
            .url("$baseUrl$MODELS_PATH")
            .addHeader(HEADER_AUTHORIZATION, "$BEARER_PREFIX $apiKey")
            .get()
            .build()
        
        return executeGetRequest(request) { body ->
            val json = JSONObject(body)
            val data = json.getJSONArray(JSON_KEY_DATA)
            val result = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val id = data.getJSONObject(i).getString(JSON_KEY_ID)
                if (isModelMatchingType(id, type)) {
                    result.add(id)
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
        val json = JSONObject().apply {
            put(JSON_KEY_MODEL, config.modelName)
            put(JSON_KEY_MESSAGES, JSONArray().put(JSONObject().apply {
                put(JSON_KEY_ROLE, ROLE_USER)
                put(JSON_KEY_CONTENT, "$prompt\n\n$content")
            }))
            if (expectJson) {
                put(JSON_KEY_RESPONSE_FORMAT, JSONObject().apply {
                    put(JSON_KEY_TYPE, JSON_OBJECT_TYPE)
                })
            }
        }
        val requestBuilder = Request.Builder()
            .url("$baseUrl$CHAT_COMPLETIONS_PATH")
            .addHeader(HEADER_AUTHORIZATION, "$BEARER_PREFIX ${config.apiKey}")
            
        return executeRequest(requestBuilder, json) { response ->
            JSONObject(response)
                .getJSONArray(JSON_KEY_CHOICES)
                .getJSONObject(0)
                .getJSONObject(JSON_KEY_MESSAGE)
                .getString(JSON_KEY_CONTENT)
        }
    }

    override suspend fun generateEmbeddings(
        config: AiModelConfig,
        texts: List<String>
    ): List<FloatArray?> {
        val json = JSONObject().apply {
            put(JSON_KEY_MODEL, config.modelName)
            put(JSON_KEY_INPUT, JSONArray().apply {
                texts.forEach { put(it) }
            })
        }
        val requestBuilder = Request.Builder()
            .url("$baseUrl$EMBEDDINGS_PATH")
            .addHeader(HEADER_AUTHORIZATION, "$BEARER_PREFIX ${config.apiKey}")
            
        return executeRequest(requestBuilder, json) { response ->
            val data = JSONObject(response).getJSONArray(JSON_KEY_DATA)
            List(texts.size) { index ->
                data.optJSONObject(index)?.getJSONArray(JSON_KEY_EMBEDDING)?.let { embedding ->
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
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val BEARER_PREFIX = "Bearer"
        private const val MODELS_PATH = "/models"
        private const val CHAT_COMPLETIONS_PATH = "/chat/completions"
        private const val EMBEDDINGS_PATH = "/embeddings"
        private const val JSON_KEY_DATA = "data"
        private const val JSON_KEY_ID = "id"
        private const val JSON_KEY_MODEL = "model"
        private const val JSON_KEY_MESSAGES = "messages"
        private const val JSON_KEY_ROLE = "role"
        private const val JSON_KEY_CONTENT = "content"
        private const val JSON_KEY_RESPONSE_FORMAT = "response_format"
        private const val JSON_KEY_TYPE = "type"
        private const val JSON_KEY_CHOICES = "choices"
        private const val JSON_KEY_MESSAGE = "message"
        private const val JSON_KEY_INPUT = "input"
        private const val JSON_KEY_EMBEDDING = "embedding"
        private const val ROLE_USER = "user"
        private const val JSON_OBJECT_TYPE = "json_object"
    }
}
