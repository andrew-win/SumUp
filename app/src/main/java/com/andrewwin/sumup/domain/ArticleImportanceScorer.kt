package com.andrewwin.sumup.domain

import android.util.Log
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import kotlin.math.min

class ArticleImportanceScorer {

    fun score(
        article: Article,
        sourceType: SourceType
    ): Float {
        val contentLength = article.content.length
        if (contentLength < MIN_CONTENT_LENGTH) {
            Log.d(
                TAG_IMPORTANCE,
                "score_zero_length: sourceType=$sourceType, url=${article.url}, contentLen=$contentLength, minLen=$MIN_CONTENT_LENGTH, title='${article.title.take(LOG_TITLE_PREVIEW_LEN)}'"
            )
            return 0f
        }

        val spamReason = detectSpamReason(article, sourceType)
        if (spamReason != null) {
            Log.d(
                TAG_IMPORTANCE,
                "score_zero_spam: sourceType=$sourceType, url=${article.url}, contentLen=$contentLength, reason=$spamReason, title='${article.title.take(LOG_TITLE_PREVIEW_LEN)}', contentPreview='${article.content.take(LOG_CONTENT_PREVIEW_LEN)}'"
            )
            return 0f
        }

        val viewScore = computeViewScore(article.viewCount, sourceType)
        val factScore = computeFactScore(article.content)
        val total = viewScore + factScore
        Log.d(
            TAG_IMPORTANCE,
            "score_ok: sourceType=$sourceType, url=${article.url}, contentLen=$contentLength, viewCount=${article.viewCount}, viewScore=$viewScore, factScore=$factScore, total=$total, threshold=$IMPORTANCE_THRESHOLD, title='${article.title.take(LOG_TITLE_PREVIEW_LEN)}'"
        )

        return total
    }

    private fun checkIsSpam(article: Article, sourceType: SourceType): Boolean {
        return detectSpamReason(article, sourceType) != null
    }

    private fun detectSpamReason(article: Article, sourceType: SourceType): String? {
        val rawContent = article.content
        val text = rawContent.lowercase()
        val lenRaw = rawContent.length
        val withoutMarker = text.replace(SPAM_MARKER, "")
        val lenNoMarker = withoutMarker.length
        val withoutKeywords = SPAM_KEYWORDS.fold(withoutMarker) { acc, keyword ->
            acc.replace(keyword, "")
        }
        val lenNoKeywords = withoutKeywords.length
        val withoutPhones = PHONE_REGEX.replace(rawContent, "")
        val lenNoPhones = withoutPhones.length
        val withoutUrls = URL_REGEX.replace(rawContent, "")
        val lenNoUrls = withoutUrls.length
        Log.d(
            TAG_IMPORTANCE,
            "spam_trace: sourceType=$sourceType, url=${article.url}, len_raw=$lenRaw, len_no_marker=$lenNoMarker, len_no_keywords=$lenNoKeywords, len_no_phone=$lenNoPhones, len_no_urls=$lenNoUrls"
        )

        if (text.contains(SPAM_MARKER)) {
            Log.d(TAG_IMPORTANCE, "spam_hit: sourceType=$sourceType, url=${article.url}, reason=contains_spam_marker")
            return "contains_spam_marker"
        }

        if (SPAM_KEYWORDS.any { text.contains(it) }) {
            Log.d(TAG_IMPORTANCE, "spam_hit: sourceType=$sourceType, url=${article.url}, reason=contains_spam_keyword")
            return "contains_spam_keyword"
        }
        
        if (PHONE_REGEX.containsMatchIn(rawContent)) {
            Log.d(TAG_IMPORTANCE, "spam_hit: sourceType=$sourceType, url=${article.url}, reason=contains_phone_number")
            return "contains_phone_number"
        }
        
        val links = URL_REGEX.findAll(rawContent).map { it.value }
        val filteredLinks = if (sourceType == SourceType.TELEGRAM) {
            val channelHandle = extractTelegramHandle(article.url)
            if (channelHandle != null) {
                links.filter { !it.contains(channelHandle, ignoreCase = true) }
            } else links
        } else {
            links
        }
        
        val linksCount = filteredLinks.count()
        Log.d(
            TAG_IMPORTANCE,
            "spam_links: sourceType=$sourceType, url=${article.url}, totalLinks=$linksCount, threshold=$SPAM_LINK_THRESHOLD"
        )
        if (linksCount >= SPAM_LINK_THRESHOLD) {
            Log.d(TAG_IMPORTANCE, "spam_hit: sourceType=$sourceType, url=${article.url}, reason=too_many_links:$linksCount")
            return "too_many_links:$linksCount"
        }
        return null
    }

    private fun extractTelegramHandle(url: String): String? {
        return try {
            url.substringAfter("t.me/").substringBefore("/")
        } catch (e: Exception) {
            null
        }
    }

    private fun computeViewScore(viewCount: Long, sourceType: SourceType): Float {
        if (sourceType == SourceType.RSS || sourceType == SourceType.WEBSITE) {
            return STATIC_RSS_VIEW_SCORE
        }

        return when {
            viewCount < 500 -> 0.1f
            viewCount < 1000 -> 0.15f
            viewCount < 5000 -> 0.2f
            viewCount < 20000 -> 0.25f
            viewCount < 50000 -> 0.3f
            viewCount < 100000 -> 0.35f
            viewCount < 250000 -> 0.4f
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
        return min(totalFactScore, 0.6f)
    }

    companion object {
        private const val MIN_CONTENT_LENGTH = 65
        private const val STATIC_RSS_VIEW_SCORE = 0.25f
        const val IMPORTANCE_THRESHOLD = 0.5f
        private const val TAG_IMPORTANCE = "ArticleImportance"
        private const val LOG_TITLE_PREVIEW_LEN = 120
        private const val LOG_CONTENT_PREVIEW_LEN = 160

        private val SPAM_KEYWORDS = listOf("реклама", "промо", "промокод")
        private const val SPAM_MARKER = "[ad]"
        private val PHONE_REGEX = Regex("(\\+?380|\\+?38|38|380)\\d{7,}")
        private val URL_REGEX = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
        private const val SPAM_LINK_THRESHOLD = 5
    }
}
