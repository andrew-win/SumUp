package com.andrewwin.sumup.data.remote.ai.handlers

import okhttp3.OkHttpClient

class OpenRouterHandler(okHttpClient: OkHttpClient) : 
    OpenAiCompatibleHandler(okHttpClient, BASE_URL) {
    
    private companion object {
        private const val BASE_URL = "https://openrouter.ai/api/v1"
    }
}
