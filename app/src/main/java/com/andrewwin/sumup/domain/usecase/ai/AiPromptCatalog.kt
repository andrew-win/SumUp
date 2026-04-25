package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.SummaryLanguage

enum class AiSummaryPromptProfile {
    DIGEST_ANALYTICAL,
    SINGLE_ARTICLE_ANALYTICAL
}

object AiPromptCatalog {

    private const val RULE_NO_INTRO_PHRASES = "Omit filler and generic intros ('Перші повідомлення', 'Зазначається'). Start directly with the core fact."
    private const val RULE_STRICT_ATTRIBUTION = "Do not attribute facts to news outlets or media agencies (e.g., REMOVE 'повідомляє видання X', 'за даними ЗМІ'). Attribution is ONLY allowed for specific authoritative figures, officials, or experts (e.g., 'Президент України повідомив...', 'Міноборони заявило...')."
    private const val RULE_JOURNALISTIC_STYLE = "Strict Reuters/AP style. Remove bureaucratic fluff, hype, and emotional modifiers. Preserve original source uncertainty."
    private const val RULE_READY_UI_COPY = "Write text that is ready to be directly displayed in the UI of a mobile news summarization app: compact, neutral, and concrete."
    private const val RULE_NO_VERBATIM_COPY = "CRITICAL: DO NOT copy input text or titles verbatim. Paraphrase abstractively: rewrite the core facts into fresh, concise, and independent factual statements."
    private const val RULE_SIMPLE_SENTENCES = "Write in simple, easy-to-read sentences. One item/bullet MUST contain exactly ONE sentence. If a fact is complex or contains multiple independent actions, you MUST either split it into multiple separate bullets/items or simplify it into one short sentence. Never put multiple sentences in a single text field. Avoid heavy subordinate clauses, complex phrasing, passive voice, and semicolons."
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
                        "Exclude real-time tactical alerts, operational tracking, or micro-level updates. BAD: 'Тревога❗ Вылез БПЛА за 10 км от берега', '✈️✈️Вышел в море в сторону Фонтанки'. GOOD: 'Цієї ночі можлива масована атака за допомогою бомбардувальників ТУ-95', 'В Одесі сталася маштабна стрілянина, винуватця шукають'.",
                        "Create 1..4 themes. Theme title: 2-4 words max, representing a broad category (e.g., 'Економіка', 'Спорт'). FORBIDDEN: generic meta-titles like 'Ключові новини', 'Головне', 'Важливе', 'Різне'.",
                        "Include 2..4 highly relevant emojis per theme.",
                        "Each theme must have 2..5 distinct items. Merge or omit themes with fewer items. Skip duplicates.",
                        "Each item must represent a single concrete event from exactly one source_id.",
                        "Item title: punchy, action-oriented news ticker style (max 18 words). Lead with the actor.",
                        "Each source_id can appear only once in the entire response.",
                        RULE_SOURCE_IDS_EXACT,
                        RULE_READY_UI_COPY,
                        RULE_NO_VERBATIM_COPY,
                        RULE_SIMPLE_SENTENCES,
                        RULE_JOURNALISTIC_STYLE,
                        RULE_NO_INTRO_PHRASES,
                        RULE_STRICT_ATTRIBUTION,
                        languageRule
                    ),
                    formatInfo = "NOTE: The example below is an etalon response representing high-impact news in distinct broad categories.\n\n" +
                            "Example:\n{\"headline\":\"Головні події дня\",\"themes\":[{\"title\":\"Міжнародна політика\",\"emojis\":[\"🌍\",\"🤝\"],\"items\":[{\"title\":\"Сенат США остаточно схвалив пакет військової допомоги Україні на 60 мільярдів доларів\",\"source_id\":\"101\"},{\"title\":\"Європейська рада офіційно розпочала переговори щодо вступу України до ЄС\",\"source_id\":\"102\"}]},{\"title\":\"Безпека та оборона\",\"emojis\":[\"🛡️\",\"⚔️\"],\"items\":[{\"title\":\"ЗСУ успішно випробували нову балістичну ракету вітчизняного виробництва дальністю 700 кілометрів\",\"source_id\":\"103\"},{\"title\":\"Німеччина передала Україні третій зенітно-ракетний комплекс Patriot із боєкомплектом\",\"source_id\":\"104\"}]},{\"title\":\"Економіка\",\"emojis\":[\"📈\",\"💰\"],\"items\":[{\"title\":\"Національний банк України знизив облікову ставку до 13% для стимулювання бізнесу\",\"source_id\":\"105\"},{\"title\":\"МВФ перерахував Україні новий транш макрофінансової допомоги у розмірі 900 мільйонів доларів\",\"source_id\":\"106\"}]},{\"title\":\"Технології\",\"emojis\":[\"💻\",\"🚀\"],\"items\":[{\"title\":\"SpaceX успішно посадила перший ступінь ракети Starship під час п'ятого тестового польоту\",\"source_id\":\"107\"},{\"title\":\"OpenAI випустила нову модель штучного інтелекту з можливістю міркування в реальному часі\",\"source_id\":\"108\"}]}]}",
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
                        "Each bullet must be a distinct, standalone hard fact (max 20 words) anchored by a concrete entity. Do not fragment facts.",
                        "source: use explicit source name from INPUT, or an empty string.",
                        RULE_READY_UI_COPY,
                        RULE_NO_VERBATIM_COPY,
                        RULE_SIMPLE_SENTENCES,
                        RULE_JOURNALISTIC_STYLE,
                        RULE_NO_INTRO_PHRASES,
                        RULE_STRICT_ATTRIBUTION,
                        languageRule
                    ),
                    formatInfo = "NOTE: The example below is an etalon response providing a multi-angled factual overview of a single news event.\n\n" +
                            "BAD PHRASING (Too complex, 2 sentences): \"Трафік танкерів відновився до рівня, який був до українських атак, за даними фінського видання Yle, що посилається на контр-адмірала з НАТО. Десятки суден очікують завантаження.\"\n" +
                            "GOOD PHRASING (Split or simplified into 1 simple sentence): \"За даними НАТО, трафік танкерів з портів РФ відновився до рівня перед атаками.\"\n\n" +
                            "Example:\n{\"items\":[{\"bullets\":[\"Верховна Рада ухвалила в цілому закон про вдосконалення процесу військової мобілізації.\",\"Документ підтримали 283 народні депутати під час пленарного засідання парламенту.\",\"Новий закон скасовує статус обмежено придатних до військової служби.\",\"Військовозобов'язані повинні оновити свої облікові дані у ТЦК протягом 60 днів.\",\"Закон набере чинності через місяць після підписання президентом.\"],\"source\":\"УНІАН\"}]}",
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
                "short_answer: one natural sentence (max 12 words). Not just 'Так/Ні'. If sources lack info, output ONLY a short answer and leave details empty. For Ukrainian questions use exactly: 'За даними поданих джерел не можна дати відповідь на ваше питання'. For English questions use exactly: 'The provided sources do not contain enough information to answer your question'. In that case details=[], sources=[].",
                "details: 2..5 short factual bullets. Max 20 words per one. Each must include 1..3 valid source_ids from INPUT. If the answer cannot be supported from sources, details must be [].",
                "sources: unique union of source_id values used in details.",
                RULE_SOURCE_IDS_EXACT,
                RULE_READY_UI_COPY,
                RULE_NO_VERBATIM_COPY,
                RULE_SIMPLE_SENTENCES,
                RULE_JOURNALISTIC_STYLE,
                RULE_NO_INTRO_PHRASES,
                RULE_STRICT_ATTRIBUTION
            ),
            formatInfo = "NOTE: The example below is an etalon response showing a clear answer without unasked details.\n\n" +
                    "BAD PHRASING (Multiple sentences, complex): \"Перше джерело зазначає, що збитки склали 3.7 млн грн. Друге джерело підтверджує виділення 2 млн грн на ремонт; також відомо про 3 постраждалих.\"\n" +
                    "GOOD PHRASING (Split or simplified into 1 simple sentence): \"Прямі матеріальні збитки для місцевої інфраструктури оцінюються у 3.7 млн грн.\"\n\n" +
                    "Example:\n{\"question\":\"Які системи ППО обіцяють передати партнери?\",\"short_answer\":\"Партнери планують передати Україні системи Patriot, NASAMS та додаткові зенітні ракети.\",\"details\":[{\"text\":\"Німеччина офіційно оголосила про передачу третьої батареї ЗРК Patriot.\",\"sources\":[\"145\",\"148\"]},{\"text\":\"Норвегія пообіцяла надати дві додаткові пускові установки NASAMS.\",\"sources\":[\"146\"]}],\"sources\":[\"145\",\"148\",\"146\"]}",
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
                "Paraphrase all facts and direct speech into abstractive one-liners (max 20 words).",
                "COMMON_FACTS (max 5): requires exact event/number overlap in 2+ sources. Put mere clarifications or contradictions in unique_details.",
                "COMMON_FACTS requirement: A fact is common ONLY if the exact same event/number/claim appears in 2+ sources.",
                "IF source B only clarifies source A (adds detail/context), DO NOT create a common fact. Put the new fragment in source B's UNIQUE_DETAILS.",
                "If sources share a broad theme but cover completely different events: common_facts=[], and set common_topic to a descriptive label like 'Новини стосуються широкої тематики X, але не мають виражених спільних рис'.",
                "If sources are totally unrelated: common_facts=[], common_topic=\"\".",
                "Include exactly one items object for each input source_id, even if unique_details is empty.",
                "unique_details constraint: max 5 total across all items combined.",
                "Reject generic overlaps without concrete anchors (e.g., skip 'Сторони обговорили співпрацю').",
                RULE_SOURCE_IDS_EXACT,
                RULE_READY_UI_COPY,
                RULE_NO_VERBATIM_COPY,
                RULE_SIMPLE_SENTENCES,
                RULE_JOURNALISTIC_STYLE,
                RULE_NO_INTRO_PHRASES,
                RULE_STRICT_ATTRIBUTION,
                languageRule(summaryLanguage)
            ),
            formatInfo = "NOTE: The examples below provide three etalon scenarios: true overlap, broad theme without overlap, and completely unrelated news.\n\n" +
                    "BAD PHRASING (Complex, multiple sentences): \"Чоловік, що проходив комісію в ТЦК, втік і впав у котлован; він отримав медичну допомогу. Його стан задовільний.\"\n" +
                    "GOOD PHRASING (Split or simplified into 1 simple sentence): \"Чоловік втік з медкомісії ТЦК та впав у технічний котлован.\"\n\n" +
                    "Example 1 (True Overlap - Sources cover the exact same event):\n{\"common_topic\":\"\",\"common_facts\":[{\"text\":\"Уряд Швейцарії офіційно виділить 5 мільярдів франків на відновлення України до 2036 року.\",\"sources\":[\"s1\",\"s2\"]}],\"items\":[{\"source_id\":\"s1\",\"unique_details\":[\"Фінансування переважно спрямують на масштабну відбудову критичної інфраструктури.\"]},{\"source_id\":\"s2\",\"unique_details\":[\"Перший фінансовий транш за цією програмою надійде вже у 2025 році.\"]}]}\n\n" +
                    "Example 2 (Broad Topic - Same sphere, but fundamentally different events):\n{\"common_topic\":\"Новини стосуються широкої тематики технологій, але не мають виражених спільних рис.\",\"common_facts\":[],\"items\":[{\"source_id\":\"s3\",\"unique_details\":[\"Компанія Apple презентувала нове покоління планшетів iPad Pro із процесором M4.\"]},{\"source_id\":\"s4\",\"unique_details\":[\"Корпорація Microsoft інвестує 2 мільярди доларів у розвиток центрів обробки даних у Японії.\"]}]}\n\n" +
                    "Example 3 (Completely Unrelated - No common theme at all):\n{\"common_topic\":\"\",\"common_facts\":[],\"items\":[{\"source_id\":\"s5\",\"unique_details\":[\"Футбольний клуб Реал Мадрид здобув перемогу у фіналі Ліги Чемпіонів.\"]},{\"source_id\":\"s6\",\"unique_details\":[\"На території Індонезії розпочалося масштабне виверження активного вулкана.\"]}]}",
            body = null
        )
    }

    private fun languageRule(summaryLanguage: SummaryLanguage): String =
        when (summaryLanguage) {
            SummaryLanguage.UK -> "Output language: Ukrainian only."
            SummaryLanguage.EN -> "Output language: English only."
        }
}
