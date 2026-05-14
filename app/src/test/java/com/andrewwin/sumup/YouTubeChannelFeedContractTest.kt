package com.andrewwin.sumup

import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.RemoteArticleDataSource
import com.andrewwin.sumup.data.remote.RssParser
import com.andrewwin.sumup.data.remote.TelegramParser
import com.andrewwin.sumup.data.remote.YouTubeParser
import com.andrewwin.sumup.domain.source.SourceUrlNormalizer
import com.andrewwin.sumup.domain.source.SourceUrlValidator
import java.net.HttpURLConnection
import java.net.URL
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

class YouTubeChannelFeedContractTest {

    @Test
    fun youtubeSource_acceptsOnlyChannelIdAndKeepsItNormalized() {
        assertTrue(SourceUrlValidator.isValid(CHANNEL_ID, SourceType.YOUTUBE))

        val normalized = SourceUrlNormalizer.normalize(CHANNEL_ID, SourceType.YOUTUBE)

        assertEquals(CHANNEL_ID, normalized)
    }

    @Test
    fun remoteDataSource_buildsYoutubeFeedUrlFromChannelId() {
        val dataSource = RemoteArticleDataSource(
            okHttpClient = OkHttpClient(),
            rssParser = RssParser(OkHttpClient()),
            telegramParser = TelegramParser(),
            youtubeParser = YouTubeParser()
        )
        val method = RemoteArticleDataSource::class.java.getDeclaredMethod(
            "buildYouTubeFeedUrl",
            String::class.java
        )
        method.isAccessible = true

        val feedUrl = method.invoke(dataSource, CHANNEL_ID) as String

        assertEquals("https://www.youtube.com/feeds/videos.xml?channel_id=$CHANNEL_ID", feedUrl)
    }

    @Test
    fun youtubeFeedEndpoint_returnsAtomEntriesForChannelId() {
        val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$CHANNEL_ID"
        val connection = URL(feedUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = NETWORK_TIMEOUT_MS
        connection.readTimeout = NETWORK_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", USER_AGENT)

        assertEquals(200, connection.responseCode)

        val document = connection.inputStream.use { input ->
            DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(input)
        }
        val entries = document.getElementsByTagNameNS(ATOM_NAMESPACE, "entry")
        assertTrue("YouTube RSS feed should contain entries", entries.length > 0)

        val firstEntry = entries.item(0) as Element
        val videoId = firstEntry.getElementsByTagNameNS(YOUTUBE_NAMESPACE, "videoId")
            .item(0)
            ?.textContent
            .orEmpty()
        val title = firstEntry.getElementsByTagNameNS(ATOM_NAMESPACE, "title")
            .item(0)
            ?.textContent
            .orEmpty()
        val link = firstEntry.getElementsByTagNameNS(ATOM_NAMESPACE, "link")
            .item(0)
            ?.attributes
            ?.getNamedItem("href")
            ?.nodeValue
            .orEmpty()

        assertEquals(11, videoId.length)
        assertFalse("YouTube RSS entry title should not be blank", title.isBlank())
        assertTrue("YouTube RSS entry should expose a watch URL", link.contains("watch?v="))
    }

    private companion object {
        private const val CHANNEL_ID = "UCXuqSBlHAE6Xw-yeJA0Tunw"
        private const val NETWORK_TIMEOUT_MS = 15_000
        private const val ATOM_NAMESPACE = "http://www.w3.org/2005/Atom"
        private const val YOUTUBE_NAMESPACE = "http://www.youtube.com/xml/schemas/2015"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
