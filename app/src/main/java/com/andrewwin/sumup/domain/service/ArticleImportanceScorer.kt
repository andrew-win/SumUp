package com.andrewwin.sumup.domain.service

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType
import kotlin.math.max

class ArticleImportanceScorer {

    fun score(
        article: Article,
        averageViews: Long,
        sourceType: SourceType
    ): Float {
        if (article.content.length < MIN_CONTENT_LENGTH) {
            return 0f
        }

        if (HARD_SPAM_REGEX.containsMatchIn(article.content)) {
            return 0f
        }

        val viewScore = computeViewScore(
            viewCount = article.viewCount,
            averageViews = averageViews,
            sourceType = sourceType
        )
        val textForFacts = article.content
            .take(MAX_CONTENT_LENGTH_TO_ANALYZE)
            .let { URL_REGEX.replace(it, " ") }
            .replace('\n', ' ')
        val factScore = computeFactScore(textForFacts)
        val spamPenalty = computeSpamPenalty(article.content)

        return (viewScore + factScore - spamPenalty).coerceIn(0f, 1f)
    }

    private fun computeViewScore(viewCount: Long, averageViews: Long, sourceType: SourceType): Float {
        if (sourceType == SourceType.RSS) {
            return STATIC_RSS_VIEW_SCORE
        }

        val safeAverage = max(averageViews, MIN_BASELINE_VIEWS)
        val ratio = viewCount.coerceAtLeast(0L).toFloat() / safeAverage.toFloat()
        val viewScore = ratio * BASE_AVERAGE_SCORE
        return viewScore.coerceAtMost(MAX_VIEW_SCORE)
    }

    private fun computeFactScore(text: String): Float {
        var totalFactScore = 0f

        val specificMatches = SPECIFIC_DATA_REGEX.findAll(text).toList()
        totalFactScore += specificMatches.size * 0.15f

        val specificRanges = specificMatches.map { it.range }
        val numberMatches = NUMBER_REGEX.findAll(text).count { number ->
            specificRanges.none { range -> number.range.first >= range.first && number.range.last <= range.last }
        }
        totalFactScore += numberMatches * 0.1f

        totalFactScore += OFFICIAL_MARKER_REGEX.findAll(text).count() * 0.1f
        totalFactScore += QUOTE_REGEX.findAll(text).count() * 0.1f

        PROPER_NAME_REGEX.findAll(text).forEach { match ->
            val tokenCount = PROPER_NAME_TOKEN_REGEX.findAll(match.value).count()
            totalFactScore += if (tokenCount >= 3) 0.15f else 0.1f
        }

        totalFactScore -= CLICKBAIT_REGEX.findAll(text).count() * 0.2f

        return totalFactScore.coerceIn(0f, MAX_FACT_SCORE)
    }

    private fun computeSpamPenalty(fullText: String): Float {
        var penalty = 0f
        if (SPAM_MARKER_REGEX.containsMatchIn(fullText)) {
            penalty += PENALTY_STEP
        }
        return penalty
    }

    companion object {
        private const val MAX_CONTENT_LENGTH_TO_ANALYZE = 400
        private const val MIN_CONTENT_LENGTH = 50
        private const val MAX_VIEW_SCORE = 0.5f
        private const val MAX_FACT_SCORE = 0.5f
        private const val MIN_BASELINE_VIEWS = 1000L
        private const val STATIC_RSS_VIEW_SCORE = 0.25f
        private const val PENALTY_STEP = 0.2f
        private const val BASE_AVERAGE_SCORE = 0.28f

        const val IMPORTANCE_THRESHOLD = 0.425f

        private val URL_REGEX = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
        private val HARD_SPAM_REGEX = Regex("(?i)#(?:реклама|ad|sponsored|promo|advertisement)")

        private val SPAM_MARKER_REGEX = Regex(
            "(?i)\\[ad\\]|(?<=[^\\p{L}]|^)(?:реклама|рекламний|промо|промокод|спонсорський|на\\s+правах\\s+реклами|партнерський|партнерка|спецпроєкт|знижка|знижки|розпродаж|акція|розіграш|giveaway|advert|pr|ad|advertisement|advertising|commercial|promo\\s+code|discount(?:\\s+code|s)?|coupon|sponsored(?:\\s+post)?|paid\\s+advertisement|promoted|partner(?:ship)?|affiliate|special\\s+offer|offer|promotion|sale|clearance|sweepstakes|contest|raffle)(?=[^\\p{L}]|$)",
            RegexOption.IGNORE_CASE
        )
        private val SPECIFIC_DATA_REGEX = Regex(
            "\\b(?:\\d{4}\\s?(?:р(?:оку|ок|ік|\\.)?|y(?:ear|r)s?)?|\\d+(?:[.,]\\d+)?\\s?(?:%|відсотк(?:ів|и|а)?|percent|pct|грн|грив(?:ень|ні|ня)|₴|\\$|usd|дол(?:ар(?:ів|а)?)?|dollars?|bucks?|eur|€|євро|euros?|£|gbp|фунт(?:ів|а)?|pounds?|pln|зл(?:отих|отий)?|млн|мільйон(?:ів|а)?|m|millions?|млрд|мільярд(?:ів|а)?|b|bln|billions?|трлн|трильйон(?:ів|а)?|t|trn|trillions?|тис(?:яч|ячі|яча)?|k|thousands?|сот(?:ень|ні|ня)|hundreds?|десят(?:ків|ки|ок)|dozens?))\\b",
            RegexOption.IGNORE_CASE
        )
        private val NUMBER_REGEX = Regex("\\b\\d+(?:[.,]\\d+)?\\b")
        private val OFFICIAL_MARKER_REGEX = Regex(
            "\\b(?:згідно|заявив(?:ла)?|дані|повідомив(?:ла)?|за\\s+даними|офіційно|джерело|зазначив(?:ла)?|розповів(?:ла)?|наголосив(?:ла)?|підкреслив(?:ла)?|вважає|зазначається|повідомляється|передає|пише|підтвердив(?:ла)?|опублікував(?:ла)?|оприлюднив(?:ла)?|інформує|розпорядження|указ|закон|постанова|документ|звіт|статистика|заява|according\\s+to|stated?|reported?|data|officially|sources?|noted?|told|emphasized?|believes?|writes?|confirmed?|published|released?|informs?|decree|law|resolution|document|reports?|statistics?|statements?|announced?|claimed?)\\b",
            RegexOption.IGNORE_CASE
        )
        private val QUOTE_REGEX = Regex("\"[^\"]+\"|«[^»]+»")
        private val PROPER_NAME_REGEX = Regex("(?<=[\\p{L}\\d,;:)\\]\"»”’\\-—–])\\s+(?:[\\p{Lu}][\\p{Ll}'’\\-]+|[\\p{Lu}]{1,4}(?![\\p{L}]))(?:[\\s\\-]+(?:[\\p{Lu}][\\p{Ll}'’\\-]+|[\\p{Lu}]{1,4}(?![\\p{L}])))*")
        private val PROPER_NAME_TOKEN_REGEX = Regex("(?:[\\p{Lu}][\\p{Ll}'’\\-]+|[\\p{Lu}]{1,4}(?![\\p{L}]))")
        private val CLICKBAIT_REGEX = Regex(
            "\\b(?:шок(?:уючий)?|сенсац(?:ія|ійний|ійно)|ви\\s+не\\s+повірите|треш|жесть|вся\\s+правда|всю\\s+правду|ніхто\\s+не\\s+очікував|розриває\\s+мережу|почалося|злили|терміново|термінова\\s+заява|shock(?:ing)?|sensational?|you\\s+won'?t\\s+believe|wtf|whole\\s+truth|no\\s+one\\s+expected|breaks\\s+the\\s+internet|it\\s+began|it\\s+has\\s+begun|leaked?|urgent(?:ly)?|breaking(?:\\s+news)?|mind[- ]blowing|unbelievable|omg|insane)\\b",
            RegexOption.IGNORE_CASE
        )
    }
}