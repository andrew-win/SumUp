package com.andrewwin.sumup

import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.source.SourceUrlNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceUrlNormalizerTest {

    @Test
    fun telegramInputsNormalizeToPublicPreviewUrl() {
        val cases = mapOf(
            "xydessa" to "https://t.me/s/xydessa",
            "@xydessa" to "https://t.me/s/xydessa",
            "t.me/xydessa" to "https://t.me/s/xydessa",
            "www.t.me/xydessa" to "https://t.me/s/xydessa",
            "https://t.me/xydessa" to "https://t.me/s/xydessa",
            "https://www.t.me/xydessa" to "https://t.me/s/xydessa",
            "telegram.me/xydessa" to "https://t.me/s/xydessa",
            "t.me/s/xydessa" to "https://t.me/s/xydessa",
            "https://t.me/s/xydessa" to "https://t.me/s/xydessa",
            "tg://resolve?domain=xydessa" to "https://t.me/s/xydessa"
        )

        cases.forEach { (input, expected) ->
            assertEquals(expected, SourceUrlNormalizer.normalize(input, SourceType.TELEGRAM))
        }
    }
}
