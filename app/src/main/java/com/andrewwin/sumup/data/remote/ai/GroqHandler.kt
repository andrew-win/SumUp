package com.andrewwin.sumup.data.remote.ai

import okhttp3.OkHttpClient

class GroqHandler(okHttpClient: OkHttpClient) : 
    OpenAiCompatibleHandler(okHttpClient, BASE_URL) {
    
    private companion object {
        private const val BASE_URL = "https://api.groq.com/openai/v1"
    }
}
