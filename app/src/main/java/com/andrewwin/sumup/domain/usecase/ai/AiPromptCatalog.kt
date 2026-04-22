package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.SummaryLanguage

enum class AiSummaryPromptProfile {
    DIGEST_ANALYTICAL,
    SINGLE_ARTICLE_ANALYTICAL
}

object AiPromptCatalog {

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
            appendLine("5. If uncertain, omit claim (do not invent).")
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
                    role = "Strict news fact-checker and digest editor.",
                    goal = "Build a short factual digest from the provided source blocks.",
                    schema = """{"${AiJsonContract.HEADLINE}":"optional short digest title","${AiJsonContract.THEMES}":[{"${AiJsonContract.TITLE}":"theme title","${AiJsonContract.EMOJIS}":["emoji1","emoji2"],"${AiJsonContract.ITEMS}":[{"${AiJsonContract.TITLE}":"news title","${AiJsonContract.SOURCE_ID}":"source id from input"}]}]}""",
                    rules = listOf(
                        "One item = one event fact from one source_id. Good: 'ЄС виділив 600 млн євро'. Bad: 'ЄС допомагає та обговорює багато питань'.",
                        "Use only explicit facts from input. Unknown fact -> omit.",
                        "Themes: 0..4. Items per theme: 1..3.",
                        "Theme title: 3-4 words only. Good: 'Допомога Україні від ЄС'. Bad: 'Ситуація на Близькому Сході та роль США'.",
                        "Each theme must have 2..4 relevant emojis.",
                        "Each item must contain: short abstractive title + one exact source_id from input.",
                        "Each source_id can appear only once in whole response.",
                        "No clickbait style. Rewrite to neutral factual language. Good: 'США готують нові санкції'. Bad: 'ШОК! Терміново!'.",
                        "No announcement phrases. Banned starters: 'Перші повідомлення', 'Дані про', 'Стало відомо', 'У статті йдеться'.",
                        "Group only by concrete shared event/actor/sector. Broad topic similarity is not enough.",
                        "If source_id binding is uncertain -> omit item.",
                        languageRule
                    ),
                    formatInfo = "Example output:\n{\"headline\":\"\",\"themes\":[{\"title\":\"Допомога Україні від ЄС\",\"emojis\":[\"🇪🇺\",\"💶\"],\"items\":[{\"title\":\"ЄС розблокував 90 млрд євро підтримки\",\"source_id\":\"2299\"},{\"title\":\"ЄІБ виділяє кошти на інфраструктуру\",\"source_id\":\"2300\"}]}]}",
                    body = basePrompt
                )
            }
            AiSummaryPromptProfile.SINGLE_ARTICLE_ANALYTICAL -> {
                promptEnvelope(
                    role = "Strict single-article fact-checker.",
                    goal = "Return short factual bullets for one article.",
                    schema = """{"${AiJsonContract.ITEMS}":[{"${AiJsonContract.TITLE}":"","${AiJsonContract.BULLETS}":["point 1","point 2"],"${AiJsonContract.SOURCE}":"optional source name"}]}""",
                    rules = listOf(
                        "One bullet = one fact. Good: one actor + one action. Bad: mixed 2-3 events.",
                        "Use only explicit facts from input.",
                        "Return exactly one item object and keep item title = empty string.",
                        "Bullets count: 2..5.",
                        "Each bullet must include concrete anchor (actor/number/date/place/decision/claim).",
                        "Sentence length target: compact one-liner.",
                        "No announcement phrases. Banned: 'У статті йдеться', 'Стало відомо', 'Перші повідомлення'.",
                        "No emotional/clickbait wording.",
                        languageRule
                    ),
                    formatInfo = "Example output:\n{\"items\":[{\"title\":\"\",\"bullets\":[\"ЄС розблокував пакет допомоги Україні на 90 млрд євро.\",\"Зеленський назвав рішення сигналом для РФ завершити війну.\"],\"source\":\"\"}]}",
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
            goal = "Answer user question using only provided sources with source-linked statements.",
            schema = """{"${AiJsonContract.ANSWER}":"string","${AiJsonContract.STATEMENTS}":[{"${AiJsonContract.TEXT}":"string","${AiJsonContract.SOURCES}":["source_id"]}],"${AiJsonContract.SOURCES}":["source_id"]}""",
            rules = listOf(
                "Use only explicit facts from provided sources.",
                "Answer length: 1-2 short sentences.",
                "Statements count: 2..4.",
                "Each statement must be one fact + exactly one source_id.",
                "Root sources = unique source_ids used in statements.",
                "No speculation and no hidden-motive interpretations.",
                "If evidence is missing, say it directly. Good: 'У джерелах немає підтвердження X.'",
                "No announcement phrases.",
                "No hallucination."
            ),
            formatInfo = "Example output:\n{\"answer\":\"У джерелах підтверджено розблокування 90 млрд євро.\",\"statements\":[{\"text\":\"ЄС погодив пакет фінансової підтримки Україні.\",\"sources\":[\"2299\"]},{\"text\":\"Зеленський обговорив це рішення з керівництвом ЄС.\",\"sources\":[\"962\"]}],\"sources\":[\"2299\",\"962\"]}",
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
            role = "Strict comparative fact-checker.",
            goal = "Split facts into common overlaps vs source-unique details.",
            schema = """{"${AiJsonContract.COMMON_TOPIC}":"optional short topic label","${AiJsonContract.COMMON_FACTS}":[{"${AiJsonContract.TEXT}":"sentence 1","${AiJsonContract.SOURCES}":["source_id_1","source_id_2"]}],"${AiJsonContract.ITEMS}":[{"${AiJsonContract.SOURCE_ID}":"source id from input","${AiJsonContract.UNIQUE_DETAILS}":["sentence 1"]}]}""",
            rules = listOf(
                "Use only explicit facts from input.",
                "One point = one fact.",
                "COMMON rule: fact is common only if same concrete event/number/claim appears in 2+ sources.",
                "If source B only уточнює source A (detail/parameter/context), do NOT add second common point. Put only the new fragment into source B unique_details.",
                "Good split: common='ЄС погодив 90 млрд євро'; unique(B)='кошти розраховані на 2 роки'. Bad: two common points about same approval.",
                "If some sources overlap and others are off-topic, common_facts must include only overlapping subset.",
                "If all sources are same broad topic but no concrete overlap: common_facts=[] and set common_topic short label.",
                "If sources are unrelated: common_facts=[] and common_topic=\"\".",
                "common_facts max=4 (if only 2 sources, max=2).",
                "Each common_facts item must include 2+ source_ids (exact ids from input).",
                "Reject generic overlap wording: 'обговорювалося', 'йшлося', 'брали участь' without concrete anchor.",
                "No paraphrase duplicates in common_facts.",
                "unique_details max=4 per source_id, only source-specific facts.",
                "Do not include source names/channels inside fact text.",
                "No announcement phrases.",
                languageRule(summaryLanguage)
            ),
            formatInfo = "Scenario A (true overlap):\n{\"common_topic\":\"\",\"common_facts\":[{\"text\":\"ЄС погодив 90 млрд євро для України\",\"sources\":[\"s1\",\"s2\"]}],\"items\":[{\"source_id\":\"s1\",\"unique_details\":[\"Пакет включає новий санкційний блок.\"]},{\"source_id\":\"s2\",\"unique_details\":[\"Кошти розраховані на 2 роки.\"]}]}\n\nScenario B (same broad topic, no shared concrete fact):\n{\"common_topic\":\"війна в Україні\",\"common_facts\":[],\"items\":[{\"source_id\":\"s1\",\"unique_details\":[\"Україна представила нову ракету.\"]},{\"source_id\":\"s2\",\"unique_details\":[\"Іран здійснив масовану атаку.\"]}]}\n\nScenario C (unrelated):\n{\"common_topic\":\"\",\"common_facts\":[],\"items\":[{\"source_id\":\"s1\",\"unique_details\":[\"...\"]},{\"source_id\":\"s2\",\"unique_details\":[\"...\"]}]}"
        )
    }

    private fun languageRule(summaryLanguage: SummaryLanguage): String =
        when (summaryLanguage) {
            SummaryLanguage.UK -> "Output language: Ukrainian only."
            SummaryLanguage.EN -> "Output language: English only."
        }
}
