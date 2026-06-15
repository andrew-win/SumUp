package com.andrewwin.sumup.domain.ai.prompt

import javax.inject.Inject

class PromptTemplateRenderer @Inject constructor() {

    fun render(template: String, values: Map<String, String>): String {
        return values.entries.fold(template) { result, (key, value) ->
            result.replace("{$key}", value)
        }
    }
}
