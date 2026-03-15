package com.andrewwin.sumup.domain

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import kotlin.math.min

class ArticleImportanceScorer {

    fun score(
        article: Article,
        sourceType: SourceType,
        allArticles: List<Article> = emptyList()
    ): Float {
        if (article.content.length < MIN_CONTENT_LENGTH) return 0f

        if (checkIsSpam(article, sourceType)) return 0f

        val viewScore = computeViewScore(article.viewCount, sourceType)
        val factScore = computeFactScore(article.content)
        val lexicalScore = computeLexicalScore(article, allArticles)

        return (viewScore + factScore + lexicalScore).coerceAtMost(1.0f)
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
            viewCount < 10000 -> 0.2f
            viewCount < 50000 -> 0.3f
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
                    totalFactScore += 0.1f
                    i++
                    continue
                }

                if (i > 0 && word.isNotEmpty() && word[0].isUpperCase()) {
                    totalFactScore += 0.1f
                    i++
                    while (i < words.size && words[i].isNotEmpty() && words[i][0].isUpperCase()) {
                        i++
                    }
                } else {
                    i++
                }
            }
        }
        return min(totalFactScore, 0.3f)
    }

    private fun computeLexicalScore(article: Article, allArticles: List<Article>): Float {
        if (allArticles.isEmpty()) return 0f
        
        val articleWords = article.title.lowercase().split(Regex("\\s+")).toSet()
        if (articleWords.isEmpty()) return 0f

        var matchCount = 0
        for (other in allArticles) {
            if (other.url == article.url) continue // Skip self
            
            val otherWords = other.title.lowercase().split(Regex("\\s+")).toSet()
            if (otherWords.isEmpty()) continue

            val intersect = articleWords.intersect(otherWords).size.toFloat()
            val union = articleWords.union(otherWords).size.toFloat()
            val similarity = if (union > 0) intersect / union else 0f

            if (similarity > 0.1f) {
                matchCount++
            }
        }

        return min(matchCount * 0.1f, 0.3f)
    }

    companion object {
        private const val MIN_CONTENT_LENGTH = 150
        private const val STATIC_RSS_VIEW_SCORE = 0.2f
        const val IMPORTANCE_THRESHOLD = 0.5f

        private val SPAM_KEYWORDS = listOf("реклама", "промо", "промокод")
        private val PHONE_REGEX = Regex("(\\+?380|\\+?38|38|380)\\d{7,}")
        private val URL_REGEX = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    }
}
