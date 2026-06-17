package com.andrewwin.sumup.data.remote.ai.handlers

import com.andrewwin.sumup.domain.ai.model.AiModelConfig
import com.andrewwin.sumup.domain.ai.model.AiModelType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class GeminiHandler(okHttpClient: OkHttpClient) : BaseAiHandler(okHttpClient) {

    override suspend fun fetchModels(apiKey: String, type: AiModelType): List<String> {
        val url = "$BASE_URL$MODELS_PATH?$QUERY_KEY=$apiKey"
        val request = Request.Builder().url(url).get().build()
        return executeGetRequest(request) { body ->
            val json = JSONObject(body)
            val models = json.getJSONArray(JSON_KEY_MODELS)
            val result = mutableListOf<String>()
            for (i in 0 until models.length()) {
                val model = models.getJSONObject(i)
                val methods = model.getJSONArray(JSON_KEY_SUPPORTED_GENERATION_METHODS).toString()
                val name = model.getString(JSON_KEY_NAME).removePrefix(MODEL_PREFIX)
                val match = when (type) {
                    AiModelType.SUMMARY -> methods.contains(METHOD_GENERATE_CONTENT) && name.contains(FLASH_MODEL_NAME_PART, ignoreCase = true)
                    AiModelType.EMBEDDING -> methods.contains(METHOD_EMBED_CONTENT)
                }
                if (match) {
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
        val url = "$BASE_URL$MODELS_PATH/${config.modelName}$GENERATE_CONTENT_PATH_SUFFIX?$QUERY_KEY=${config.apiKey}"
        val json = JSONObject().apply {
            put(JSON_KEY_CONTENTS, JSONArray().put(JSONObject().apply {
                put(JSON_KEY_PARTS, JSONArray().put(JSONObject().put(JSON_KEY_TEXT, "$prompt\n\n$content")))
            }))
            if (expectJson) {
                put(JSON_KEY_GENERATION_CONFIG, JSONObject().apply {
                    put(JSON_KEY_RESPONSE_MIME_TYPE, APPLICATION_JSON_MIME_TYPE)
                    createThinkingConfig(config.modelName)?.let { thinkingConfig ->
                        put(JSON_KEY_THINKING_CONFIG, thinkingConfig)
                    }
                })
            }
        }
        return executeRequest(Request.Builder().url(url), json) { response ->
            JSONObject(response)
                .getJSONArray(JSON_KEY_CANDIDATES)
                .getJSONObject(0)
                .getJSONObject(JSON_KEY_CONTENT)
                .getJSONArray(JSON_KEY_PARTS)
                .getJSONObject(0)
                .getString(JSON_KEY_TEXT)
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
        val url = "$BASE_URL$MODELS_PATH/$modelName$BATCH_EMBED_CONTENTS_PATH_SUFFIX?$QUERY_KEY=${config.apiKey}"
        val json = JSONObject().apply {
            put(JSON_KEY_REQUESTS, JSONArray().apply {
                texts.forEach { text ->
                    put(JSONObject().apply {
                        put(JSON_KEY_MODEL, "$MODEL_PREFIX$modelName")
                        put(JSON_KEY_TASK_TYPE, TASK_TYPE_SIMILARITY)
                        put(JSON_KEY_OUTPUT_DIMENSIONALITY, EMBEDDING_DIMENSIONALITY)
                        put(JSON_KEY_CONTENT, JSONObject().apply {
                            put(JSON_KEY_PARTS, JSONArray().put(JSONObject().put(JSON_KEY_TEXT, text)))
                        })
                    })
                }
            })
        }
        return executeRequest(Request.Builder().url(url), json) { response ->
            val embeddings = JSONObject(response).getJSONArray(JSON_KEY_EMBEDDINGS)
            List(texts.size) { index ->
                embeddings.optJSONObject(index)?.getJSONArray(JSON_KEY_VALUES)?.let { values ->
                    FloatArray(values.length()) { i -> values.getDouble(i).toFloat() }
                }
            }
        }
    }

    private fun callSingleGeminiEmbedding(config: AiModelConfig, text: String): FloatArray {
        val modelName = config.modelName.removePrefix(MODEL_PREFIX)
        val url = "$BASE_URL$MODELS_PATH/$modelName$EMBED_CONTENT_PATH_SUFFIX?$QUERY_KEY=${config.apiKey}"
        val json = JSONObject().apply {
            put(JSON_KEY_TASK_TYPE, TASK_TYPE_SIMILARITY)
            put(JSON_KEY_OUTPUT_DIMENSIONALITY, EMBEDDING_DIMENSIONALITY)
            put(JSON_KEY_CONTENT, JSONObject().apply {
                put(JSON_KEY_PARTS, JSONArray().put(JSONObject().put(JSON_KEY_TEXT, text)))
            })
        }
        return executeRequest(Request.Builder().url(url), json) { response ->
            val values = JSONObject(response).getJSONObject(JSON_KEY_EMBEDDING).getJSONArray(JSON_KEY_VALUES)
            FloatArray(values.length()) { i -> values.getDouble(i).toFloat() }
        }
    }

    private fun createThinkingConfig(modelName: String): JSONObject? {
        val normalizedModelName = modelName
            .removePrefix(MODEL_PREFIX)
            .lowercase()

        return when {
            normalizedModelName.startsWith(GEMINI_2_5_PREFIX) -> JSONObject().apply {
                put(JSON_KEY_THINKING_BUDGET, MIN_THINKING_BUDGET)
            }
            normalizedModelName.startsWith(GEMINI_3_PREFIX) || normalizedModelName.endsWith(LATEST_MODEL_SUFFIX) -> JSONObject().apply {
                put(JSON_KEY_THINKING_LEVEL, MIN_THINKING_LEVEL)
            }
            else -> null
        }
    }

    private companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val MODELS_PATH = "/models"
        private const val QUERY_KEY = "key"
        private const val MODEL_PREFIX = "models/"
        private const val GENERATE_CONTENT_PATH_SUFFIX = ":generateContent"
        private const val BATCH_EMBED_CONTENTS_PATH_SUFFIX = ":batchEmbedContents"
        private const val EMBED_CONTENT_PATH_SUFFIX = ":embedContent"
        private const val GEMINI_2_5_PREFIX = "gemini-2.5-"
        private const val GEMINI_3_PREFIX = "gemini-3"
        private const val LATEST_MODEL_SUFFIX = "-latest"
        private const val FLASH_MODEL_NAME_PART = "flash"
        private const val METHOD_GENERATE_CONTENT = "generateContent"
        private const val METHOD_EMBED_CONTENT = "embedContent"
        private const val APPLICATION_JSON_MIME_TYPE = "application/json"
        private const val MIN_THINKING_BUDGET = 0
        private const val MIN_THINKING_LEVEL = "minimal"
        private const val TASK_TYPE_SIMILARITY = "SEMANTIC_SIMILARITY"
        private const val EMBEDDING_DIMENSIONALITY = 768
        private const val JSON_KEY_MODELS = "models"
        private const val JSON_KEY_SUPPORTED_GENERATION_METHODS = "supportedGenerationMethods"
        private const val JSON_KEY_NAME = "name"
        private const val JSON_KEY_CONTENTS = "contents"
        private const val JSON_KEY_CONTENT = "content"
        private const val JSON_KEY_PARTS = "parts"
        private const val JSON_KEY_TEXT = "text"
        private const val JSON_KEY_GENERATION_CONFIG = "generationConfig"
        private const val JSON_KEY_RESPONSE_MIME_TYPE = "responseMimeType"
        private const val JSON_KEY_THINKING_CONFIG = "thinkingConfig"
        private const val JSON_KEY_CANDIDATES = "candidates"
        private const val JSON_KEY_REQUESTS = "requests"
        private const val JSON_KEY_MODEL = "model"
        private const val JSON_KEY_TASK_TYPE = "taskType"
        private const val JSON_KEY_OUTPUT_DIMENSIONALITY = "outputDimensionality"
        private const val JSON_KEY_EMBEDDINGS = "embeddings"
        private const val JSON_KEY_VALUES = "values"
        private const val JSON_KEY_EMBEDDING = "embedding"
        private const val JSON_KEY_THINKING_BUDGET = "thinkingBudget"
        private const val JSON_KEY_THINKING_LEVEL = "thinkingLevel"
    }
}
