package com.andrewwin.sumup.data.remote.ai.handlers

import com.andrewwin.sumup.domain.ai.service.AiProviderHandler
import com.andrewwin.sumup.domain.support.AiProviderUnavailableException
import com.andrewwin.sumup.domain.support.AiRateLimitException
import com.andrewwin.sumup.domain.support.AiServiceException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

abstract class BaseAiHandler(
    protected val okHttpClient: OkHttpClient
) : AiProviderHandler {

    protected fun <T> executeRequest(
        requestBuilder: Request.Builder,
        json: JSONObject,
        parser: (String) -> T
    ): T {
        val body = json.toString().toRequestBody(APPLICATION_JSON_MEDIA_TYPE.toMediaType())
        val request = requestBuilder.post(body).build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message = AI_REQUEST_FAILED_MESSAGE.format(response.code)
                throw when (response.code) {
                    RATE_LIMIT_HTTP_CODE -> AiRateLimitException(message)
                    in SERVER_ERROR_CODE_START..SERVER_ERROR_CODE_END -> AiProviderUnavailableException(message, response.code)
                    else -> AiServiceException(message, response.code)
                }
            }
            val responseBody = response.body?.string() ?: throw Exception(EMPTY_SERVER_RESPONSE_MESSAGE)
            parser(responseBody)
        }
    }

    protected fun executeGetRequest(
        request: Request,
        parser: (String) -> List<String>
    ): List<String> {
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception(MODEL_FETCH_FAILED_MESSAGE.format(response.code))
            val body = response.body?.string() ?: throw Exception(EMPTY_RESPONSE_MESSAGE)
            parser(body)
        }
    }

    private companion object {
        private const val APPLICATION_JSON_MEDIA_TYPE = "application/json"
        private const val AI_REQUEST_FAILED_MESSAGE = "AI request failed: %s"
        private const val EMPTY_SERVER_RESPONSE_MESSAGE = "Empty server response"
        private const val MODEL_FETCH_FAILED_MESSAGE = "Failed to load models: %s"
        private const val EMPTY_RESPONSE_MESSAGE = "Empty response"
        private const val RATE_LIMIT_HTTP_CODE = 429
        private const val SERVER_ERROR_CODE_START = 500
        private const val SERVER_ERROR_CODE_END = 599
    }
}
