package com.andrewwin.sumup.data.remote.sources.telegram

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private const val TELEGRAM_REQUEST_FAILED_MESSAGE = "Telegram request failed with code %s"

class TelegramFetcher(
    private val okHttpClient: OkHttpClient
) {
    fun fetchDocument(url: String): Result<Document> = runCatching {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error(TELEGRAM_REQUEST_FAILED_MESSAGE.format(response.code))
            }
            parseTelegramDocument(
                body = response.body,
                baseUrl = url
            )
        }
    }

    private fun parseTelegramDocument(
        body: ResponseBody,
        baseUrl: String
    ): Document {
        return body.byteStream().use { inputStream ->
            Jsoup.parse(inputStream, null, baseUrl)
        }
    }
}
