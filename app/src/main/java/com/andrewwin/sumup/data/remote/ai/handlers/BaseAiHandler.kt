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
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = requestBuilder.post(body).build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message = "Запит до ШІ не вдався: ${response.code}"
                throw when (response.code) {
                    429 -> AiRateLimitException(message)
                    in 500..599 -> AiProviderUnavailableException(message, response.code)
                    else -> AiServiceException(message, response.code)
                }
            }
            val responseBody = response.body?.string() ?: throw Exception("Порожня відповідь від сервера")
            parser(responseBody)
        }
    }

    protected fun executeGetRequest(
        request: Request,
        parser: (String) -> List<String>
    ): List<String> {
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Помилка завантаження моделей: ${response.code}")
            val body = response.body?.string() ?: throw Exception("Порожня відповідь")
            parser(body)
        }
    }
}
