package com.andrewwin.sumup.data.remote.sources.youtube

import com.andrewwin.sumup.domain.ai.model.RemoteContentFetchStatus
import io.github.thoroldvix.api.TranscriptApiFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

private const val YOUTUBE_FEED_REQUEST_FAILED_MESSAGE = "YouTube feed request failed with code %s"

class YouTubeFetcher(
    private val okHttpClient: OkHttpClient
) {
    private val youtubeTranscriptApi = TranscriptApiFactory.createWithClient(OkHttpYoutubeClient(okHttpClient))

    fun fetchFeedBytes(url: String): Result<ByteArray> = runCatching {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error(YOUTUBE_FEED_REQUEST_FAILED_MESSAGE.format(response.code))
            }
            response.body.bytes()
        }
    }

    suspend fun <T> fetchFeedStream(
        url: String,
        block: suspend (InputStream) -> T
    ): Result<T> = runCatching {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error(YOUTUBE_FEED_REQUEST_FAILED_MESSAGE.format(response.code))
            }
            val stream = response.body.byteStream()
            withContext(Dispatchers.Default) {
                block(stream)
            }
        }
    }

    fun fetchTranscript(videoId: String): Result<YouTubeTranscriptFetchResult> = runCatching {
        val transcriptList = youtubeTranscriptApi.listTranscripts(videoId)
        val generatedTranscript = runCatching {
            transcriptList.findGeneratedTranscript("uk", "ru", "en")
        }
        val transcript = generatedTranscript.getOrElse {
            transcriptList.findTranscript("uk", "ru", "en")
        }
        YouTubeTranscriptFetchResult(
            transcriptContent = transcript.fetch(),
            status = if (generatedTranscript.isSuccess) {
                RemoteContentFetchStatus.YT_GENERATED
            } else {
                RemoteContentFetchStatus.YT_MANUAL
            }
        )
    }
}
