package com.andrewwin.sumup.data.remote

import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.domain.support.AiProviderUnavailableException
import com.andrewwin.sumup.domain.support.AiRateLimitException
import com.andrewwin.sumup.domain.support.DebugTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AiService(private val okHttpClient: OkHttpClient) {

    suspend fun fetchModels(provider: AiProvider, apiKey: String, type: AiModelType): List<String> =
        withContext(Dispatchers.IO) {
            val request = buildFetchModelsRequest(provider, apiKey)
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Помилка завантаження моделей: ${response.code}")
                val json = JSONObject(response.body?.string() ?: throw Exception("Порожня відповідь"))
                parseFetchModelsResponse(provider, type, json).sorted()
            }
        }

    private fun buildFetchModelsRequest(provider: AiProvider, apiKey: String): Request {
        val url = when (provider) {
            AiProvider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            AiProvider.GROQ -> "https://api.groq.com/openai/v1/models"
            AiProvider.OPENROUTER -> "https://openrouter.ai/api/v1/models"
            AiProvider.COHERE -> "https://api.cohere.com/v1/models"
            AiProvider.CHATGPT -> "https://api.openai.com/v1/models"
            AiProvider.CLAUDE -> "https://api.anthropic.com/v1/models"
        }
        return Request.Builder().url(url)
            .applyAuthHeaders(provider, apiKey)
            .get()
            .build()
    }

    private fun parseFetchModelsResponse(provider: AiProvider, type: AiModelType, json: JSONObject): List<String> {
        return when (provider) {
            AiProvider.GEMINI -> {
                json.getJSONArray("models").toObjectList()
                    .filter { model ->
                        val methods = model.getJSONArray("supportedGenerationMethods").toString()
                        when (type) {
                            AiModelType.SUMMARY -> methods.contains("generateContent")
                            AiModelType.EMBEDDING -> methods.contains("embedContent")
                        }
                    }
                    .map { it.getString("name").removePrefix("models/") }
            }
            else -> {
                val dataKey = if (provider == AiProvider.COHERE) "models" else "data"
                val idKey = if (provider == AiProvider.COHERE) "name" else "id"
                json.getJSONArray(dataKey).toObjectList()
                    .map { it.getString(idKey) }
                    .filter { isModelMatchingType(it, type) }
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

    suspend fun generateResponse(
        config: AiModelConfig,
        prompt: String,
        content: String,
        expectJson: Boolean = false
    ): String =
        withContext(Dispatchers.IO) {
            DebugTrace.d(
                "ai_service",
                "generateResponse provider=${config.provider} model=${config.modelName} expectJson=$expectJson promptPreview=${DebugTrace.preview(prompt, 220)} contentPreview=${DebugTrace.preview(content, 220)}"
            )
            when (config.provider) {
                AiProvider.GEMINI -> callGemini(config, prompt, content, expectJson)
                AiProvider.GROQ -> callOpenAiCompatible(config, prompt, content, "https://api.groq.com/openai/v1/chat/completions", expectJson)
                AiProvider.OPENROUTER -> callOpenAiCompatible(config, prompt, content, "https://openrouter.ai/api/v1/chat/completions", expectJson)
                AiProvider.CHATGPT -> callOpenAiCompatible(config, prompt, content, "https://api.openai.com/v1/chat/completions", expectJson)
                AiProvider.COHERE -> callCohere(config, prompt, content)
                AiProvider.CLAUDE -> callClaude(config, prompt, content, expectJson)
            }
        }

    suspend fun generateEmbedding(config: AiModelConfig, text: String): FloatArray =
        withContext(Dispatchers.IO) {
            when (config.provider) {
                AiProvider.GEMINI -> callGeminiEmbedding(config, text)
                AiProvider.CHATGPT -> callOpenAiCompatibleEmbedding(config, text, "https://api.openai.com/v1/embeddings")
                AiProvider.COHERE -> callCohereEmbedding(config, text)
                else -> throw Exception("Provider ${config.provider} does not support embeddings")
            }
        }

    private fun callGemini(config: AiModelConfig, prompt: String, content: String, expectJson: Boolean): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${config.modelName}:generateContent?key=${config.apiKey}"
        val json = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", "$prompt\n\n$content")))
            }))
            if (expectJson) {
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                })
            }
        }
        return executeRequest(Request.Builder().url(url), json) { response ->
            JSONObject(response)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }

    private fun callOpenAiCompatible(
        config: AiModelConfig,
        prompt: String,
        content: String,
        url: String,
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
        val requestBuilder = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
        return executeRequest(requestBuilder, json) { response ->
            JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    private fun callCohere(config: AiModelConfig, prompt: String, content: String): String {
        val url = "https://api.cohere.com/v1/chat"
        val json = JSONObject().apply {
            put("model", config.modelName)
            put("message", "$prompt\n\n$content")
        }
        val requestBuilder = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
        return executeRequest(requestBuilder, json) { response ->
            JSONObject(response).getString("text")
        }
    }

    private fun callClaude(config: AiModelConfig, prompt: String, content: String, expectJson: Boolean): String {
        val url = "https://api.anthropic.com/v1/messages"
        val json = JSONObject().apply {
            put("model", config.modelName)
            put("max_tokens", 1024)
            if (expectJson) {
                put("system", "Return only valid JSON. No markdown, no prose outside JSON.")
            }
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", "$prompt\n\n$content")
            }))
        }
        val requestBuilder = Request.Builder().url(url)
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
        return executeRequest(requestBuilder, json) { response ->
            JSONObject(response).getJSONArray("content").getJSONObject(0).getString("text")
        }
    }

    private fun callGeminiEmbedding(config: AiModelConfig, text: String): FloatArray {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${config.modelName}:embedContent?key=${config.apiKey}"
        val json = JSONObject().apply {
            put("taskType", "SEMANTIC_SIMILARITY")
            put("outputDimensionality", 768)
            put("content", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", text)))
            })
        }
        return executeRequest(Request.Builder().url(url), json) { response ->
            val values = JSONObject(response).getJSONObject("embedding").getJSONArray("values")
            FloatArray(values.length()) { i -> values.getDouble(i).toFloat() }
        }
    }

    private fun callOpenAiCompatibleEmbedding(config: AiModelConfig, text: String, url: String): FloatArray {
        val json = JSONObject().apply {
            put("model", config.modelName)
            put("input", text)
        }
        val requestBuilder = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
        return executeRequest(requestBuilder, json) { response ->
            val embedding = JSONObject(response).getJSONArray("data").getJSONObject(0).getJSONArray("embedding")
            FloatArray(embedding.length()) { i -> embedding.getDouble(i).toFloat() }
        }
    }

    private fun callCohereEmbedding(config: AiModelConfig, text: String): FloatArray {
        val url = "https://api.cohere.com/v1/embed"
        val json = JSONObject().apply {
            put("model", config.modelName)
            put("texts", JSONArray().put(text))
            put("input_type", "classification")
        }
        val requestBuilder = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
        return executeRequest(requestBuilder, json) { response ->
            val firstEmbedding = JSONObject(response).getJSONArray("embeddings").getJSONArray(0)
            FloatArray(firstEmbedding.length()) { i -> firstEmbedding.getDouble(i).toFloat() }
        }
    }

    private fun <T> executeRequest(requestBuilder: Request.Builder, json: JSONObject, parser: (String) -> T): T {
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = requestBuilder.post(body).build()
        okHttpClient.newCall(request).execute().use { response ->
            DebugTrace.d("ai_service", "http status=${response.code} url=${request.url.redact()}")
            if (!response.isSuccessful) {
                val message = "Запит до ШІ не вдався: ${response.code}"
                throw when (response.code) {
                    429 -> AiRateLimitException(message)
                    in 500..599 -> AiProviderUnavailableException(message, response.code)
                    else -> Exception(message)
                }
            }
            val body = response.body?.string() ?: throw Exception("Порожня відповідь від сервера")
            DebugTrace.d("ai_service", "rawBodyPreview=${DebugTrace.preview(body)}")
            return parser(body)
        }
    }

    private fun Request.Builder.applyAuthHeaders(provider: AiProvider, apiKey: String): Request.Builder = apply {
        when (provider) {
            AiProvider.GROQ, AiProvider.OPENROUTER, AiProvider.CHATGPT, AiProvider.COHERE ->
                addHeader("Authorization", "Bearer $apiKey")
            AiProvider.CLAUDE -> {
                addHeader("x-api-key", apiKey)
                addHeader("anthropic-version", "2023-06-01")
            }
            else -> Unit
        }
    }

    private fun JSONArray.toObjectList(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun okhttp3.HttpUrl.redact(): String {
        return newBuilder().query(null).build().toString()
    }
}





