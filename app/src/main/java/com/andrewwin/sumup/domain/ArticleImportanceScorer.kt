package com.andrewwin.sumup.domain

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import kotlin.math.min

class ArticleImportanceScorer {

    fun score(
        article: Article,
        sourceType: SourceType
    ): Float {
        if (article.content.length < MIN_CONTENT_LENGTH) return 0f

        if (checkIsSpam(article, sourceType)) return 0f

        val viewScore = computeViewScore(article.viewCount, sourceType)
        val factScore = computeFactScore(article.content)

        return viewScore + factScore
    }

    private fun checkIsSpam(article: Article, sourceType: SourceType): Boolean {
        val text = article.content.lowercase()
        
        if (SPAM_KEYWORDS.any { text.contains(it) }) return true
        
        if (PHONE_REGEX.containsMatchIn(article.content)) return true
        
        val links = URL_REGEX.findAll(article.content).map { it.value }
        val filteredLinks = if (sourceType == SourceType.TELEGRAM) {
            val channelHandle = extractTelegramHandle(article.url)
            if (channelHandle != null) {
                links.filter { !it.contains(channelHandle, ignoreCase = true) }
            } else links
        } else {
            links
        }
        
        return filteredLinks.count() >= 2
    }

    private fun extractTelegramHandle(url: String): String? {
        return try {
            url.substringAfter("t.me/").substringBefore("/")
        } catch (e: Exception) {
            null
        }
    }

    private fun computeViewScore(viewCount: Long, sourceType: SourceType): Float {
        if (sourceType == SourceType.RSS) return STATIC_RSS_VIEW_SCORE

        return when {
            viewCount < 500 -> 0.05f
            viewCount < 1000 -> 0.1f
            viewCount < 5000 -> 0.15f
            viewCount < 20000 -> 0.2f
            viewCount < 50000 -> 0.25f
            viewCount < 100000 -> 0.3f
            viewCount < 250000 -> 0.35f
            else -> 0.4f
        }
    }

    private fun computeFactScore(text: String): Float {
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        var totalFactScore = 0f

        sentences.forEach { sentence ->
            val words = sentence.trim().split(Regex("\\s+"))
            var i = 0
            while (i < words.size) {
                val word = words[i]
                if (word.isEmpty()) {
                    i++
                    continue
                }

                if (word.contains(Regex("\\d"))) {
                    totalFactScore += 0.075f
                    i++
                    continue
                }

                if (i > 0 && word.isNotEmpty() && word[0].isUpperCase()) {
                    totalFactScore += 0.075f
                    i++
                    while (i < words.size && words[i].isNotEmpty() && words[i][0].isUpperCase()) {
                        i++
                    }
                } else {
                    i++
                }
            }
        }
        return min(totalFactScore, 0.6f)
    }

    companion object {
        private const val MIN_CONTENT_LENGTH = 125
        private const val STATIC_RSS_VIEW_SCORE = 0.25f
        const val IMPORTANCE_THRESHOLD = 0.5f

        private val SPAM_KEYWORDS = listOf("реклама", "промо", "промокод")
        private val PHONE_REGEX = Regex("(\\+?380|\\+?38|38|380)\\d{7,}")
        private val URL_REGEX = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    }
}
