package com.andrewwin.sumup

import com.andrewwin.sumup.data.remote.TelegramParser
import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramParserDisplayNameTest {
    @Test
    fun telegramParser_parseChannelDisplayName_removesTelegramSuffix() {
        val html = """
            <html>
              <head>
                <title>Україна Спортивна 🇺🇦 Правий Інсайд – Telegram</title>
              </head>
            </html>
        """.trimIndent()

        val displayName = TelegramParser().parseChannelDisplayName(html)

        assertEquals("Україна Спортивна 🇺🇦 Правий Інсайд", displayName)
    }
}
