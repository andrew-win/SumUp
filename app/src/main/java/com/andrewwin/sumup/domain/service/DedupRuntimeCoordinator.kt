package com.andrewwin.sumup.domain.service

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DedupRuntimeCoordinator @Inject constructor() {
    private val embeddingsGeneration = AtomicLong(0)
    private val dedupCacheGeneration = AtomicLong(0)

    fun currentEmbeddingsGeneration(): Long = embeddingsGeneration.get()

    fun currentDedupCacheGeneration(): Long = dedupCacheGeneration.get()

    fun invalidateAfterEmbeddingsClear() {
        embeddingsGeneration.incrementAndGet()
        dedupCacheGeneration.incrementAndGet()
    }
}
