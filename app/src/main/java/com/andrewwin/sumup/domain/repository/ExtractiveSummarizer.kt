package com.andrewwin.sumup.domain.repository

interface ExtractiveSummarizer {
    fun summarize(text: String, sentenceCount: Int = 3): List<String>
    fun getCentralHeadlines(headlines: List<String>, count: Int = 3, alpha: Double = 0.7): List<String>
}
