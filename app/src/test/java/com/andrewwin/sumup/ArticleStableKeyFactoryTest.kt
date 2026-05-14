package com.andrewwin.sumup

import com.andrewwin.sumup.data.remote.ArticleStableKeyFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArticleStableKeyFactoryTest {

    @Test
    fun buildYouTubeKey_usesVideoIdBeforeWatchUrl() {
        val firstKey = ArticleStableKeyFactory.buildYouTubeKey(
            sourceId = SOURCE_ID,
            videoId = FIRST_VIDEO_ID,
            url = "https://www.youtube.com/watch?v=$FIRST_VIDEO_ID"
        )
        val secondKey = ArticleStableKeyFactory.buildYouTubeKey(
            sourceId = SOURCE_ID,
            videoId = SECOND_VIDEO_ID,
            url = "https://www.youtube.com/watch?v=$SECOND_VIDEO_ID"
        )

        assertEquals("youtube:$SOURCE_ID:$FIRST_VIDEO_ID", firstKey)
        assertEquals("youtube:$SOURCE_ID:$SECOND_VIDEO_ID", secondKey)
        assertNotEquals(firstKey, secondKey)
    }

    private companion object {
        private const val SOURCE_ID = 9L
        private const val FIRST_VIDEO_ID = "dhs5rHiqE6E"
        private const val SECOND_VIDEO_ID = "Ceqf_KUo2gM"
    }
}
