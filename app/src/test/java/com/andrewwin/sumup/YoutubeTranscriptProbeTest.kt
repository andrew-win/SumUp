package com.andrewwin.sumup

import io.github.thoroldvix.api.TranscriptApiFactory
import io.github.thoroldvix.api.TranscriptContent
import io.github.thoroldvix.api.YoutubeClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Test

class YoutubeTranscriptProbeTest {

    @Test
    fun probeChannelTranscripts() {
        val channelId = "UCXoJ8kY9zpLBEz-8saaT3ew"
        val maxVideos = 3
        val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"

        val http = OkHttpClient()
        val feedXml = http.newCall(Request.Builder().url(feedUrl).build()).execute().use { response ->
            check(response.isSuccessful) { "Feed HTTP ${response.code}" }
            response.body?.string().orEmpty()
        }

        val videoIds = extractVideoIds(feedXml).distinct().take(maxVideos)
        check(videoIds.isNotEmpty()) { "No video ids parsed from feed" }

        val transcriptApi = TranscriptApiFactory.createWithClient(OkHttpYoutubeClient(http))
        println("Channel: $channelId, videos to probe: ${videoIds.size}")

        videoIds.forEachIndexed { index, videoId ->
            println("[$index] https://www.youtube.com/watch?v=$videoId")
            runCatching {
                val transcriptList = transcriptApi.listTranscripts(videoId)
                val transcript = runCatching {
                    transcriptList.findGeneratedTranscript("uk", "ru", "en")
                }.getOrElse {
                    transcriptList.findTranscript("uk", "ru", "en")
                }
                val rawText = transcript.fetch().content
                    .mapNotNull { it.text?.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                val appLikeText = formatYoutubeTranscriptByTiming(transcript.fetch())
                println("  raw chars=${rawText.length}, app-like chars=${appLikeText.length}")
                println("  sample=${appLikeText.take(180)}")
            }.onFailure { e ->
                println("  FAIL: ${e::class.simpleName}: ${e.message}")
            }
        }
    }

    private fun extractVideoIds(feedXml: String): List<String> {
        val ids = Regex("<yt:videoId>([^<]+)</yt:videoId>")
            .findAll(feedXml)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (ids.isNotEmpty()) return ids
        return Regex("<id>yt:video:([^<]+)</id>")
            .findAll(feedXml)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun formatYoutubeTranscriptByTiming(transcript: TranscriptContent): String {
        val fragments = transcript.content
            .filter { !it.text.isNullOrBlank() }
            .sortedBy { it.start }
        if (fragments.isEmpty()) return ""

        val blocks = mutableListOf<String>()
        val current = StringBuilder()
        var blockStart = fragments.first().start
        var blockEnd = blockStart

        fragments.forEach { fragment ->
            val normalized = normalizeTranscriptFragment(fragment.text)
            if (normalized.isBlank()) return@forEach

            val fragmentEnd = fragment.start + fragment.dur
            val shouldFlush =
                current.isNotEmpty() &&
                    (fragment.start - blockEnd > YT_BLOCK_GAP_SECONDS ||
                        fragmentEnd - blockStart >= YT_BLOCK_WINDOW_SECONDS ||
                        current.length >= YT_BLOCK_MAX_CHARS)

            if (shouldFlush) {
                blocks += finalizeTranscriptBlock(current.toString())
                current.clear()
                blockStart = fragment.start
            }

            if (current.isNotEmpty()) current.append(' ')
            current.append(normalized)
            blockEnd = fragmentEnd
        }

        if (current.isNotEmpty()) {
            blocks += finalizeTranscriptBlock(current.toString())
        }

        return blocks.joinToString(separator = "\n\n")
    }

    private fun normalizeTranscriptFragment(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val withoutTags = raw.replace(Regex("<[^>]+>"), " ")
        return withoutTags
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun finalizeTranscriptBlock(rawBlock: String): String {
        val cleaned = rawBlock
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return ""
        return if (cleaned.last() == '.' || cleaned.last() == '!' || cleaned.last() == '?') cleaned else "$cleaned."
    }

    private class OkHttpYoutubeClient(private val client: OkHttpClient) : YoutubeClient {
        override fun get(url: String, headers: Map<String, String>): String {
            val request = Request.Builder().url(url).apply {
                headers.forEach { (name, value) -> addHeader(name, value) }
            }.build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "YouTube GET ${response.code}" }
                return response.body?.string().orEmpty()
            }
        }

        override fun post(url: String, json: String): String {
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "YouTube POST ${response.code}" }
                return response.body?.string().orEmpty()
            }
        }
    }

    private companion object {
        private const val YT_BLOCK_WINDOW_SECONDS = 22.0
        private const val YT_BLOCK_GAP_SECONDS = 2.2
        private const val YT_BLOCK_MAX_CHARS = 260
    }
}

