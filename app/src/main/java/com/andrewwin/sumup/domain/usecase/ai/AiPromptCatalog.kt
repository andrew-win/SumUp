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
            goal = "Provide a direct, evidence-based answer to the user's question using only provided sources.",
            schema = """{"${AiJsonContract.ANSWER}":"string","${AiJsonContract.STATEMENTS}":[{"${AiJsonContract.TEXT}":"string","${AiJsonContract.SOURCES}":["source_id"]}],"${AiJsonContract.SOURCES}":["source_id"]}""",
            rules = listOf(
                "Use BLUF format (Bottom Line Up Front): direct answer first, supporting context second.",
                "Answer length: 2-4 definitive sentences.",
                "Statements constraint: 4..8 (inflated limit).",
                "Each statement must contain one specific fact linked to exactly one source_id.",
                "Extract root sources (unique source_ids) used in statements.",
                "If evidence is missing, state it directly and concisely (e.g., 'У джерелах немає підтвердження цієї інформації.').",
                "No speculation, no filler text, no interpretations.",
                RULE_JOURNALISTIC_STYLE,
                RULE_NO_INTRO_PHRASES
            ),
            formatInfo = "Question: 'Чи дали Україні гроші?'\nBAD Answer: 'Згідно з першими повідомленнями у статтях, можливо, Україна отримає значну допомогу...'\nGOOD Answer: 'Так, ЄС офіційно розблокував 90 млрд євро макрофінансової допомоги.'\n\nJSON Output:\n{\"answer\":\"Так, ЄС офіційно розблокував 90 млрд євро.\",\"statements\":[{\"text\":\"Рада ЄС погодила виділення фінансового пакета Україні.\",\"sources\":[\"2299\"]},{\"text\":\"Президент України підтвердив домовленість із керівництвом Євросоюзу.\",\"sources\":[\"962\"]}],\"sources\":[\"2299\",\"962\"]}",
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