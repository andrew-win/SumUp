package com.andrewwin.sumup.data.remote.ai.handlers

import com.andrewwin.sumup.domain.ai.model.AiModelConfig
import com.andrewwin.sumup.domain.ai.model.AiModelType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class CohereHandler(okHttpClient: OkHttpClient) : BaseAiHandler(okHttpClient) {

    override suspend fun fetchModels(apiKey: String, type: AiModelType): List<String> {
        val url = "$BASE_URL/models"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
            
        return executeGetRequest(request) { body ->
            val json = JSONObject(body)
            val models = json.getJSONArray("models")
            val result = mutableListOf<String>()
            for (i in 0 until models.length()) {
                val name = models.getJSONObject(i).getString("name")
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
        val url = "$BASE_URL/chat"
        val json = JSONObject().apply {
            put("model", config.modelName)
            put("message", "$prompt\n\n$content")
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            
        return executeRequest(requestBuilder, json) { response ->
            JSONObject(response).getString("text")
        }
    }

    override suspend fun generateEmbeddings(
        config: AiModelConfig,
        texts: List<String>
    ): List<FloatArray?> {
        val url = "$BASE_URL/embed"
        val json = JSONObject().apply {
            put("model", config.modelName)
            put("texts", JSONArray().apply {
                texts.forEach { put(it) }
            })
            put("input_type", EMBED_INPUT_TYPE)
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            
        return executeRequest(requestBuilder, json) { response ->
            val embeddings = JSONObject(response).getJSONArray("embeddings")
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
        private const val EMBED_INPUT_TYPE = "classification"
    }
}
