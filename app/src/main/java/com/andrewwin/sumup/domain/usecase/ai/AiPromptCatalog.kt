package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.SummaryLanguage

enum class AiSummaryPromptProfile {
    DIGEST_ANALYTICAL,
    SINGLE_ARTICLE_ANALYTICAL
}

object AiPromptCatalog {

    private const val RULE_NO_INTRO_PHRASES = "Omit filler, announcements, and intro phrases (e.g., 'Перші повідомлення', 'Дані про', 'Стало відомо', 'У статті йдеться', 'Зазначається'). Start directly with the core fact."
    private const val RULE_JOURNALISTIC_STYLE = "Strict AP/Reuters journalistic style: use active voice and strong action verbs. Strip away all bureaucratic fluff, adverbs of degree (e.g., 'значно', 'надзвичайно'), modal verbs ('може', 'мабуть'), and emotional modifiers. Focus strictly on 'Who did what'."

    private fun promptEnvelope(role: String, goal: String, schema: String, rules: List<String>, formatInfo: String? = null, body: String? = null): String {
        return buildString {
            appendLine("ROLE")
            appendLine(role)
            appendLine()
            appendLine("GOAL")
            appendLine(goal)
            appendLine()
            appendLine("OUTPUT CONTRACT (MANDATORY)")
            appendLine("1. Return valid JSON object only.")
            appendLine("2. No prose before/after JSON.")
            appendLine("3. No markdown, no code fences, no comments.")
            appendLine("4. Use only keys from schema.")
            appendLine("5. If uncertain about a fact or source binding, omit the claim (do not invent).")
            appendLine()
            appendLine("RULES")
            rules.forEachIndexed { index, rule -> appendLine("${index + 1}. $rule") }
            appendLine()
            appendLine("SCHEMA")
            appendLine(schema)
            if (!formatInfo.isNullOrBlank()) {
                appendLine()
                appendLine("FORMAT EXAMPLES")
                appendLine(formatInfo)
            }
            if (!body.isNullOrBlank()) {
                appendLine()
                appendLine("INPUT")
                appendLine(body.trim())
            }
        }.trim()
    }

    fun buildSummaryPrompt(
        basePrompt: String,
        summaryLanguage: SummaryLanguage,
        profile: AiSummaryPromptProfile
    ): String {
        val languageRule = languageRule(summaryLanguage)

        return when (profile) {
            AiSummaryPromptProfile.DIGEST_ANALYTICAL -> {
                promptEnvelope(
                    role = "Strict news editor (Reuters/AP style).",
                    goal = "Build a highly condensed, hard-fact digest grouped by broad categories.",
                    schema = """{"${AiJsonContract.HEADLINE}":"optional short digest title","${AiJsonContract.THEMES}":[{"${AiJsonContract.TITLE}":"theme title","${AiJsonContract.EMOJIS}":["emoji1","emoji2"],"${AiJsonContract.ITEMS}":[{"${AiJsonContract.TITLE}":"news title","${AiJsonContract.SOURCE_ID}":"source id from input"}]}]}""",
                    rules = listOf(
                        "One item = one concrete event fact from exactly one source_id.",
                        "Themes constraint: 1..4. Items per theme: 2..4 (inflated limits to ensure enough output).",
                        "Group items into broad thematic categories (e.g., 'Міжнародна політика', 'Економіка', 'Фронт').",
                        "Theme title: 2-4 words max. Must be a broad category, not a specific event description.",
                        "Include 2..4 highly relevant emojis per theme.",
                        "Item title: punchy, action-oriented news ticker style (max 20-25 words). Lead with the main actor and action.",
                        "Each source_id can appear only once in the entire response.",
                        RULE_JOURNALISTIC_STYLE,
                        RULE_NO_INTRO_PHRASES,
                        languageRule
                    ),
                    formatInfo = "GOOD Structure Example:\n{\"headline\":\"Головне за день\",\"themes\":[{\"title\":\"Міжнародна підтримка\",\"emojis\":[\"🌍\",\"🤝\"],\"items\":[{\"title\":\"ЄС розблокував 90 млрд євро макрофінансової допомоги для України\",\"source_id\":\"2299\"},{\"title\":\"Іспанія передасть ракети для систем Patriot у межах механізму PURL\",\"source_id\":\"2300\"}]},{\"title\":\"Економіка\",\"emojis\":[\"📈\",\"💰\"],\"items\":[{\"title\":\"Нацбанк знизив облікову ставку до 13%\",\"source_id\":\"2301\"}]}]}",
                    body = basePrompt
                )
            }
            AiSummaryPromptProfile.SINGLE_ARTICLE_ANALYTICAL -> {
                promptEnvelope(
                    role = "Strict single-article news analyst.",
                    goal = "Extract the most critical 'hard facts' into punchy bullet points.",
                    schema = """{"${AiJsonContract.ITEMS}":[{"${AiJsonContract.TITLE}":"","${AiJsonContract.BULLETS}":["point 1","point 2"],"${AiJsonContract.SOURCE}":"optional source name"}]}""",
                    rules = listOf(
                        "Return exactly one item object. Force item title to be an empty string: \"\".",
                        "One bullet = one standalone hard fact (focus on Who, What, When, Where, Numbers).",
                        "Bullets constraint: 4..10 (inflated limit to ensure you generate enough details).",
                        "Sentence length: strictly compact one-liner (max 30-40 words).",
                        "Anchor every bullet with a concrete entity. Avoid vague summaries.",
                        RULE_JOURNALISTIC_STYLE,
                        RULE_NO_INTRO_PHRASES,
                        languageRule
                    ),
                    formatInfo = "BAD Example (Fluff & Passive): [\"Було прийнято важливе рішення щодо виділення коштів\", \"Зеленський дуже позитивно оцінив цей значний крок\"]\n\nGOOD Example (AP Style): [\"ЄС схвалив пакет фінансової допомоги Україні на 90 млрд євро.\", \"Уряд спрямує кошти на підтримку макроекономічної стабільності у 2024 році.\", \"Президент Зеленський підписав відповідну угоду з керівництвом Єврокомісії.\"]",
                    body = basePrompt
                )
            }
        }
    }

    fun buildQuestionPrompt(
        question: String,
        questionPrefix: String,
        questionSuffix: String
    ): String {
        return promptEnvelope(
            role = "Strict QA fact-checker.",
            goal = "Return a strict 3-block answer: question, very short direct answer, then evidence-backed details from sources.",
            schema = """{"${AiJsonContract.QUESTION}":"string","${AiJsonContract.SHORT_ANSWER}":"string","${AiJsonContract.DETAILS}":[{"${AiJsonContract.TEXT}":"string","${AiJsonContract.SOURCES}":["source_id"]}],"${AiJsonContract.SOURCES}":["source_id"]}""",
            rules = listOf(
                "Return JSON only and follow schema exactly.",
                "question: repeat user question concisely in one line (do not add new claims).",
                "short_answer: one natural concise sentence (about 8-18 words) with a direct factual answer. Not just 'Так/Ні'.",
                "details: 2..5 short factual bullets, each with concrete evidence.",
                "Each details item must include 1..3 valid source_id values from input.",
                "If a claim has no evidence in sources, do not include it.",
                "sources: unique union of source_id values used in details.",
                "If evidence is missing, state it directly and concisely (e.g., 'У джерелах немає підтвердження цієї інформації.').",
                "No speculation, no filler text, no interpretations.",
                RULE_JOURNALISTIC_STYLE,
                RULE_NO_INTRO_PHRASES
            ),
            formatInfo = "Question: 'Які числа згадуються у новинах?'\nBAD short_answer: 'Так, згадуються числа.'\nGOOD short_answer: 'У новинах згадані 3.7 млн грн, 2 млн грн та 3 постраждалих.'\n\nJSON Output:\n{\"question\":\"Які числа згадуються у новинах?\",\"short_answer\":\"У новинах згадані 3.7 млн грн, 2 млн грн та 3 постраждалих.\",\"details\":[{\"text\":\"У першому джерелі йдеться про збитки на 3.7 млн грн.\",\"sources\":[\"2299\"]},{\"text\":\"Інше джерело повідомляє про 2 млн грн фінансування та 3 постраждалих.\",\"sources\":[\"962\",\"963\"]}],\"sources\":[\"2299\",\"962\",\"963\"]}",
            body = """
                $questionPrefix $question

                $questionSuffix
            """.trimIndent()
        )
    }

    fun buildComparePrompt(
        summaryLanguage: SummaryLanguage
    ): String {
        return promptEnvelope(
            role = "Strict comparative news analyst.",
            goal = "Distill multiple sources into a crisp matrix of overlapping facts and unique details.",
            schema = """{"${AiJsonContract.COMMON_TOPIC}":"optional short topic label","${AiJsonContract.COMMON_FACTS}":[{"${AiJsonContract.TEXT}":"sentence 1","${AiJsonContract.SOURCES}":["source_id_1","source_id_2"]}],"${AiJsonContract.ITEMS}":[{"${AiJsonContract.SOURCE_ID}":"source id from input","${AiJsonContract.UNIQUE_DETAILS}":["sentence 1"]}]}""",
            rules = listOf(
                "Format: Rewrite all facts into ultra-short, abstractive one-liners (max 30-35 words to ensure completeness).",
                "No direct quotes. Paraphrase direct speech into concise statements of fact.",
                "COMMON_FACTS requirement: A fact is common ONLY if the exact same event/number/claim appears in 2+ sources.",
                "IF source B only clarifies source A (adds detail/context), DO NOT create a common fact. Put the new fragment in source B's UNIQUE_DETAILS.",
                "IF sources share a broad topic but lack concrete overlapping facts: set common_facts=[] and provide a short common_topic label.",
                "IF sources are completely unrelated: set common_facts=[] and common_topic=\"\".",
                "common_facts constraint: max 8 (if only 2 sources exist, max 4).",
                "UNIQUE_DETAILS constraint: max 8 per source_id. Focus only on exclusive, high-value facts.",
                "Each common_facts item MUST include 2+ exact source_ids.",
                "Do not include source names/channels inside the fact text.",
                "Reject generic overlaps without concrete anchors (e.g., skip 'Сторони обговорили співпрацю').",
                RULE_JOURNALISTIC_STYLE,
                RULE_NO_INTRO_PHRASES,
                languageRule(summaryLanguage)
            ),
            formatInfo = "Scenario A (True overlap):\n{\"common_topic\":\"\",\"common_facts\":[{\"text\":\"ЄС погодив виділення Україні 90 млрд євро\",\"sources\":[\"s1\",\"s2\"]}],\"items\":[{\"source_id\":\"s1\",\"unique_details\":[\"Пакет включає новий блок санкцій проти РФ\"]},{\"source_id\":\"s2\",\"unique_details\":[\"Уряд спрямує кошти на соціальні виплати протягом дворічного періоду\"]}]}\n\nScenario B (Broad topic, NO shared fact):\n{\"common_topic\":\"Озброєння та атаки\",\"common_facts\":[],\"items\":[{\"source_id\":\"s1\",\"unique_details\":[\"ЗСУ представили нову балістичну ракету власного виробництва\"]},{\"source_id\":\"s2\",\"unique_details\":[\"Іран здійснив масовану атаку дронами-камікадзе\"]}]}"
        )
    }

    private fun languageRule(summaryLanguage: SummaryLanguage): String =
        when (summaryLanguage) {
            SummaryLanguage.UK -> "Output language requirement: Ukrainian only. Use correct Ukrainian journalistic terminology."
            SummaryLanguage.EN -> "Output language requirement: English only. Use correct English journalistic terminology."
        }
}
