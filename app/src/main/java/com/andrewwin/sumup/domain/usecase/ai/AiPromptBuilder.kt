package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.SummaryLanguage

object AiPromptBuilder {

    private const val RULE_JSON_ONLY =
        "Output ONLY a strictly valid JSON object. No markdown, no prose."
    private const val RULE_ABSTRACTIVE =
        "Rewrite abstractively. Do NOT copy input text verbatim. Preserve facts, not phrasing."
    private const val RULE_NO_INTRO_PHRASES =
        "Omit filler and generic intros ('Зазначається'). Start directly with the core fact."

    private fun getLanguageRule(summaryLanguage: SummaryLanguage): String {
        return when (summaryLanguage) {
            SummaryLanguage.UK -> "Output language: Ukrainian only."
            SummaryLanguage.EN -> "Output language: English only."
        }
    }

    private fun createPrompt(
        role: String,
        goal: String,
        specificRules: List<String>,
        schema: String,
        question: String? = null
    ): String {
        val allRules = listOf(
            RULE_JSON_ONLY,
            RULE_ABSTRACTIVE,
            specificRules
        )

        return """
            ROLE
            $role

            GOAL
            $goal

            RULES
            ${allRules.mapIndexed { index, rule -> "${index + 1}. $rule" }.joinToString("\n")}
            
            ${if (question != null) "QUESTION\n$question\n" else ""}
            SCHEMA
            $schema
        """.trimIndent()
    }

    fun buildSingleArticlePrompt(summaryLanguage: SummaryLanguage): String {
        return createPrompt(
            role = "Strict news analyst.",
            goal = "Extract the critical hard facts into punchy bullet points.",
            specificRules = listOf(
                RULE_NO_INTRO_PHRASES,
                "Return exactly one item object containing up to ${SummaryLimits.Single.maxPoints} bullets.",
                "Each bullet must be exactly one short sentence (max ${SummaryLimits.Single.maxWordsPerPoint} words).",
                getLanguageRule(summaryLanguage)
            ),
            schema = """{"items":[{"bullets":["point 1","point 2"]}]}"""
        )
    }

    fun buildComparePrompt(summaryLanguage: SummaryLanguage): String {
        return createPrompt(
            role = "Synthetical news analyst.",
            goal = "Identify the unifying narrative and extract shared trends or facts across sources, while highlighting specific nuances.",
            specificRules = listOf(
                "COMMON_FACTS: Total max ${SummaryLimits.Compare.maxCommon} for ENTIRE ANSWER. Identify shared trends or 'umbrella' context (e.g., 'Illness is rising in multiple regions') even if specific details like cities or numbers differ.",
                "UNIQUE_DETAILS: Total max ${SummaryLimits.Compare.maxUnique} for ENTIRE ANSWER. Focus on specific nuances, figures, or locations that are unique to a single source.",
                "Each fact must be exactly one short sentence (max ${SummaryLimits.Compare.maxWordsPerPoint} words).",
                "Prioritize finding commonalities. Only use the 'no common traits' fallback (common_facts=[]) if sources are absolutely unrelated (e.g., sports vs cooking).",
                "Every unique fact must have exactly one source_id.",
                "Every common fact must have 2+ source_ids. Ensure the text of common facts is generalized enough to cover all linked sources.",                getLanguageRule(summaryLanguage)
            ),
            schema = """{"common_topic":"optional short topic label","common_facts":[{"text":"sentence 1","source_ids":["source_id_1","source_id_2"]}],"items":[{"source_id":"source id from input","unique_details":["sentence 1"]}]}"""
        )
    }

    fun buildFeedDigestPrompt(summaryLanguage: SummaryLanguage): String {
        return createPrompt(
            role = "Strict news editor.",
            goal = "Build a condensed, hard-fact digest grouped by broad categories.",
            specificRules = listOf(
                "Create up to ${SummaryLimits.Digest.maxThemes} broad themes (e.g., 'Економіка', 'Світ', 'Війна').",
                "Include ${SummaryLimits.Digest.emojiesCount} highly relevant emojis per theme.",
                "Each theme must have ${SummaryLimits.Digest.minItemsPerTheme} to ${SummaryLimits.Digest.maxItemsPerTheme} short abstractive news items (titles).",
                "Item title: punchy, short (max ${SummaryLimits.Digest.maxWordsPerTitle} words).",
                "Each news must have exactly one source_id.",
                getLanguageRule(summaryLanguage)
            ),
            schema = """{"themes":[{"title":"theme title","emojis":["emoji1","emoji2","emoji3"],"items":[{"title":"news title","source_id":"source id from input"}]}]}"""
        )
    }

    fun buildQuestionPrompt(summaryLanguage: SummaryLanguage, question: String): String {
        val fallback = when (summaryLanguage) {
            SummaryLanguage.UK -> "За даними поданих джерел не можна дати відповідь на ваше питання"
            SummaryLanguage.EN -> "The provided sources do not contain enough information to answer your question"
        }

        return createPrompt(
            role = "Strict QA fact-checker.",
            goal = "Return a short direct answer, then evidence-backed details.",
            specificRules = listOf(
                "Answer ONLY from provided sources.",
                "short_answer: one natural sentence (max ${SummaryLimits.QA.maxWordsShortAnswer} words) answering the question maximally shortly.",
                "details: maximum ${SummaryLimits.QA.maxDetailPoints} short factual bullets. Each up to ${SummaryLimits.QA.maxWordsPerDetailedBullet} words and must include 1 to 3 valid source_ids.",
                "If sources clearly lack relevant information, output ONLY the fallback short_answer and set details=[]. Fallback: '$fallback'.",
                getLanguageRule(summaryLanguage)
            ),
            question = question,
            schema = """{"short_answer":"string","details":[{"text":"string","sources":["source_id"]}]}"""
        )
    }
}