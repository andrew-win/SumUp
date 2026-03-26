package com.andrewwin.sumup.domain.usecase.ai

object AiJsonContract {
    const val HEADLINE = "headline"
    const val ITEMS = "items"
    const val TITLE = "title"
    const val BULLETS = "bullets"
    const val SOURCE = "source"
    const val SOURCE_ID = "source_id"
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
- exactly 2 sentences in common and different arrays.
- {{LANGUAGE_RULE}}
- avoid repeated or semantically duplicate sentences.
- no markdown, no extra prose.
"""

    fun compareJsonPrompt(languageRule: String): String =
        COMPARE_JSON_PROMPT_TEMPLATE.replace("{{LANGUAGE_RULE}}", languageRule)
}









