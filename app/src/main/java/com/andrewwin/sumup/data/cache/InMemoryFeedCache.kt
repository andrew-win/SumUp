package com.andrewwin.sumup.data.cache

import com.andrewwin.sumup.domain.ArticleCluster
import com.andrewwin.sumup.domain.repository.FeedCache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryFeedCache @Inject constructor() : FeedCache {
    @Volatile
    private var value: List<ArticleCluster>? = null

    override fun get(): List<ArticleCluster>? = value

    override fun set(value: List<ArticleCluster>) {
        this.value = value
    }
}
