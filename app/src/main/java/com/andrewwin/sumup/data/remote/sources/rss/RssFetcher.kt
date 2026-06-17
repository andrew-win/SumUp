package com.andrewwin.sumup.data.remote.sources.rss

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Reader

private const val RSS_REQUEST_FAILED_MESSAGE = "RSS request failed with code %s"

class RssFetcher(
    private val okHttpClient: OkHttpClient
) {
    fun fetchBody(url: String): Result<String> = runCatching {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error(RSS_REQUEST_FAILED_MESSAGE.format(response.code))
            }
            response.body.string()
        }
    }

    fun fetchFullContent(url: String): Result<String> = fetchBody(url)

    suspend fun <T> fetchBodyReader(
        url: String,
        block: suspend (Reader) -> T
    ): Result<T> = runCatching {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error(RSS_REQUEST_FAILED_MESSAGE.format(response.code))
            }
            val reader = response.body.charStream()
            withContext(Dispatchers.Default) {
                block(reader)
            }
        }
    }
}
