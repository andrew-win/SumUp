package com.andrewwin.sumup

import com.andrewwin.sumup.data.remote.YouTubeParser
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
}

