package com.andrewwin.sumup.domain.news

import com.andrewwin.sumup.data.local.entities.SourceType

interface ArticleContentCleaner {
    suspend fun detectFooterPattern(texts: List<String>): String?
    suspend fun extractMainContent(url: String, rawContent: String, type: SourceType): String
    suspend fun clean(text: String, type: SourceType, footerPattern: String? = null): String
}
