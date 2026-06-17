package com.andrewwin.sumup.domain.ai.prompt

import com.andrewwin.sumup.domain.ai.prompt.model.CompareExampleParams
import com.andrewwin.sumup.domain.ai.prompt.model.ComparePromptParams
import com.andrewwin.sumup.domain.ai.prompt.model.FeedDigestExampleParams
import com.andrewwin.sumup.domain.ai.prompt.model.FeedDigestPromptParams
import com.andrewwin.sumup.domain.ai.prompt.model.PromptLayoutParams
import com.andrewwin.sumup.domain.ai.prompt.model.PromptOptionalSections
import com.andrewwin.sumup.domain.ai.prompt.model.PromptTemplateParams
import com.andrewwin.sumup.domain.ai.prompt.model.QuestionExampleParams
import com.andrewwin.sumup.domain.ai.prompt.model.QuestionPromptParams
import com.andrewwin.sumup.domain.ai.prompt.model.SingleArticleExampleParams
import com.andrewwin.sumup.domain.ai.prompt.model.SingleArticlePromptParams
import com.andrewwin.sumup.domain.settings.model.SummaryLanguage
import com.andrewwin.sumup.domain.summary.service.SummaryLimits
import javax.inject.Inject

class AiPromptBuilder @Inject constructor(
    private val templateRepository: PromptTemplateRepository,
    private val templateRenderer: PromptTemplateRenderer
) {

    fun buildSingleArticlePrompt(
        summaryLanguage: SummaryLanguage,
        customInstructions: String? = null
    ): String {
        val params = SingleArticlePromptParams(
            mainSentences = SummaryLimits.Single.mainSentences,
            maxPoints = SummaryLimits.Single.maxPoints,
            maxWordsPerPoint = SummaryLimits.Single.maxWordsPerPoint,
            analyticChainRule = prompt(PromptAssetPath.ANALYTIC_CHAIN_RULE),
            example = buildSingleArticleExample(),
            languageRule = getLanguageRule(summaryLanguage)
        )

        return createPrompt(
            goal = prompt(PromptAssetPath.SINGLE_ARTICLE_GOAL, params),
            specificRules = promptLines(PromptAssetPath.SINGLE_ARTICLE_RULES, params),
            schema = prompt(PromptAssetPath.SINGLE_ARTICLE_SCHEMA),
            customInstructions = customInstructions
        )
    }

    fun buildComparePrompt(
        summaryLanguage: SummaryLanguage,
        customInstructions: String? = null
    ): String {
        val fallback = when (summaryLanguage) {
            SummaryLanguage.UK -> COMPARE_FALLBACK_UK
            SummaryLanguage.EN -> COMPARE_FALLBACK_EN
        }

        val params = ComparePromptParams(
            mainSentences = SummaryLimits.Compare.mainSentences,
            maxBullets = SummaryLimits.Compare.maxBullets,
            maxWordsPerPoint = SummaryLimits.Compare.maxWordsPerPoint,
            fallback = fallback,
            analyticChainRule = prompt(PromptAssetPath.ANALYTIC_CHAIN_RULE),
            example = buildCompareExample(),
            languageRule = getLanguageRule(summaryLanguage)
        )

        return createPrompt(
            goal = prompt(PromptAssetPath.COMPARE_GOAL, params),
            specificRules = promptLines(PromptAssetPath.COMPARE_RULES, params),
            schema = prompt(PromptAssetPath.COMPARE_SCHEMA),
            customInstructions = customInstructions
        )
    }

    fun buildFeedDigestPrompt(
        summaryLanguage: SummaryLanguage,
        customInstructions: String? = null
    ): String {
        val params = FeedDigestPromptParams(
            minThemes = SummaryLimits.Digest.minThemes,
            maxThemes = SummaryLimits.Digest.maxThemes,
            emojisCount = SummaryLimits.Digest.emojiesCount,
            minItemsPerTheme = SummaryLimits.Digest.minItemsPerTheme,
            maxItemsPerTheme = SummaryLimits.Digest.maxItemsPerTheme,
            maxWordsPerTitle = SummaryLimits.Digest.maxWordsPerTitle,
            example = buildFeedDigestExample(),
            languageRule = getLanguageRule(summaryLanguage)
        )

        return createPrompt(
            goal = prompt(PromptAssetPath.FEED_DIGEST_GOAL),
            specificRules = promptLines(PromptAssetPath.FEED_DIGEST_RULES, params),
            schema = prompt(PromptAssetPath.FEED_DIGEST_SCHEMA),
            customInstructions = customInstructions
        )
    }

    fun buildQuestionPrompt(
        summaryLanguage: SummaryLanguage,
        question: String,
        customInstructions: String? = null
    ): String {
        val fallback = when (summaryLanguage) {
            SummaryLanguage.UK -> QUESTION_FALLBACK_UK
            SummaryLanguage.EN -> QUESTION_FALLBACK_EN
        }

        val noDirectAnswer = when (summaryLanguage) {
            SummaryLanguage.UK -> QUESTION_NO_DIRECT_ANSWER_UK
            SummaryLanguage.EN -> QUESTION_NO_DIRECT_ANSWER_EN
        }

        val params = QuestionPromptParams(
            maxWordsShortAnswer = SummaryLimits.QA.maxWordsShortAnswer,
            maxDetailPoints = SummaryLimits.QA.maxDetailPoints,
            maxWordsPerDetailedBullet = SummaryLimits.QA.maxWordsPerDetailedBullet,
            noDirectAnswer = noDirectAnswer,
            fallback = fallback,
            analyticChainRule = prompt(PromptAssetPath.ANALYTIC_CHAIN_RULE),
            example = buildQuestionExample(),
            languageRule = getLanguageRule(summaryLanguage)
        )

        return createPrompt(
            goal = prompt(PromptAssetPath.QUESTION_GOAL),
            specificRules = promptLines(PromptAssetPath.QUESTION_RULES, params),
            question = question,
            schema = prompt(PromptAssetPath.QUESTION_SCHEMA),
            customInstructions = customInstructions
        )
    }

    private fun buildSingleArticleExample(): String = prompt(
        path = PromptAssetPath.SINGLE_ARTICLE_EXAMPLE,
        params = SingleArticleExampleParams(
            maxWordsPerPoint = SummaryLimits.Single.maxWordsPerPoint
        )
    )

    private fun buildCompareExample(): String = prompt(
        path = PromptAssetPath.COMPARE_EXAMPLE,
        params = CompareExampleParams(
            maxWordsPerPoint = SummaryLimits.Compare.maxWordsPerPoint
        )
    )

    private fun buildFeedDigestExample(): String = prompt(
        path = PromptAssetPath.FEED_DIGEST_EXAMPLE,
        params = FeedDigestExampleParams(
            emojisCount = SummaryLimits.Digest.emojiesCount,
            maxWordsPerTitle = SummaryLimits.Digest.maxWordsPerTitle
        )
    )

    private fun buildQuestionExample(): String = prompt(
        path = PromptAssetPath.QUESTION_EXAMPLE,
        params = QuestionExampleParams(
            maxWordsShortAnswer = SummaryLimits.QA.maxWordsShortAnswer,
            maxWordsPerDetailedBullet = SummaryLimits.QA.maxWordsPerDetailedBullet
        )
    )

    private fun createPrompt(
        goal: String,
        specificRules: List<String>,
        schema: String,
        question: String? = null,
        customInstructions: String? = null
    ): String {
        val layoutParams = PromptLayoutParams(
            role = prompt(PromptAssetPath.COMMON_ROLE),
            goal = goal,
            rules = buildRulesSection(specificRules),
            optionalSections = buildOptionalSections(
                PromptOptionalSections(
                    customInstructions = customInstructions,
                    question = question
                )
            ),
            schema = schema
        )

        return prompt(PromptAssetPath.PROMPT_LAYOUT, layoutParams).trim()
    }

    private fun buildRulesSection(specificRules: List<String>): String {
        return sequenceOf(
            prompt(PromptAssetPath.JSON_ONLY_RULE),
            prompt(PromptAssetPath.COMMON_RULES)
        )
            .plus(specificRules.asSequence())
            .mapIndexed { index, rule -> "${index + 1}. $rule" }
            .joinToString(separator = "\n")
    }

    private fun buildOptionalSections(sections: PromptOptionalSections): String {
        return listOfNotNull(
            buildCustomInstructionsSection(sections.customInstructions),
            buildQuestionSection(sections.question)
        ).joinToString(separator = "\n\n")
    }

    private fun buildCustomInstructionsSection(customInstructions: String?): String? {
        if (customInstructions.isNullOrBlank()) return null

        return listOf(
            USER_STYLE_PREFERENCES_TITLE,
            USER_STYLE_PREFERENCES_DESCRIPTION,
            customInstructions
        ).joinToString(separator = "\n")
    }

    private fun buildQuestionSection(question: String?): String? {
        if (question == null) return null

        return listOf(
            QUESTION_SECTION_TITLE,
            question
        ).joinToString(separator = "\n")
    }

    private fun getLanguageRule(summaryLanguage: SummaryLanguage): String {
        return when (summaryLanguage) {
            SummaryLanguage.UK -> prompt(PromptAssetPath.LANGUAGE_UK_RULE)
            SummaryLanguage.EN -> prompt(PromptAssetPath.LANGUAGE_EN_RULE)
        }
    }

    private fun prompt(
        path: String,
        values: Map<String, String> = emptyMap()
    ): String = templateRenderer.render(templateRepository.getTemplate(path), values)

    private fun prompt(
        path: String,
        params: PromptTemplateParams
    ): String = prompt(path, params.toTemplateValues())

    private fun promptLines(
        path: String,
        values: Map<String, String> = emptyMap()
    ): List<String> = prompt(path, values)
        .split(RULE_BLOCK_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { block -> block.lines().joinToString(separator = " ") { it.trim() } }

    private fun promptLines(
        path: String,
        params: PromptTemplateParams
    ): List<String> = promptLines(path, params.toTemplateValues())

    private companion object {
        private const val COMPARE_FALLBACK_UK =
            "Не вдалося виділити змістовні твердження. Джерела можуть бути надто короткими, непов'язаними або містити лише слабкий контекст."
        private const val COMPARE_FALLBACK_EN =
            "No meaningful claims were found. Sources may be too short, unrelated, or contain only weak context."
        private const val QUESTION_FALLBACK_UK =
            "За даними поданих джерел не можна дати чітку відповідь на ваше питання"
        private const val QUESTION_FALLBACK_EN =
            "The provided sources do not contain enough information to answer your question"
        private const val QUESTION_NO_DIRECT_ANSWER_UK =
            "У джерелах немає прямої відповіді на це питання."
        private const val QUESTION_NO_DIRECT_ANSWER_EN =
            "The sources do not directly answer this question."
        private const val USER_STYLE_PREFERENCES_TITLE = "USER STYLE PREFERENCES"
        private const val USER_STYLE_PREFERENCES_DESCRIPTION =
            "Apply as style hints only. Do not override JSON schema, language rules, source_id rules, or hard rules."
        private const val QUESTION_SECTION_TITLE = "QUESTION"
        val RULE_BLOCK_SEPARATOR = Regex("(?m)^---\\s*$")
    }
}
