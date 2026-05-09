package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.SummaryLanguage

object AiPromptBuilder {

    private const val RULE_JSON_ONLY =
        "Output ONLY one strictly valid JSON object matching the SCHEMA exactly. No markdown, no prose, no extra keys."

    private const val RULE_REUTERS_STYLE = "Use Reuters style: Objective, neutral, and punchy." +
            "Do not summarize by copying sentences from the input." +
            "Produce an analytical synthesis in your own words, while preserving exact facts, names, dates, numbers, locations, and source-attributed claims. " +
            "Short direct quotes are allowed only when the wording itself is important or evidentiary." +
            "Inverted pyramid: most important fact first, context second, background last." +
            "Active voice. Subject → verb → object. Avoid 'there is / there are' constructions." +
            "No filler ('it is worth noting', 'it should be mentioned', 'experts say')."

    private fun getLanguageRule(summaryLanguage: SummaryLanguage): String {
        return when (summaryLanguage) {
            SummaryLanguage.UK -> "Output language: ONLY Ukrainian."
            SummaryLanguage.EN -> "Output language: ONLY English."
        }
    }

    private fun createPrompt(
        role: String,
        goal: String,
        specificRules: List<String>,
        schema: String,
        question: String? = null,
        customInstructions: String? = null
    ): String {
        val allRules = mutableListOf<String>().apply {
            add(RULE_JSON_ONLY)
            add(RULE_REUTERS_STYLE)
            addAll(specificRules)
        }

        return buildString {
            append("ROLE\n")
            append(role)
            append("\n\nGOAL\n")
            append(goal)
            append("\n\nRULES\n")
            allRules.forEachIndexed { index, rule ->
                append("${index + 1}. $rule\n")
            }

            if (!customInstructions.isNullOrBlank()) {
                append("\nUSER SPECIFIC RULES\n")
                append("Apply these only if they do not conflict with RULES or SCHEMA:\n")
                append(customInstructions)
                append("\n")
            }

            if (question != null) {
                append("\nQUESTION\n")
                append(question)
                append("\n")
            }

            append("\nSCHEMA\n")
            append(schema)
        }.trimIndent()
    }

    fun buildSingleArticlePrompt(
        summaryLanguage: SummaryLanguage,
        customInstructions: String? = null
    ): String = createPrompt(
        role = "Senior wire-service correspondent.",
        goal = "Extract the critical hard facts into punchy bullets an editor can scan in 20 seconds.",
        specificRules = listOf(
            "Return exactly one item with up to ${SummaryLimits.Single.maxPoints} bullets.",
            "Each bullet: one sentence, max ${SummaryLimits.Single.maxWordsPerPoint} words. " +
                    "Lead with the most newsworthy element — a name, figure, or decision.",
            getLanguageRule(summaryLanguage)
        ),
        schema = """{"items":[{"bullets":["point 1","point 2"]}]}""",
        customInstructions = customInstructions
    )


    fun buildComparePrompt(
        summaryLanguage: SummaryLanguage,
        customInstructions: String? = null
    ): String {
        val fallback = when (summaryLanguage) {
            SummaryLanguage.UK -> "Не вдалося виявити унікальні фрагменти. Можливо новини перефразують одна одну, дуже схожі або надто короткі."
            SummaryLanguage.EN -> "No unique fragments were found. The news articles may be paraphrasing each other, very similar, or too short."
        }
        return createPrompt(
            role = "Senior news editor specialising in cross-source synthesis",
            goal = "Identify the unifying narrative and extract shared trends or facts across sources, while highlighting specific nuances.",
            specificRules = listOf(
                "COMMON_FACTS: Total max ${SummaryLimits.Compare.maxCommon} for ENTIRE ANSWER. Identify shared trends or 'umbrella' context (e.g., 'Illness is rising in multiple regions') even if specific details like cities or numbers differ.",
                "UNIQUE_DETAILS: Total max ${SummaryLimits.Compare.maxUnique} for ENTIRE ANSWER. Focus on specific nuances, figures, or locations that are unique to a single source.",
                "Each fact must be exactly one short sentence (max ${SummaryLimits.Compare.maxWordsPerPoint} words).",
                "Prioritize finding commonalities. Only use the 'no common traits' fallback (common_facts=[]) if sources are absolutely unrelated (e.g., sports vs cooking).",
                "Every unique fact must have exactly one source_id.",
                "Every common fact must have 2+ source_ids. Ensure the text of common facts is generalized enough to cover all linked sources.",
                "If a specific source has no unique details, return an empty array [] for its unique_details field.",
                "Use single item with fallback text ('$fallback') if NO unique details were found across ALL sources combined. But avoid in most cases.",
                getLanguageRule(summaryLanguage)
            ),
            schema = """{"common_topic":"optional short topic label","common_facts":[{"text":"sentence 1","source_ids":["source_id_1","source_id_2"]}],"items":[{"source_id":"source id from input","unique_details":["sentence 1"]}]}""",
            customInstructions = customInstructions
        )
    }

    fun buildFeedDigestPrompt(
        summaryLanguage: SummaryLanguage,
        customInstructions: String? = null
    ): String {
        return createPrompt(
            role = "News desk editor composing a flagship daily digest.",
            goal = "Build a condensed, hard-fact digest grouped by broad categories.",
            specificRules = listOf(
                "Create up to ${SummaryLimits.Digest.maxThemes} broad themes (e.g., '\uD83D\uDCB0\uD83D\uDCCA\uD83C\uDFE6 Економіка', '\uD83E\uDD16\uD83E\uDDE0⚙\uFE0F Штучний інтелект', '\uD83C\uDDFA\uD83C\uDDE6\uD83E\uDE96\uD83D\uDCA5 Війна в Україні').",
                "Never combine topics that are fundamentally different. Bad - 'Technology and Health', good - 'Health and Sports'.",
                "Include ${SummaryLimits.Digest.emojiesCount} highly relevant emojis in theme title.",
                "Each theme must have ${SummaryLimits.Digest.minItemsPerTheme} to ${SummaryLimits.Digest.maxItemsPerTheme} short abstractive news items (titles).",
                "Item title: punchy, short (max ${SummaryLimits.Digest.maxWordsPerTitle} words).",
                "Each news must have exactly one source_id.",
                "Input news rows use this format: id|src|url|title|content. Use the id column exactly as source_id.",
                getLanguageRule(summaryLanguage)
            ),
            schema = """{"themes":[{"title":"Emoji Theme Title","items":[{"title":"news title","source_id":"id"}]}]}""",
            customInstructions = customInstructions
        )
    }

    fun buildQuestionPrompt(
        summaryLanguage: SummaryLanguage,
        question: String,
        customInstructions: String? = null
    ): String {
        val fallback = when (summaryLanguage) {
            SummaryLanguage.UK -> "За даними поданих джерел не можна дати чітку відповідь на ваше питання"
            SummaryLanguage.EN -> "The provided sources do not contain enough information to answer your question"
        }

        return createPrompt(
            role = "Helpful analyst and fact-checker.",
            goal = "Provide the most relevant information found in the sources regarding the question.",
            specificRules = listOf(
                "short_answer: one natural sentence (max ${SummaryLimits.QA.maxWordsShortAnswer} words) answering the question maximally shortly.",
                "details: maximum ${SummaryLimits.QA.maxDetailPoints} short factual bullets. Each up to ${SummaryLimits.QA.maxWordsPerDetailedBullet} words and must include 1+ valid source_ids.",
                "If the sources are ON TOPIC but don't have a direct answer (e.g., missing exact numbers), set short_answer to: 'У джерелах немає прямої відповіді на це питання.' and provide relevant context in 'details'.",
                "If the sources are COMPLETELY UNRELATED to the question (e.g., question is about sports, but sources are about war), set short_answer to '$fallback'.",
                "In case of completely unrelated sources, use 'details' to explain the mismatch (e.g., 'Новини стосуються різних тем: одна про війну, інша про футбол').",
                getLanguageRule(summaryLanguage)
            ),
            question = question,
            schema = """{"short_answer":"string","details":[{"text":"string","sources":["source_id"]}]}""",
            customInstructions = customInstructions
        )
    }
}
