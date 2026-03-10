package com.andrewwin.sumup.data.remote

import com.andrewwin.sumup.data.local.entities.AiModelConfig
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

    suspend fun fetchModels(provider: AiProvider, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val url = when (provider) {
            AiProvider.GEMINI -> "https://generativelanguage.googleapis.com/v1/models?key=$apiKey"
            AiProvider.GROQ -> "https://api.groq.com/openai/v1/models"
        }

        val requestBuilder = Request.Builder().url(url)
        if (provider == AiProvider.GROQ) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
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
                        if (model.getJSONArray("supportedGenerationMethods").toString().contains("generateContent")) {
                            modelNames.add(name)
                        }
                    }
                }
                AiProvider.GROQ -> {
                    val data = json.getJSONArray("data")
                    for (i in 0 until data.length()) {
                        modelNames.add(data.getJSONObject(i).getString("id"))
                    }
                }
            }
            modelNames.sorted()
        }
    }

    suspend fun generateResponse(config: AiModelConfig, prompt: String, content: String): String = withContext(Dispatchers.IO) {
        when (config.provider) {
            AiProvider.GEMINI -> callGemini(config, prompt, content)
            AiProvider.GROQ -> callGroq(config, prompt, content)
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

    private fun executeRequest(
        url: String, 
        json: JSONObject, 
        requestBuilder: Request.Builder = Request.Builder().url(url),
        parser: (String) -> String
    ): String {
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = requestBuilder.post(body).build()
        
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Запит до ШІ не вдався: ${response.code}")
            val responseBody = response.body?.string() ?: throw Exception("Порожня відповідь від сервера")
            return parser(responseBody)
        }
    }
}
