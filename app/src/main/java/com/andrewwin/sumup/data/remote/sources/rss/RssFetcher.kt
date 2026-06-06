package com.andrewwin.sumup.data.remote.sources.rss

import okhttp3.OkHttpClient
import okhttp3.Request

class RssFetcher(
    private val okHttpClient: OkHttpClient
) {
    fun fetchBody(url: String): Result<String> = runCatching {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("RSS request failed with code ${response.code}")
            }
            response.body.string()
        }
    }

    fun fetchFullContent(url: String): Result<String> = fetchBody(url)
}
