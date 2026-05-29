package com.andrewwin.sumup.data.remote.ai

import com.andrewwin.sumup.domain.entities.ai.AiModelConfig
import com.andrewwin.sumup.domain.entities.ai.AiModelType
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
            .url("$baseUrl/models")
            .addHeader("Authorization", "Bearer $apiKey")
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
        val json = JSONObject().apply {
            put("model", config.modelName)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", "$prompt\n\n$content")
            }))
            if (expectJson) {
                put("response_format", JSONObject().apply {
                    put("type", "json_object")
                })
            }
        }
        val requestBuilder = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            
        return executeRequest(requestBuilder, json) { response ->
            JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    override suspend fun generateEmbeddings(
        config: AiModelConfig,
        texts: List<String>
    ): List<FloatArray?> {
        val json = JSONObject().apply {
            put("model", config.modelName)
            put("input", JSONArray().apply {
                texts.forEach { put(it) }
            })
        }
        val requestBuilder = Request.Builder()
            .url("$baseUrl/embeddings")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            
        return executeRequest(requestBuilder, json) { response ->
            val data = JSONObject(response).getJSONArray("data")
            List(texts.size) { index ->
                data.optJSONObject(index)?.getJSONArray("embedding")?.let { embedding ->
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
}
