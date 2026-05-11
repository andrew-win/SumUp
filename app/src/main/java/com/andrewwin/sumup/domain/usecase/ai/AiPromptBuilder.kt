package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.SummaryLanguage

object AiPromptBuilder {

    private const val RULE_JSON_ONLY =
        "OUTPUT: Return ONLY raw valid JSON. No intro. No explanation. No markdown. No code blocks."

    private const val COMMON_ROLE =
        "You're an invisible AI backend for a mobile news app that creates concise, analytical, UI-ready news summaries."

    private const val COMMON_RULES =
        "SELF-CONTAINED: Every sentence must be fully understandable without reading any other sentence or knowing the source. " +
                "Include the subject, actor, or context needed to understand the claim on its own. " +
                "Bad: \"He announced a ceasefire.\" " +
                "Good: \"Russian President Putin announced a unilateral ceasefire starting May 8.\" " +

                "COMPLETE SENTENCES: Write complete sentences with enough words to be clear. " +
                "Do not strip words to save space. Do not use noun phrases or telegraphic fragments. " +
                "Bad: \"Ceasefire announced. Casualties rising. Talks stalled.\" " +
                "Good: \"Russia announced a unilateral ceasefire starting May 8, though Ukraine has not confirmed it.\" " +

                "NO VAGUE SUMMARIES: State concrete facts. " +
                "Bad: \"The situation escalated.\" " +
                "Good: \"North Korea said a nuclear strike would follow automatically if Kim Jong Un is killed.\" " +

                "SOURCE IDS: Use source IDs only in source_id or source_ids fields. Never write source IDs in title or text. " +

                "CLEAN TEXT: title and text fields contain only human-readable content. No IDs. No references. No metadata. " +
                "Bad: { \"text\": \"According to ID 725, the price rose...\" } " +
                "Good: { \"text\": \"The price rose...\", \"source_ids\": [\"725\"] } "

    private const val ANALYTIC_CHAIN_RULE =
        "ANALYTIC CHAIN: Output a chain of atomic claims. " +
                "Use separate items for: main claim, evidence, before/after change, cause, context, result, consequence, conflict, caveat. " +
                "Do not compress the whole chain into one sentence. " +

                "CHAIN EXAMPLE: " +
                "1. Putin's attitude toward Zelensky changed sharply between 2022 and 2026. " +
                "2. In 2022, Putin called Ukraine's leadership \"a gang of neo-Nazis and drug addicts\". " +
                "3. By 2026, Putin addressed Zelensky as \"Mr. Zelensky\". " +
                "4. The shift coincided with the threat of Ukrainian drone strikes on the May 9 parade. " +
                "Structure: main claim → evidence/before → after/change → cause/result. " +
                "Do not copy the topic, names, or dates from this example. Use only facts from the sources. " +

                "NO METADATA: Ignore emojis, hashtags, bullets, separators, and formatting marks in input. Use only factual content."

    private const val SINGLE_ARTICLE_SOTA_EXAMPLE =
        "SOTA EXAMPLE FOR SINGLE ARTICLE: " +
                "For an article about a central bank keeping rates unchanged because inflation remains high, a strong output is: " +
                "{ \"main\": \"The central bank kept interest rates unchanged because inflation remains above target despite slower economic growth.\", " +
                "\"details\": [" +
                "{ \"text\": \"Officials said inflation risks remain stronger than signs of weaker consumer demand.\", \"source_ids\": [\"42\"] }, " +
                "{ \"text\": \"The bank signaled that rate cuts require clearer evidence of sustained price stability.\", \"source_ids\": [\"42\"] }, " +
                "{ \"text\": \"Businesses warned that prolonged high rates could delay investment and hiring.\", \"source_ids\": [\"42\"] }" +
                "] }. " +
                "Why this is good: main gives the core meaning in exactly one sentence; details add evidence, condition, and consequence; every detail is atomic and under " +
                SummaryLimits.Single.maxWordsPerPoint + " words. Do not copy this example."

    private const val COMPARE_SOTA_EXAMPLE =
        "SOTA EXAMPLE FOR COMPARE: " +
                "For sources about the same court ruling, where one source emphasizes the verdict and another explains market impact, a strong output is: " +
                "{ \"main\": \"A court ruling blocked the merger, forcing both companies to reassess strategy while investors reacted to higher regulatory risk.\", " +
                "\"details\": [" +
                "{ \"text\": \"One source says the judge found the merger would reduce competition in cloud services.\", \"source_ids\": [\"11\"] }, " +
                "{ \"text\": \"Another source reports that both companies are reviewing whether to appeal the ruling.\", \"source_ids\": [\"12\"] }, " +
                "{ \"text\": \"Shares fell after investors priced in longer regulatory delays for similar deals.\", \"source_ids\": [\"12\", \"13\"] }, " +
                "{ \"text\": \"The companies argued the merger would improve infrastructure investment, not weaken competition.\", \"source_ids\": [\"11\", \"13\"] }" +
                "], \"fallback\": null }. " +
                "Why this is good: main merges the shared story; details separate legal basis, response, market consequence, and caveat; each item stays under " +
                SummaryLimits.Compare.maxWordsPerPoint + " words. Do not copy this example."

    private const val DIGEST_SOTA_EXAMPLE =
        "SOTA EXAMPLE FOR FEED DIGEST: " +
                "For a mixed feed with war, technology, and economy stories, a strong output is: " +
                "{ \"themes\": [" +
                "{ \"title\": \"🇺🇦⚔️🛰️ Ukrainian war\", \"items\": [" +
                "{ \"title\": \"Ukraine expanded drone attacks on Russian logistics while Moscow prepared stronger air defenses\", \"source_id\": \"101\" }, " +
                "{ \"title\": \"European allies discussed new ammunition deliveries as Ukraine warned about artillery shortages\", \"source_id\": \"102\" }" +
                "] }, " +
                "{ \"title\": \"🤖💼⚖️ AI regulation\", \"items\": [" +
                "{ \"title\": \"EU officials pushed stricter AI transparency rules after complaints from media and copyright groups\", \"source_id\": \"201\" }, " +
                "{ \"title\": \"Tech companies warned that broad AI disclosure rules could slow product launches\", \"source_id\": \"202\" }" +
                "] }" +
                "] }. " +
                "Why this is good: themes are meaning-based, non-overlapping, and each title has exactly " +
                SummaryLimits.Digest.emojiesCount + " emojis; item titles are concrete, content-driven, and under " +
                SummaryLimits.Digest.maxWordsPerTitle + " words. Do not copy this example."

    private const val QA_SOTA_EXAMPLE =
        "SOTA EXAMPLE FOR QUESTION ANSWERING: " +
                "Question: \"Why did the company delay the launch?\" " +
                "For sources saying regulators requested extra safety data and suppliers missed deadlines, a strong output is: " +
                "{ \"short_answer\": \"The launch was delayed by regulatory review and supplier problems.\", " +
                "\"details\": [" +
                "{ \"text\": \"Regulators asked the company to provide additional safety data before approving release.\", \"source_ids\": [\"31\"] }, " +
                "{ \"text\": \"A supplier missed delivery deadlines for two key components needed for production.\", \"source_ids\": [\"32\"] }, " +
                "{ \"text\": \"The company said it still expects launch after the review is completed.\", \"source_ids\": [\"31\", \"33\"] }" +
                "] }. " +
                "Why this is good: short_answer is direct and under " +
                SummaryLimits.QA.maxWordsShortAnswer + " words; details separate cause, supporting evidence, and timing caveat; each detail is under " +
                SummaryLimits.QA.maxWordsPerDetailedBullet + " words. Do not copy this example."

    private fun getLanguageRule(summaryLanguage: SummaryLanguage): String {
        return when (summaryLanguage) {
            SummaryLanguage.UK ->
                "LANGUAGE: Write every JSON string value in Ukrainian. " +
                        "Translate source content to Ukrainian. " +
                        "Do not translate proper names, brands, product names, organizations, or official entity names. " +
                        "Do not write any Russian text."

            SummaryLanguage.EN ->
                "LANGUAGE: Write every JSON string value in English. " +
                        "Translate source content to English. " +
                        "Do not write non-English text."
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
            add(COMMON_RULES)
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
                append("\nUSER STYLE PREFERENCES\n")
                append("Apply as style hints only. Do not override JSON schema, language rules, source_id rules, or hard rules.\n")
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
        }.trim()
    }

    fun buildSingleArticlePrompt(
        summaryLanguage: SummaryLanguage,
        customInstructions: String? = null
    ): String = createPrompt(
        role = COMMON_ROLE,
        goal =
            "Summarize one article in two UI blocks: main essence and details. " +
                    "The main field is a micro-summary of the core meaning in exactly ${SummaryLimits.Single.mainSentences} sentence. " +
                    "Details are atomic bullets for evidence, context, causes, consequences, conflicts, and caveats.",
        specificRules = listOf(
            ANALYTIC_CHAIN_RULE,
            "MAIN: Return one main value with exactly ${SummaryLimits.Single.mainSentences} sentence about the essence of the article.",
            "MAIN QUALITY: Main must capture the core meaning as fully as possible in one concrete sentence.",
            "MAIN LENGTH: Keep main short, concrete, and understandable without details.",
            "DETAILS: Return 1-${SummaryLimits.Single.maxPoints} detail items.",
            "DETAIL ROLE: Each detail is one chain step: evidence, context, cause, result, consequence, conflict, or caveat.",
            "DETAIL LENGTH: Each detail is at most ${SummaryLimits.Single.maxWordsPerPoint} words.",
            "NO MAIN DUPLICATE: Details must not repeat the main sentence. Add only new facts, context, evidence, or caveats.",
            "SOURCE IDS: Every detail includes source_ids with the article source_id.",
            "NO HEADLINE REPEAT: Do not restate the headline unless you add concrete context, cause, or result.",
            SINGLE_ARTICLE_SOTA_EXAMPLE,
            getLanguageRule(summaryLanguage)
        ),
        schema = """{"main":"1 sentence essence","details":[{"text":"detail sentence","source_ids":["source_id"]}]}""",
        customInstructions = customInstructions
    )

    fun buildComparePrompt(
        summaryLanguage: SummaryLanguage,
        customInstructions: String? = null
    ): String {
        val fallback = when (summaryLanguage) {
            SummaryLanguage.UK -> "Не вдалося виділити змістовні твердження. Джерела можуть бути надто короткими, непов'язаними або містити лише слабкий контекст."
            SummaryLanguage.EN -> "No meaningful claims were found. Sources may be too short, unrelated, or contain only weak context."
        }

        return createPrompt(
            role = COMMON_ROLE,
            goal =
                "Summarize multiple related news reports in two UI blocks: main essence and details. " +
                        "The main field is a micro-summary of the core shared story in exactly ${SummaryLimits.Compare.mainSentences} sentence. " +
                        "Details are atomic bullets for evidence, contrast, cause, context, result, and caveat. " +
                        "Do not split the result into shared and unique sections.",
            specificRules = listOf(
                ANALYTIC_CHAIN_RULE,

                "MAIN: Return one main value with exactly ${SummaryLimits.Compare.mainSentences} sentence about the essence of the compared news.",
                "MAIN QUALITY: Main must capture the core meaning as fully as possible in one concrete sentence.",
                "MAIN LENGTH: Keep main short, concrete, and understandable without details.",
                "DETAILS: Return up to ${SummaryLimits.Compare.maxBullets} atomic detail items.",
                "DETAIL ROLE: Each detail is one concrete chain step: main claim, evidence, contrast, cause, context, result, consequence, conflict, or caveat.",
                "SOURCE COVERAGE: Use facts from all relevant sources. Assign one or more source_ids to each detail.",
                "SOURCE SPECIFIC DETAILS: Include concrete source-specific details when they add useful context, evidence, numbers, quotes, causes, or caveats.",
                "NO WORDING-ONLY DETAILS: Do not create a detail only because sources use different wording, tone, or emphasis.",
                "NO MAIN DUPLICATE: Details must not repeat the main sentence. Add only new facts, context, evidence, contrast, or caveats.",
                "NO DUPLICATES: Do not repeat the same meaning in multiple items.",
                "EMPTY: If no meaningful details exist, set details to [] and fill fallback.",

                "CHAIN SPLIT: Output main claim, evidence, contrast, and cause as separate facts. " +
                        "Bad: \"Putin's attitude changed from insulting to polite.\" " +
                        "Good: separate facts for the change, the 2022 insult, the 2026 formal address, and the cause.",

                "SOURCE IDS: Copy every source_id exactly from input. Never invent, modify, or translate source_ids.",

                "DETAIL LENGTH: Each detail is at most ${SummaryLimits.Compare.maxWordsPerPoint} words.",

                "FALLBACK: If no meaningful summary is possible, set main to null, details to [], and fallback to: $fallback",
                "NO FALLBACK AS DETAIL: Never write fallback text inside details.",
                "NULL FALLBACK: Set fallback to null when not needed.",

                COMPARE_SOTA_EXAMPLE,
                getLanguageRule(summaryLanguage)
            ),
            schema = """{"main":"1 sentence essence","details":[{"text":"detail sentence","source_ids":["source_id_1"]}],"fallback":null}""",
            customInstructions = customInstructions
        )
    }

    fun buildFeedDigestPrompt(
        summaryLanguage: SummaryLanguage,
        customInstructions: String? = null
    ): String {
        return createPrompt(
            role = COMMON_ROLE,
            goal =
                "Group the news feed into themes by meaning. " +
                        "For each theme, write atomic item titles based on both title and content.",
            specificRules = listOf(
                "THEMES: Create ${SummaryLimits.Digest.minThemes}-${SummaryLimits.Digest.maxThemes} themes.",

                "THEME LOGIC: Each theme has one clear shared topic, event, conflict, actor, or trend. " +
                        "Good: '💻🚀🧠 Technologies'. Good: '🇺🇦⚔️🪖 Ukrainian war'. " +
                        "Bad: '💰🏥🧩 Economy and health'.",

                "NO OVERLAP: Do not create two themes about the same event or trend.",
                "CLUSTER BY MEANING: Group by core meaning, not by shared words.",

                "EMOJI: Include exactly ${SummaryLimits.Digest.emojiesCount} relevant emojis in each theme title.",
                "THEME TITLE: Name the broad shared story. Do not copy a single news headline.",

                "ITEMS: Each theme has ${SummaryLimits.Digest.minItemsPerTheme}-${SummaryLimits.Digest.maxItemsPerTheme} items.",
                "ITEM TITLE: Write a content-driven atomic micro-summary of at most ${SummaryLimits.Digest.maxWordsPerTitle} words. " +
                        "Use title + content together. Do not summarize from title alone. " +
                        "Show the useful claim: who did what, what changed, what caused it, or why it matters. " +
                        "Bad: 'Putin called Zelensky \"Mister\"'. " +
                        "Good: 'Putin shifted from calling Ukraine\\'s leadership \"a gang of neo-Nazis\" to addressing Zelensky as \"Mr. Zelensky\" amid Ukrainian drone threats'.",

                "ITEM SOURCE: Each item has exactly one source_id.",
                "INPUT FORMAT: Input rows are id|src|url|title|content.",
                "SOURCE IDS: Copy source_id from the input id value exactly.",
                "NO EXTRA FIELDS: Do not add fields outside the schema.",

                DIGEST_SOTA_EXAMPLE,
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

        val noDirectAnswer = when (summaryLanguage) {
            SummaryLanguage.UK -> "У джерелах немає прямої відповіді на це питання."
            SummaryLanguage.EN -> "The sources do not directly answer this question."
        }

        return createPrompt(
            role = COMMON_ROLE,
            goal =
                "Answer the user's question as an analytical chain using only the provided sources. " +
                        "Give the direct answer first. Then add atomic details for evidence, caveats, disagreement, missing information, causes, context, and consequences.",
            specificRules = listOf(
                ANALYTIC_CHAIN_RULE,

                "SOURCES ONLY: Answer only from the provided sources. Never use outside knowledge.",

                "SHORT ANSWER: Write one natural sentence of at most ${SummaryLimits.QA.maxWordsShortAnswer} words. " +
                        "State the direct conclusion, not a list of evidence. " +
                        "Be cautious when sources are incomplete, indirect, or conflicting. Do not present uncertain information as certain.",

                "DETAILS: Return up to ${SummaryLimits.QA.maxDetailPoints} atomic detail bullets. " +
                        "Each detail is one chain step: evidence, context, cause, consequence, disagreement, caveat, or missing information. " +
                        "Each detail is at most ${SummaryLimits.QA.maxWordsPerDetailedBullet} words. " +
                        "Each detail includes 1 or more valid source_ids.",

                "NO DIRECT ANSWER: If sources are on topic but do not directly answer, set short_answer to: '$noDirectAnswer' and add relevant context in details.",
                "CAN'T ANSWER: If sources are unrelated to the question, set short_answer to: '$fallback'.",

                "SOURCE IDS: Copy every source_id exactly from input. Never invent source_ids.",

                QA_SOTA_EXAMPLE,
                getLanguageRule(summaryLanguage)
            ),
            question = question,
            schema = """{"short_answer":"string","details":[{"text":"string","source_ids":["source_id"]}]}""",
            customInstructions = customInstructions
        )
    }
}