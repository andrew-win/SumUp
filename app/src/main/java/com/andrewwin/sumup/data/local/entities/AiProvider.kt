package com.andrewwin.sumup.data.local.entities

import androidx.annotation.StringRes
import com.andrewwin.sumup.R

enum class AiProvider(@StringRes val labelRes: Int) {
    GEMINI(R.string.provider_gemini),
    GROQ(R.string.provider_groq),
    OPENROUTER(R.string.provider_openrouter),
    COHERE(R.string.provider_cohere),
    CHATGPT(R.string.provider_chatgpt),
    CLAUDE(R.string.provider_claude)
}






