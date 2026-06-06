package com.andrewwin.sumup.data.remote.sources.youtube

import io.github.thoroldvix.api.YoutubeClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpYoutubeClient(
    private val client: OkHttpClient
) : YoutubeClient {
    override fun get(url: String, headers: Map<String, String>): String {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> addHeader(name, value) }
        }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("YouTube request failed")
            return response.body.string()
        }
    }

    override fun post(url: String, json: String): String {
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("YouTube request failed")
            return response.body.string()
        }
    }
}
