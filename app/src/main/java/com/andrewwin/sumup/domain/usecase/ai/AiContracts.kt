package com.andrewwin.sumup.domain.usecase.ai

object AiJsonContract {
    const val HEADLINE = "headline"
    const val ITEMS = "items"
    const val THEMES = "themes"
    const val TITLE = "title"
    const val BULLETS = "bullets"
    const val SOURCE = "source"
    const val SOURCE_URL = "source_url"
    const val SOURCE_ID = "source_id"
    const val EMOJIS = "emojis"
    const val COMMON = "common"
    const val DIFFERENT = "different"
    const val ANSWER = "answer"
    const val STATEMENTS = "statements"
    const val TEXT = "text"
    const val SOURCES = "sources"
}

object AiPromptRules {
    private const val COMPARE_JSON_PROMPT_TEMPLATE = """
Return ONLY valid JSON.
Schema:
{
  "headline": "short event title",
  "items": [
    {
      "source_id": "source id from input",
      "common": ["sentence 1", "sentence 2"],
      "different": ["sentence 1", "sentence 2"]
    }
  ]
}
Rules:
- include each source from input.
- up to 2 concise sentences per source in common and different arrays.
- prioritize only the most informative points across all sources.
- keep total output compact: target up to 5 points for "common" and up to 5 points for "different" after aggregation.
- {{LANGUAGE_RULE}}
- stay factual and grounded in the provided source content.
- write abstractive summaries (not copy-paste or sentence fragments from source).
- use attribute-first phrasing: start with the core fact/property/outcome (e.g., "Має/Підтримує/Отримав..."), then context.
- avoid event-narration lead-ins like "було представлено", "дебютував", "компанія оголосила", unless launch timing is the key difference itself.
- for "common": capture overlaps shared by multiple sources.
- for "different": capture unique angles/details specific to this source.
- avoid repeated or semantically duplicate sentences.
- no markdown, no extra prose.
"""

    fun compareJsonPrompt(languageRule: String): String =
        COMPARE_JSON_PROMPT_TEMPLATE.replace("{{LANGUAGE_RULE}}", languageRule)
}









