package com.andrewwin.sumup.domain

import com.andrewwin.sumup.data.local.entities.Article

interface ArticleTextCleaner {
    fun cleanArticles(articles: List<Article>): List<Article>
}
