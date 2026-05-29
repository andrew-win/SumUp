package com.andrewwin.sumup.data.remote.ai

import com.andrewwin.sumup.domain.entities.ai.AiModelConfig
import com.andrewwin.sumup.domain.entities.ai.AiModelType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class GeminiHandler(okHttpClient: OkHttpClient) : BaseAiHandler(okHttpClient) {

    override suspend fun fetchModels(apiKey: String, type: AiModelType): List<String> {
        val url = "$BASE_URL/models?key=$apiKey"
        val request = Request.Builder().url(url).get().build()
        return executeGetRequest(request) { body ->
            val json = JSONObject(body)
            val models = json.getJSONArray("models")
            val result = mutableListOf<String>()
            for (i in 0 until models.length()) {
                val model = models.getJSONObject(i)
                val methods = model.getJSONArray("supportedGenerationMethods").toString()
                val match = when (type) {
                    AiModelType.SUMMARY -> methods.contains("generateContent")
                    AiModelType.EMBEDDING -> methods.contains("embedContent")
                }
                if (match) {
                    result.add(model.getString("name").removePrefix(MODEL_PREFIX))
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
        val url = "$BASE_URL/models/${config.modelName}:generateContent?key=${config.apiKey}"
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

    override suspend fun generateEmbeddings(
        config: AiModelConfig,
        texts: List<String>
    ): List<FloatArray?> {
        if (texts.isEmpty()) return emptyList()
        return if (texts.size == 1) {
            listOf(callSingleGeminiEmbedding(config, texts.first()))
        } else {
            callBatchGeminiEmbeddings(config, texts)
        }
    }

    private fun callBatchGeminiEmbeddings(config: AiModelConfig, texts: List<String>): List<FloatArray?> {
        val modelName = config.modelName.removePrefix(MODEL_PREFIX)
        val url = "$BASE_URL/models/$modelName:batchEmbedContents?key=${config.apiKey}"
        val json = JSONObject().apply {
            put("requests", JSONArray().apply {
                texts.forEach { text ->
                    put(JSONObject().apply {
                        put("model", "$MODEL_PREFIX$modelName")
                        put("taskType", TASK_TYPE_SIMILARITY)
                        put("outputDimensionality", EMBEDDING_DIMENSIONALITY)
                        put("content", JSONObject().apply {
                            put("parts", JSONArray().put(JSONObject().put("text", text)))
                        })
                    })
                }
            })
        }
        return executeRequest(Request.Builder().url(url), json) { response ->
            val embeddings = JSONObject(response).getJSONArray("embeddings")
            List(texts.size) { index ->
                embeddings.optJSONObject(index)?.getJSONArray("values")?.let { values ->
                    FloatArray(values.length()) { i -> values.getDouble(i).toFloat() }
                }
            }
        }
    }

    private fun callSingleGeminiEmbedding(config: AiModelConfig, text: String): FloatArray {
        val modelName = config.modelName.removePrefix(MODEL_PREFIX)
        val url = "$BASE_URL/models/$modelName:embedContent?key=${config.apiKey}"
        val json = JSONObject().apply {
            put("taskType", TASK_TYPE_SIMILARITY)
            put("outputDimensionality", EMBEDDING_DIMENSIONALITY)
            put("content", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", text)))
            })
        }
        return executeRequest(Request.Builder().url(url), json) { response ->
            val values = JSONObject(response).getJSONObject("embedding").getJSONArray("values")
            FloatArray(values.length()) { i -> values.getDouble(i).toFloat() }
        }
    }

    private companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val MODEL_PREFIX = "models/"
        private const val TASK_TYPE_SIMILARITY = "SEMANTIC_SIMILARITY"
        private const val EMBEDDING_DIMENSIONALITY = 768
    }
}
