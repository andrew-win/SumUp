package com.andrewwin.sumup.data.remote

import android.util.Log
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiService(private val okHttpClient: OkHttpClient) {

    suspend fun fetchModels(provider: AiProvider, apiKey: String, type: AiModelType): List<String> = withContext(Dispatchers.IO) {
        val url = when (provider) {
            AiProvider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            AiProvider.GROQ -> "https://api.groq.com/openai/v1/models"
            AiProvider.OPENROUTER -> "https://openrouter.ai/api/v1/models"
            AiProvider.COHERE -> "https://api.cohere.com/v1/models"
            AiProvider.CHATGPT -> "https://api.openai.com/v1/models"
            AiProvider.CLAUDE -> "https://api.anthropic.com/v1/models"
        }

        val requestBuilder = Request.Builder().url(url)
        when (provider) {
            AiProvider.GROQ, AiProvider.OPENROUTER, AiProvider.CHATGPT -> {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            AiProvider.COHERE -> {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            AiProvider.CLAUDE -> {
                requestBuilder.addHeader("x-api-key", apiKey)
                requestBuilder.addHeader("anthropic-version", "2023-06-01")
            }
            else -> Unit
        }

        val request = requestBuilder.get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Помилка завантаження моделей: ${response.code}")
            val responseBody = response.body?.string() ?: throw Exception("Порожня відповідь")
            val json = JSONObject(responseBody)
            
            val modelNames = mutableListOf<String>()
            when (provider) {
                AiProvider.GEMINI -> {
                    val models = json.getJSONArray("models")
                    for (i in 0 until models.length()) {
                        val model = models.getJSONObject(i)
                        val name = model.getString("name").removePrefix("models/")
                        val supportedMethods = model.getJSONArray("supportedGenerationMethods").toString()

                        Log.d("AiService", "[$i] $name → $supportedMethods")  // ← додай це

                        if (type == AiModelType.SUMMARY && supportedMethods.contains("generateContent")) {
                            modelNames.add(name)
                        } else if (type == AiModelType.EMBEDDING && supportedMethods.contains("embedContent")) {
                            modelNames.add(name)
                        }
                    }
                }
                else -> {
                    val dataKey = if (provider == AiProvider.COHERE) "models" else "data"
                    val idKey = if (provider == AiProvider.COHERE) "name" else "id"
                    val data = json.getJSONArray(dataKey)
                    for (i in 0 until data.length()) {
                        val modelId = data.getJSONObject(i).getString(idKey)
                        if (isModelMatchesType(modelId, type)) {
                            modelNames.add(modelId)
                        }
                    }
                }
            }
            modelNames.sorted()
        }
    }

    private fun isModelMatchesType(modelId: String, type: AiModelType): Boolean {
        val id = modelId.lowercase()
        val isEmbedding = id.contains("emb")
        val isExcluded = id.contains("whisper") || id.contains("tts") || id.contains("dall-e")
        
        return if (type == AiModelType.EMBEDDING) {
            isEmbedding && !isExcluded
        } else {
            !isEmbedding && !isExcluded
        }
    }

    suspend fun generateResponse(config: AiModelConfig, prompt: String, content: String): String = withContext(Dispatchers.IO) {
        when (config.provider) {
            AiProvider.GEMINI -> callGemini(config, prompt, content)
            AiProvider.GROQ -> callGroq(config, prompt, content)
            AiProvider.OPENROUTER -> callOpenRouter(config, prompt, content)
            AiProvider.COHERE -> callCohere(config, prompt, content)
            AiProvider.CHATGPT -> callChatGpt(config, prompt, content)
            AiProvider.CLAUDE -> callClaude(config, prompt, content)
        }
    }

    suspend fun generateEmbedding(config: AiModelConfig, text: String): FloatArray = withContext(Dispatchers.IO) {
        when (config.provider) {
            AiProvider.GEMINI -> callGeminiEmbedding(config, text)
            AiProvider.CHATGPT -> callChatGptEmbedding(config, text)
            AiProvider.COHERE -> callCohereEmbedding(config, text)
            else -> throw Exception("Provider ${config.provider} does not support embeddings")
        }
    }

    private fun callGeminiEmbedding(config: AiModelConfig, text: String, taskType: String = "SEMANTIC_SIMILARITY"): FloatArray {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${config.modelName}:embedContent?key=${config.apiKey}"
        val json = JSONObject().apply {
            put("taskType", taskType)
            put("outputDimensionality", 768)
            put("content", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", text)))
            })
        }
        return executeRequest(url, json) { response ->
            val values = JSONObject(response).getJSONObject("embedding").getJSONArray("values")
            FloatArray(values.length()) { i -> values.getDouble(i).toFloat() }
        }
    }

    private fun callChatGptEmbedding(config: AiModelConfig, text: String): FloatArray {
        val url = "https://api.openai.com/v1/embeddings"
        val json = JSONObject().apply {
            put("model", config.modelName)
            put("input", text)
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
        return executeRequest(url, json, requestBuilder) { response ->
            val data = JSONObject(response).getJSONArray("data").getJSONObject(0)
            val embedding = data.getJSONArray("embedding")
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
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
        return executeRequest(url, json, requestBuilder) { response ->
            val embeddings = JSONObject(response).getJSONArray("embeddings")
            val firstEmbedding = embeddings.getJSONArray(0)
            FloatArray(firstEmbedding.length()) { i -> firstEmbedding.getDouble(i).toFloat() }
        }
    }

    private fun callGemini(config: AiModelConfig, prompt: String, content: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${config.modelName}:generateContent?key=${config.apiKey}"
        val json = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", "$prompt\n\n$content")))
            }))
        }
        return executeRequest(url, json) { response ->
            val candidates = JSONObject(response).getJSONArray("candidates")
            candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        }
    }

    private fun callGroq(config: AiModelConfig, prompt: String, content: String): String {
        val url = "https://api.groq.com/openai/v1/chat/completions"
        val json = JSONObject().apply {
            put("model", config.modelName)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "$prompt\n\n$content")
                })
            })
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
        
        return executeRequest(url, json, requestBuilder) { response ->
            val choices = JSONObject(response).getJSONArray("choices")
            choices.getJSONObject(0).getJSONObject("message").getString("content")
        }
    }

    private fun callOpenRouter(config: AiModelConfig, prompt: String, content: String): String {
        val url = "https://openrouter.ai/api/v1/chat/completions"
        val json = JSONObject().apply {
            put("model", config.modelName)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "$prompt\n\n$content")
                })
            })
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")

        return executeRequest(url, json, requestBuilder) { response ->
            val choices = JSONObject(response).getJSONArray("choices")
            choices.getJSONObject(0).getJSONObject("message").getString("content")
        }
    }

    private fun callChatGpt(config: AiModelConfig, prompt: String, content: String): String {
        val url = "https://api.openai.com/v1/chat/completions"
        val json = JSONObject().apply {
            put("model", config.modelName)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "$prompt\n\n$content")
                })
            })
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")

        return executeRequest(url, json, requestBuilder) { response ->
            val choices = JSONObject(response).getJSONArray("choices")
            choices.getJSONObject(0).getJSONObject("message").getString("content")
        }
    }

    private fun callCohere(config: AiModelConfig, prompt: String, content: String): String {
        val url = "https://api.cohere.com/v1/chat"
        val json = JSONObject().apply {
            put("model", config.modelName)
            put("message", "$prompt\n\n$content")
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")

        return executeRequest(url, json, requestBuilder) { response ->
            JSONObject(response).getString("text")
        }
    }

    private fun callClaude(config: AiModelConfig, prompt: String, content: String): String {
        val url = "https://api.anthropic.com/v1/messages"
        val json = JSONObject().apply {
            put("model", config.modelName)
            put("max_tokens", 1024)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "$prompt\n\n$content")
                })
            })
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")

        return executeRequest(url, json, requestBuilder) { response ->
            val contentItems = JSONObject(response).getJSONArray("content")
            contentItems.getJSONObject(0).getString("text")
        }
    }

    private fun <T> executeRequest(
        url: String, 
        json: JSONObject, 
        requestBuilder: Request.Builder = Request.Builder().url(url),
        parser: (String) -> T
    ): T {
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = requestBuilder.post(body).build()
        
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message = "Запит до ШІ не вдався: ${response.code}"
                when (response.code) {
                    429 -> throw com.andrewwin.sumup.domain.exception.AiRateLimitException(message)
                    in 500..599 -> throw com.andrewwin.sumup.domain.exception.AiProviderUnavailableException(message, response.code)
                    else -> throw Exception(message)
                }
            }
            val responseBody = response.body?.string() ?: throw Exception("Порожня відповідь від сервера")
            return parser(responseBody)
        }
    }
}
