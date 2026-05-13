package com.andrewwin.sumup

import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.source.SourceUrlValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceUrlValidatorTest {

    @Test
    fun telegramUrlFormatsAreValid() {
        val validTelegramInputs = listOf(
            "name",
            "@name",
            "t.me/name",
            "t.me/s/name",
            "www.t.me/name",
            "www.t.me/s/name",
            "https://t.me/name",
            "https://t.me/s/name",
            "https://www.t.me/name",
            "telegram.me/name",
            "tg://resolve?domain=name",
            "tg:resolve?domain=name"
        )

        validTelegramInputs.forEach { input ->
            assertTrue("$input should be a valid Telegram source", SourceUrlValidator.isValid(input, SourceType.TELEGRAM))
        }
    }

    @Test
    fun nonTelegramFormatsAreInvalidForTelegram() {
        val invalidTelegramInputs = listOf(
            "example.com/rss",
            "https://example.com/feed",
            "UC12345678901234567890",
            "youtube.com/channel/UC12345678901234567890"
        )

        invalidTelegramInputs.forEach { input ->
            assertFalse("$input should not be a valid Telegram source", SourceUrlValidator.isValid(input, SourceType.TELEGRAM))
        }
    }

    @Test
    fun telegramFormatsAreInvalidForRss() {
        val telegramInputs = listOf(
            "t.me/name",
            "www.t.me/name",
            "https://t.me/name",
            "https://www.t.me/name",
            "telegram.me/name",
            "tg://resolve?domain=name"
        )

        telegramInputs.forEach { input ->
            assertFalse("$input should not be a valid RSS source", SourceUrlValidator.isValid(input, SourceType.RSS))
        }
    }
}
