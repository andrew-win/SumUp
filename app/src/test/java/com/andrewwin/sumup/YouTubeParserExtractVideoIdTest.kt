package com.andrewwin.sumup

import com.andrewwin.sumup.data.remote.YouTubeParser
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeParserExtractVideoIdTest {
    @Test
    fun youtubeParser_extractVideoIdFromUrl_handlesWatchAndShortsUrls() {
        val parser = YouTubeParser()
        val method = YouTubeParser::class.java.getDeclaredMethod("extractVideoIdFromUrl", String::class.java)
        method.isAccessible = true

        val watchId = method.invoke(parser, "https://www.youtube.com/watch?v=abcdefghijk") as String?
        val shortsId = method.invoke(parser, "https://www.youtube.com/shorts/ZYXWVUTSRQP") as String?

        assertEquals("abcdefghijk", watchId)
        assertEquals("ZYXWVUTSRQP", shortsId)
    }

    @Test
    fun youtubeParser_parseChannelDisplayName_readsFeedTitle() {
        val xml = """
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom">
              <link rel="self" href="http://www.youtube.com/feeds/videos.xml?channel_id=UCXoJ8kY9zpLBEz-8saaT3ew"/>
              <id>yt:channel:XoJ8kY9zpLBEz-8saaT3ew</id>
              <yt:channelId>XoJ8kY9zpLBEz-8saaT3ew</yt:channelId>
              <title>ТСН</title>
              <author>
                <name>Author fallback</name>
              </author>
              <entry>
                <title>Video title</title>
              </entry>
            </feed>
        """.trimIndent()
        val inputStream = ByteArrayInputStream(xml.toByteArray())

        val displayName = YouTubeParser().parseChannelDisplayName(inputStream)

        assertEquals("ТСН", displayName)
    }
}
