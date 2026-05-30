package com.andrewwin.sumup.domain.ai

import com.andrewwin.sumup.domain.entities.settings.DeduplicationStrategy
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalEmbeddingWarmupCoordinator @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val localEmbeddingProvider: LocalEmbeddingProvider
) {
    private val warmupScheduled = AtomicBoolean(false)

    suspend fun warmUpAfterAppStart() {
        if (!warmupScheduled.compareAndSet(false, true)) {
            return
        }

        delay(WARMUP_DELAY_MS)
        val preferences = userPreferencesRepository.preferences.first()
        val shouldWarmUp = preferences.isDeduplicationEnabled &&
            preferences.deduplicationStrategy == DeduplicationStrategy.LOCAL

        if (!shouldWarmUp) {
            return
        }

        localEmbeddingProvider.initialize()
    }

    private companion object {
        private const val WARMUP_DELAY_MS = 300L
    }
}
