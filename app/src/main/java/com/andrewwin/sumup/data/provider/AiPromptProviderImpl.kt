package com.andrewwin.sumup.data.provider

import android.content.Context
import com.andrewwin.sumup.R
import com.andrewwin.sumup.domain.provider.AiPromptProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AiPromptProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AiPromptProvider {
    override fun defaultSummaryPrompt(): String = context.getString(R.string.summary_prompt_default)
    override fun questionPromptPrefix(): String = context.getString(R.string.ai_question_prompt_prefix)
    override fun questionPromptSuffix(): String = context.getString(R.string.ai_question_prompt_suffix)
    override fun strictJsonInstruction(): String =
        "You must respond ONLY in valid JSON format. Do not include any greeting, markdown formatting blocks, or conversational text."
}
