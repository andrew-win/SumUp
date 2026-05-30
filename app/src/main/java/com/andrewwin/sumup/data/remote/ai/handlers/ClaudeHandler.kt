package com.andrewwin.sumup.data.remote.ai.handlers

import com.andrewwin.sumup.domain.entities.ai.AiModelConfig
import com.andrewwin.sumup.domain.entities.ai.AiModelType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class ClaudeHandler(okHttpClient: OkHttpClient) : BaseAiHandler(okHttpClient) {

    override suspend fun fetchModels(apiKey: String, type: AiModelType): List<String> {
        val url = "$BASE_URL/models"
        val request = Request.Builder()
            .url(url)
            .addHeader(HEADER_API_KEY, apiKey)
            .addHeader(HEADER_VERSION, VERSION_VALUE)
            .get()
            .build()
            
        return executeGetRequest(request) { body ->
            val json = JSONObject(body)
            val data = json.getJSONArray("data")
            val result = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val id = data.getJSONObject(i).getString("id")
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
        val url = "$BASE_URL/messages"
        val json = JSONObject().apply {
            put("model", config.modelName)
            put("max_tokens", MAX_TOKENS)
            if (expectJson) {
                put("system", "Return only valid JSON. No markdown, no prose outside JSON.")
            }
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", "$prompt\n\n$content")
            }))
        }
        val requestBuilder = Request.Builder().url(url)
            .addHeader(HEADER_API_KEY, config.apiKey)
            .addHeader(HEADER_VERSION, VERSION_VALUE)
            
        return executeRequest(requestBuilder, json) { response ->
            JSONObject(response).getJSONArray("content").getJSONObject(0).getString("text")
        }
    }

    override suspend fun generateEmbeddings(
        config: AiModelConfig,
        texts: List<String>
    ): List<FloatArray?> {
        throw Exception("Claude provider does not support embeddings")
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
        private const val HEADER_API_KEY = "x-api-key"
        private const val HEADER_VERSION = "anthropic-version"
        private const val VERSION_VALUE = "2023-06-01"
        private const val MAX_TOKENS = 1024
    }
}
