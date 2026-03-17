package com.andrewwin.sumup.domain.repository

import com.andrewwin.sumup.domain.ArticleCluster

interface FeedCache {
    fun get(): List<ArticleCluster>?
    fun set(value: List<ArticleCluster>)
}
