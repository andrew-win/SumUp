package com.andrewwin.sumup.domain.article.deduplication

import com.andrewwin.sumup.domain.article.model.Article
import java.security.MessageDigest

object ArticleDedupContentSignatureFactory {
    private const val SIGNATURE_VERSION = "title-fallback-content-v1"
    private const val CONTENT_FALLBACK_LIMIT = 280
    private val whitespaceRegex = Regex("\\s+")

    fun build(article: Article): String {
        val title = normalize(article.title)
        val fallbackContent = normalize(article.content).take(CONTENT_FALLBACK_LIMIT)
        val raw = if (title.isNotBlank()) title else fallbackContent
        return sha1("$SIGNATURE_VERSION\u241f$raw")
    }

    private fun normalize(value: String): String =
        whitespaceRegex.replace(value.trim().lowercase(), " ")

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}
