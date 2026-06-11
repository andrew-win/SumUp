package com.andrewwin.sumup.domain.feed.dedup

data class DeduplicationRebuildResult(
    val cloudEmbeddingsIncomplete: Boolean = false
)
