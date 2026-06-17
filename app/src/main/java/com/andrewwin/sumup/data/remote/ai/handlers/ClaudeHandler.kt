package com.andrewwin.sumup.data.remote.ai.handlers

import com.andrewwin.sumup.domain.ai.model.AiModelConfig
import com.andrewwin.sumup.domain.ai.model.AiModelType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class ClaudeHandler(okHttpClient: OkHttpClient) : BaseAiHandler(okHttpClient) {

    override suspend fun fetchModels(apiKey: String, type: AiModelType): List<String> {
        val url = "$BASE_URL$MODELS_PATH"
        val request = Request.Builder()
            .url(url)
            .addHeader(HEADER_API_KEY, apiKey)
            .addHeader(HEADER_VERSION, VERSION_VALUE)
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
        val url = "$BASE_URL$MESSAGES_PATH"
        val json = JSONObject().apply {
            put(JSON_KEY_MODEL, config.modelName)
            put(JSON_KEY_MAX_TOKENS, MAX_TOKENS)
            if (expectJson) {
                put(JSON_KEY_SYSTEM, JSON_ONLY_SYSTEM_PROMPT)
            }
            put(JSON_KEY_MESSAGES, JSONArray().put(JSONObject().apply {
                put(JSON_KEY_ROLE, ROLE_USER)
                put(JSON_KEY_CONTENT, "$prompt\n\n$content")
            }))
        }
        val requestBuilder = Request.Builder().url(url)
            .addHeader(HEADER_API_KEY, config.apiKey)
            .addHeader(HEADER_VERSION, VERSION_VALUE)
            
        return executeRequest(requestBuilder, json) { response ->
            JSONObject(response).getJSONArray(JSON_KEY_CONTENT).getJSONObject(0).getString(JSON_KEY_TEXT)
        }
    }

    override suspend fun generateEmbeddings(
        config: AiModelConfig,
        texts: List<String>
    ): List<FloatArray?> {
        throw Exception(CLAUDE_EMBEDDINGS_UNSUPPORTED_MESSAGE)
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
        private const val BASE_URL = "https://api.anthropic.com/v1"
        private const val MODELS_PATH = "/models"
        private const val MESSAGES_PATH = "/messages"
        private const val HEADER_API_KEY = "x-api-key"
        private const val HEADER_VERSION = "anthropic-version"
        private const val VERSION_VALUE = "2023-06-01"
        private const val MAX_TOKENS = 1024
        private const val JSON_KEY_DATA = "data"
        private const val JSON_KEY_ID = "id"
        private const val JSON_KEY_MODEL = "model"
        private const val JSON_KEY_MAX_TOKENS = "max_tokens"
        private const val JSON_KEY_SYSTEM = "system"
        private const val JSON_KEY_MESSAGES = "messages"
        private const val JSON_KEY_ROLE = "role"
        private const val JSON_KEY_CONTENT = "content"
        private const val JSON_KEY_TEXT = "text"
        private const val ROLE_USER = "user"
        private const val JSON_ONLY_SYSTEM_PROMPT = "Return only valid JSON. No markdown, no prose outside JSON."
        private const val CLAUDE_EMBEDDINGS_UNSUPPORTED_MESSAGE = "Claude provider does not support embeddings"
    }
}
