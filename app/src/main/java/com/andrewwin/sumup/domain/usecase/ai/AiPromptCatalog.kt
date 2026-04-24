package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.SummaryLanguage

enum class AiSummaryPromptProfile {
    DIGEST_ANALYTICAL,
    SINGLE_ARTICLE_ANALYTICAL
}

object AiPromptCatalog {

    private const val RULE_NO_INTRO_PHRASES = "Omit filler and generic intros ('Перші повідомлення', 'Зазначається'). Start directly with the core fact. Named attribution is allowed only for accuracy."
    private const val RULE_JOURNALISTIC_STYLE = "Strict Reuters/AP style. Remove bureaucratic fluff, hype, and emotional modifiers. Preserve original source uncertainty."
    private const val RULE_READY_UI_COPY = "Write text that is ready to be directly displayed in the UI of a mobile news summarization app: compact, neutral, and concrete."
    private const val RULE_NO_VERBATIM_COPY = "CRITICAL: DO NOT copy input text or titles verbatim. Paraphrase abstractively: rewrite the core facts into fresh, concise, and independent factual statements."
    private const val RULE_SOURCE_IDS_EXACT = "Use source_id values exactly as provided in INPUT."

    private fun promptEnvelope(role: String, goal: String, schema: String, rules: List<String>, formatInfo: String? = null, body: String? = null): String {
        return buildString {
            appendLine("ROLE")
            appendLine(role)
            appendLine()
            appendLine("GOAL")
            appendLine(goal)
            appendLine()
            appendLine("OUTPUT CONTRACT")
            appendLine("1. Output ONLY a strictly valid JSON object. No markdown, no prose.")
            appendLine("2. Adhere strictly to the provided SCHEMA keys and types.")
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
                    goal = "Build a condensed, hard-fact digest grouped by broad categories.",
                    schema = """{"${AiJsonContract.HEADLINE}":"optional short digest title","${AiJsonContract.THEMES}":[{"${AiJsonContract.TITLE}":"theme title","${AiJsonContract.EMOJIS}":["emoji1","emoji2"],"${AiJsonContract.ITEMS}":[{"${AiJsonContract.TITLE}":"news title","${AiJsonContract.SOURCE_ID}":"source id from input"}]}]}""",
                    rules = listOf(
                        "Create 1..4 themes. Theme title: 2-4 words max, representing a broad category (e.g., 'Економіка', 'Спорт'), not a specific event.",
                        "Include 2..4 highly relevant emojis per theme.",
                        "Each theme must have 2..4 distinct items. Merge or omit themes with fewer items. Skip duplicates.",
                        "Each item must represent a single concrete event from exactly one source_id.",
                        "Item title: punchy, action-oriented news ticker style (max 20-25 words). Lead with the actor.",
                        "Each source_id can appear only once in the entire response.",
                        RULE_SOURCE_IDS_EXACT,
                        RULE_READY_UI_COPY,
                        RULE_NO_VERBATIM_COPY,
                        RULE_JOURNALISTIC_STYLE,
                        RULE_NO_INTRO_PHRASES,
                        languageRule
                    ),
                    formatInfo = "NOTE: The example below is strictly a partial fragment demonstrating schema and style, not a complete response.\n\nExample:\n{\"headline\":\"Головне за день\",\"themes\":[{\"title\":\"Внутрішня політика\",\"emojis\":[\"🏛️\",\"🇺🇦\"],\"items\":[{\"title\":\"Верховна Рада ухвалила в другому читанні закон про мобілізацію з новими поправками\",\"source_id\":\"101\"},{\"title\":\"Президент підписав указ про призначення нового керівника Служби безпеки України\",\"source_id\":\"102\"}]},{\"title\":\"Економіка та бізнес\",\"emojis\":[\"📈\",\"💰\"],\"items\":[{\"title\":\"Національний банк України знизив облікову ставку до 13% для стимулювання кредитування\",\"source_id\":\"103\"},{\"title\":\"Світовий банк виділив додатковий транш у розмірі 1.5 млрд доларів на відновлення інфраструктури\",\"source_id\":\"104\"}]},{\"title\":\"Технології\",\"emojis\":[\"💻\",\"🚀\"],\"items\":[{\"title\":\"Компанія OpenAI представила нову мовну модель GPT-4o з покращеними можливостями голосового спілкування\",\"source_id\":\"105\"},{\"title\":\"SpaceX успішно здійснила третій тестовий запуск космічного корабля Starship на орбіту\",\"source_id\":\"106\"}]},{\"title\":\"Спорт\",\"emojis\":[\"⚽\",\"🏆\"],\"items\":[{\"title\":\"Збірна України з футболу здобула вольову перемогу над Ісландією та вийшла на Євро-2024\",\"source_id\":\"107\"},{\"title\":\"Олександр Усик став абсолютним чемпіоном світу у надважкій вазі після перемоги над Тайсоном Ф'юрі\",\"source_id\":\"108\"}]}]}",
                    body = basePrompt
                )
            }
            AiSummaryPromptProfile.SINGLE_ARTICLE_ANALYTICAL -> {
                promptEnvelope(
                    role = "Strict single-article news analyst.",
                    goal = "Extract the critical hard facts into punchy bullet points.",
                    schema = """{"${AiJsonContract.ITEMS}":[{"${AiJsonContract.BULLETS}":["point 1","point 2"],"${AiJsonContract.SOURCE}":"optional source name"}]}""",
                    rules = listOf(
                        "Return exactly one item object containing 2..5 bullets.",
                        "Each bullet must be a distinct, standalone hard fact (max 30-40 words) anchored by a concrete entity. Do not fragment facts.",
                        "source: use explicit source name from INPUT, or an empty string.",
                        RULE_READY_UI_COPY,
                        RULE_NO_VERBATIM_COPY,
                        RULE_JOURNALISTIC_STYLE,
                        RULE_NO_INTRO_PHRASES,
                        languageRule
                    ),
                    formatInfo = "NOTE: The example below is strictly a partial fragment demonstrating schema and style, not a complete response.\n\nExample:\n{\"items\":[{\"bullets\":[\"Компанія Apple офіційно презентувала нову лінійку смартфонів iPhone 16 під час щорічної осінньої презентації в штаб-квартирі у Купертіно.\",\"Нові базові моделі отримали процесор A18, покращену систему охолодження та кнопку Action Button, яка раніше була ексклюзивом Pro-версій.\",\"Стартова ціна базового смартфона складатиме 799 доларів, а офіційні продажі в роздрібних магазинах США та Європи розпочнуться 20 вересня.\"],\"source\":\"Bloomberg\"}]}",
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
            goal = "Return a strict 3-block answer: question, short direct answer, then evidence-backed details.",
            schema = """{"${AiJsonContract.QUESTION}":"string","${AiJsonContract.SHORT_ANSWER}":"string","${AiJsonContract.DETAILS}":[{"${AiJsonContract.TEXT}":"string","${AiJsonContract.SOURCES}":["source_id"]}],"${AiJsonContract.SOURCES}":["source_id"]}""",
            rules = listOf(
                "Answer ONLY from provided sources, in the same language as the question.",
                "question: repeat user question concisely in one line.",
                "short_answer: one natural sentence (8-18 words). Not just 'Так/Ні'. If sources lack info, output: 'На основі інформації із джерел не можна відповісти на питання', details=[], sources=[].",
                "details: 2..5 short factual bullets. Each must include 1..3 valid source_ids from INPUT.",
                "sources: unique union of source_id values used in details.",
                RULE_SOURCE_IDS_EXACT,
                RULE_READY_UI_COPY,
                RULE_NO_VERBATIM_COPY,
                RULE_JOURNALISTIC_STYLE,
                RULE_NO_INTRO_PHRASES
            ),
            formatInfo = "NOTE: The example below is strictly a partial fragment demonstrating schema and style, not a complete response.\n\nExample:\n{\"question\":\"Які числа згадуються у новинах?\",\"short_answer\":\"У новинах згадані 3.7 млн грн прямих збитків, 2 млн грн цільового фінансування та 3 постраждалих місцевих жителів.\",\"details\":[{\"text\":\"Перше джерело вказує на прямі матеріальні збитки для інфраструктури у розмірі 3.7 млн грн.\",\"sources\":[\"2299\"]},{\"text\":\"Друге джерело повідомляє про виділення 2 млн грн цільового фінансування на ремонтні роботи.\",\"sources\":[\"962\"]}],\"sources\":[\"2299\",\"962\"]}",
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
            goal = "Distill sources into a crisp matrix of overlapping facts and unique details.",
            schema = """{"${AiJsonContract.COMMON_TOPIC}":"optional short topic label","${AiJsonContract.COMMON_FACTS}":[{"${AiJsonContract.TEXT}":"sentence 1","${AiJsonContract.SOURCES}":["source_id_1","source_id_2"]}],"${AiJsonContract.ITEMS}":[{"${AiJsonContract.SOURCE_ID}":"source id from input","${AiJsonContract.UNIQUE_DETAILS}":["sentence 1"]}]}""",
            rules = listOf(
                "Paraphrase all facts and direct speech into abstractive one-liners (max 30-35 words).",
                "COMMON_FACTS (max 4): requires exact event/number overlap in 2+ sources. Put mere clarifications or contradictions in unique_details.",
                "COMMON_FACTS requirement: A fact is common ONLY if the exact same event/number/claim appears in 2+ sources.",
                "IF source B only clarifies source A (adds detail/context), DO NOT create a common fact. Put the new fragment in source B's UNIQUE_DETAILS.",
                "If sources share a broad theme but cover completely different events: common_facts=[], and set common_topic to a descriptive label like 'Хоча новини стосуються однієї сфери, але вони описують різні події'.",
                "If sources are totally unrelated: common_facts=[], common_topic=\"\".",
                "Include exactly one items object for each input source_id, even if unique_details is empty.",
                "unique_details constraint: max 4 total across all items combined.",
                "Reject generic overlaps without concrete anchors (e.g., skip 'Сторони обговорили співпрацю').",
                RULE_SOURCE_IDS_EXACT,
                RULE_READY_UI_COPY,
                RULE_NO_VERBATIM_COPY,
                RULE_JOURNALISTIC_STYLE,
                RULE_NO_INTRO_PHRASES,
                languageRule(summaryLanguage)
            ),
            formatInfo = "NOTE: The examples below are strictly partial fragments demonstrating schema and style, not complete responses.\n\nTrue Overlap Example:\n{\"common_topic\":\"\",\"common_facts\":[{\"text\":\"Європейський Союз офіційно погодив виділення Україні 90 млрд євро макрофінансової допомоги\",\"sources\":[\"s1\",\"s2\"]}],\"items\":[{\"source_id\":\"s1\",\"unique_details\":[\"Пакет фінансової підтримки також включає новий жорсткий блок санкцій проти енергетичного сектору РФ\"]},{\"source_id\":\"s2\",\"unique_details\":[\"Український уряд планує спрямувати отримані макрофінансові кошти на покриття соціальних виплат протягом наступного кварталу\"]}]}\n\nBroad Topic Example:\n{\"common_topic\":\"Хоча новини стосуються однієї сфери (технології), але вони описують різні події\",\"common_facts\":[],\"items\":[{\"source_id\":\"s1\",\"unique_details\":[\"Компанія Apple анонсувала випуск нового процесора M4 для лінійки планшетів iPad Pro\"]},{\"source_id\":\"s2\",\"unique_details\":[\"Microsoft інвестувала 2 мільярди доларів у розвиток дата-центрів для штучного інтелекту в Японії\"]}]}",
            body = null
        )
    }

    private fun languageRule(summaryLanguage: SummaryLanguage): String =
        when (summaryLanguage) {
            SummaryLanguage.UK -> "Output language: Ukrainian only."
            SummaryLanguage.EN -> "Output language: English only."
        }
}