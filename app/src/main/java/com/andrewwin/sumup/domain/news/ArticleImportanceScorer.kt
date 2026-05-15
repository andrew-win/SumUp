package com.andrewwin.sumup.domain.news

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.SourceType

class ArticleImportanceScorer {

    fun score(
        article: Article,
        averageViews: Long,
        sourceType: SourceType
    ): Float {
        val fullText = "${article.title} ${article.content}"
        if (fullText.length < MIN_TITLE_AND_CONTENT_LENGTH) {
            return 0f
        }

        if (ZERO_SCORE_KEYWORD_REGEX.containsMatchIn(fullText)) {
            return 0f
        }

        val textForFacts = "${article.title} ${article.content.take(CONTENT_SYMBOLS_FOR_FACTS)}"
            .let { URL_REGEX.replace(it, " ") }
            .replace('\n', ' ')

        return (
            computeViewsScore(article.viewCount, averageViews, sourceType) +
                computeFactsScore(textForFacts) +
                computeLengthBonus(article.content) -
                computeKeywordPenalty(fullText)
            ).coerceIn(0f, 1f)
    }

    private fun computeViewsScore(
        currentViews: Long,
        averageViews: Long,
        sourceType: SourceType
    ): Float {
        if (sourceType == SourceType.RSS) return RSS_FIXED_VIEWS_SCORE
        if (averageViews <= 0L) return 0f
        return currentViews.coerceAtLeast(0L).toFloat() / averageViews.toFloat() * VIEWS_FACTOR
    }

    private fun computeFactsScore(text: String): Float {
        val entityBonus = ENTITY_REGEX.findAll(text).count() * FACT_BONUS_STEP
        val numberBonus = countNumbers(text) * FACT_BONUS_STEP
        return (entityBonus + numberBonus).coerceAtMost(MAX_FACTS_SCORE)
    }

    private fun computeLengthBonus(content: String): Float {
        return when {
            content.length >= EXACT_LENGTH_BONUS_THRESHOLD -> EXACT_LENGTH_BONUS
            content.length > MEDIUM_LENGTH_BONUS_THRESHOLD -> MEDIUM_LENGTH_BONUS
            content.length > SHORT_LENGTH_BONUS_THRESHOLD -> SHORT_LENGTH_BONUS
            else -> 0f
        }
    }

    private fun computeKeywordPenalty(fullText: String): Float {
        return CLICKBAIT_KEYWORD_REGEX.findAll(fullText).count() * KEYWORD_PENALTY_STEP
    }

    private fun countNumbers(text: String): Int {
        val ranges = buildList {
            addAll(DIGIT_NUMBER_REGEX.findAll(text).map { it.range })
            addAll(WRITTEN_NUMBER_SEQUENCE_REGEX.findAll(text).map { it.range })
        }

        return ranges
            .sortedWith(compareBy<IntRange> { it.first }.thenByDescending { it.last })
            .fold(mutableListOf<IntRange>()) { accepted, candidate ->
                if (accepted.none { candidate.first >= it.first && candidate.last <= it.last }) {
                    accepted.add(candidate)
                }
                accepted
            }
            .size
    }

    companion object {
        private const val CONTENT_SYMBOLS_FOR_FACTS = 200
        private const val MIN_TITLE_AND_CONTENT_LENGTH = 50
        private const val VIEWS_FACTOR = 0.5f
        private const val RSS_FIXED_VIEWS_SCORE = 0.35f
        private const val MAX_FACTS_SCORE = 0.6f
        private const val FACT_BONUS_STEP = 0.15f
        private const val SHORT_LENGTH_BONUS_THRESHOLD = 100
        private const val MEDIUM_LENGTH_BONUS_THRESHOLD = 125
        private const val EXACT_LENGTH_BONUS_THRESHOLD = 150
        private const val SHORT_LENGTH_BONUS = 0.1f
        private const val MEDIUM_LENGTH_BONUS = 0.125f
        private const val EXACT_LENGTH_BONUS = 0.15f
        private const val KEYWORD_PENALTY_STEP = 0.15f

        const val IMPORTANCE_THRESHOLD = 0.5f

        private val URL_REGEX = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
        private val ZERO_SCORE_KEYWORD_REGEX = Regex(
            "(?<!\\p{L})(?:реклама|рекламний|промо|промокод|спонсорський|на\\s+правах\\s+реклами|ad|advert|advertisement|sponsored|promo\\s+code|discount\\s+code|coupon)(?!\\p{L})",
            RegexOption.IGNORE_CASE
        )
        private val ENTITY_REGEX = Regex("(?<!\\p{L})(?:[\\p{Lu}][\\p{Ll}'’-]+|[A-Z]{2,})(?:[\\s-]+(?:[\\p{Lu}][\\p{Ll}'’-]+|[A-Z]{2,}))*")
        private val DIGIT_NUMBER_REGEX = Regex(
            "(?<![\\p{L}\\d])\\d{1,3}(?:(?:[\\s.,])\\d{3})*(?:[.,]\\d+)?(?:\\s?(?:%|відсотк\\p{L}*|процент\\p{L}*|percent|pct|грн|грив\\p{L}*|₴|usd|дол\\p{L}*|\\$|eur|євро|€|gbp|фунт\\p{L}*|£|pln|зл\\p{L}*|тис\\p{L}*|тисяч\\p{L}*|тыс\\p{L}*|тысяч\\p{L}*|k|млн|мільйон\\p{L}*|миллион\\p{L}*|million\\p{L}*|m|млрд|мільярд\\p{L}*|миллиард\\p{L}*|billion\\p{L}*|b|bln|трлн|трильйон\\p{L}*|триллион\\p{L}*|trillion\\p{L}*|t|trn))?(?![\\p{L}\\d])",
            RegexOption.IGNORE_CASE
        )
        private val WRITTEN_NUMBER_SEQUENCE_REGEX = Regex(
            "(?<!\\p{L})(?:один|одна|одне|одного|одній|одним|одними|перш\\p{L}*|два|дві|двох|двом|двома|друг\\p{L}*|три|трьох|трьом|трьома|трет\\p{L}*|чотири|чотирьох|чотирьом|чотирма|четвер\\p{L}*|п['’]?ять|п['’]?яти|п['’]?ятьох|п['’]?ятьма|п['’]?ят\\p{L}*|шість|шести|шістьох|шістьма|шост\\p{L}*|сім|семи|сімох|сьома|сьом\\p{L}*|вісім|восьми|вісьмох|вісьма|восьм\\p{L}*|дев['’]?ять|дев['’]?яти|дев['’]?ятьох|дев['’]?ятьма|дев['’]?ят\\p{L}*|десять|десяти|десятьох|десятьма|десят\\p{L}*|одинадцять|одинадцяти|дванадцять|дванадцяти|тринадцять|тринадцяти|чотирнадцять|чотирнадцяти|п['’]?ятнадцять|п['’]?ятнадцяти|шістнадцять|шістнадцяти|сімнадцять|сімнадцяти|вісімнадцять|вісімнадцяти|дев['’]?ятнадцять|дев['’]?ятнадцяти|двадцять|двадцяти|тридцять|тридцяти|сорок|сорока|п['’]?ятдесят|п['’]?ятдесяти|шістдесят|шістдесяти|сімдесят|сімдесяти|вісімдесят|вісімдесяти|дев['’]?яносто|дев['’]?яноста|сто|ста|сот\\p{L}*|двісті|двохсот|триста|трьохсот|чотириста|чотирьохсот|п['’]?ятсот|п['’]?ятисот|шістсот|шестисот|сімсот|семисот|вісімсот|восьмисот|дев['’]?ятсот|дев['’]?ятисот|тисяч\\p{L}*|мільйон\\p{L}*|мільярд\\p{L}*|one|first|two|second|three|third|four|fourth|five|fifth|six|sixth|seven|seventh|eight|eighth|nine|ninth|ten|tenth|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred\\p{L}*|thousand\\p{L}*|million\\p{L}*|billion\\p{L}*|trillion\\p{L}*|ноль|нуль|один|одна|одно|одного|одной|одним|одними|перв\\p{L}*|два|две|двух|двум|двумя|втор\\p{L}*|три|трех|трёх|трем|трём|тремя|треть\\p{L}*|четыре|четырех|четырёх|четырем|четырём|четырьмя|четвер\\p{L}*|пять|пяти|пятер\\p{L}*|пяты\\p{L}*|шесть|шести|шестер\\p{L}*|шест\\p{L}*|семь|семи|семер\\p{L}*|седьм\\p{L}*|восемь|восьми|восьм\\p{L}*|девять|девяти|девят\\p{L}*|десять|десяти|десят\\p{L}*|одиннадцать|одиннадцати|двенадцать|двенадцати|тринадцать|тринадцати|четырнадцать|четырнадцати|пятнадцать|пятнадцати|шестнадцать|шестнадцати|семнадцать|семнадцати|восемнадцать|восемнадцати|девятнадцать|девятнадцати|двадцать|двадцати|тридцать|тридцати|сорок|сорока|пятьдесят|пятидесяти|шестьдесят|шестидесяти|семьдесят|семидесяти|восемьдесят|восьмидесяти|девяносто|девяноста|сто|ста|сот\\p{L}*|двести|двухсот|триста|трехсот|трёхсот|четыреста|четырехсот|четырёхсот|пятьсот|пятисот|шестьсот|шестисот|семьсот|семисот|восемьсот|восьмисот|девятьсот|девятисот|тысяч\\p{L}*|миллион\\p{L}*|миллиард\\p{L}*|триллион\\p{L}*)(?:[\\s-]+(?:один|одна|одне|одного|одній|одним|одними|перш\\p{L}*|два|дві|двох|двом|двома|друг\\p{L}*|три|трьох|трьом|трьома|трет\\p{L}*|чотири|чотирьох|чотирьом|чотирма|четвер\\p{L}*|п['’]?ять|п['’]?яти|п['’]?ятьох|п['’]?ятьма|п['’]?ят\\p{L}*|шість|шести|шістьох|шістьма|шост\\p{L}*|сім|семи|сімох|сьома|сьом\\p{L}*|вісім|восьми|вісьмох|вісьма|восьм\\p{L}*|дев['’]?ять|дев['’]?яти|дев['’]?ятьох|дев['’]?ятьма|дев['’]?ят\\p{L}*|десять|десяти|десятьох|десятьма|десят\\p{L}*|одинадцять|одинадцяти|дванадцять|дванадцяти|тринадцять|тринадцяти|чотирнадцять|чотирнадцяти|п['’]?ятнадцять|п['’]?ятнадцяти|шістнадцять|шістнадцяти|сімнадцять|сімнадцяти|вісімнадцять|вісімнадцяти|дев['’]?ятнадцять|дев['’]?ятнадцяти|двадцять|двадцяти|тридцять|тридцяти|сорок|сорока|п['’]?ятдесят|п['’]?ятдесяти|шістдесят|шістдесяти|сімдесят|сімдесяти|вісімдесят|вісімдесяти|дев['’]?яносто|дев['’]?яноста|сто|ста|сот\\p{L}*|двісті|двохсот|триста|трьохсот|чотириста|чотирьохсот|п['’]?ятсот|п['’]?ятисот|шістсот|шестисот|сімсот|семисот|вісімсот|восьмисот|дев['’]?ятсот|дев['’]?ятисот|тисяч\\p{L}*|мільйон\\p{L}*|мільярд\\p{L}*|one|first|two|second|three|third|four|fourth|five|fifth|six|sixth|seven|seventh|eight|eighth|nine|ninth|ten|tenth|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred\\p{L}*|thousand\\p{L}*|million\\p{L}*|billion\\p{L}*|trillion\\p{L}*|ноль|нуль|один|одна|одно|одного|одной|одним|одними|перв\\p{L}*|два|две|двух|двум|двумя|втор\\p{L}*|три|трех|трёх|трем|трём|тремя|треть\\p{L}*|четыре|четырех|четырёх|четырем|четырём|четырьмя|четвер\\p{L}*|пять|пяти|пятер\\p{L}*|пяты\\p{L}*|шесть|шести|шестер\\p{L}*|шест\\p{L}*|семь|семи|семер\\p{L}*|седьм\\p{L}*|восемь|восьми|восьм\\p{L}*|девять|девяти|девят\\p{L}*|десять|десяти|десят\\p{L}*|одиннадцать|одиннадцати|двенадцать|двенадцати|тринадцать|тринадцати|четырнадцать|четырнадцати|пятнадцать|пятнадцати|шестнадцать|шестнадцати|семнадцать|семнадцати|восемнадцать|восемнадцати|девятнадцать|девятнадцати|двадцать|двадцати|тридцать|тридцати|сорок|сорока|пятьдесят|пятидесяти|шестьдесят|шестидесяти|семьдесят|семидесяти|восемьдесят|восьмидесяти|девяносто|девяноста|сто|ста|сот\\p{L}*|двести|двухсот|триста|трехсот|трёхсот|четыреста|четырехсот|четырёхсот|пятьсот|пятисот|шестьсот|шестисот|семьсот|семисот|восемьсот|восьмисот|девятьсот|девятисот|тысяч\\p{L}*|миллион\\p{L}*|миллиард\\p{L}*|триллион\\p{L}*))*?(?!\\p{L})",
            RegexOption.IGNORE_CASE
        )
        private val CLICKBAIT_KEYWORD_REGEX = Regex(
            "(?<!\\p{L})(?:шок(?:уюч\\p{L}*)?|сенсац\\p{L}*|терміново|shock(?:ing)?|sensational|breaking)(?!\\p{L})",
            RegexOption.IGNORE_CASE
        )
    }
}
