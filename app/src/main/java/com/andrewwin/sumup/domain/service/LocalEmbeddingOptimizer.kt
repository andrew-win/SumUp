package com.andrewwin.sumup.domain.service

class LocalEmbeddingOptimizer {
    fun calculateAdjustedScore(
        rawScore: Float,
        featuresA: TextOptimizationFeatures,
        featuresB: TextOptimizationFeatures
    ): Float {
        val jaccard = calculateJaccard(featuresA.jaccardTokens, featuresB.jaccardTokens)
        val jaccardBonus = calculateJaccardBonus(jaccard)

        val commonEntities = featuresA.entities.intersect(featuresB.entities)

        val entityBonus = if (commonEntities.isNotEmpty()) {
            ENTITY_MATCH_BONUS
        } else {
            0f
        }

        val entityMismatchPenalty = if (
            featuresA.entities.isNotEmpty() &&
            featuresB.entities.isNotEmpty() &&
            commonEntities.isEmpty()
        ) {
            ENTITY_MISMATCH_PENALTY
        } else {
            0f
        }

        return (rawScore + jaccardBonus + entityBonus - entityMismatchPenalty).coerceIn(0f, 1f)
    }

    private fun calculateJaccard(leftTokens: Set<String>, rightTokens: Set<String>): Float {
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0f
        val intersectionSize = leftTokens.intersect(rightTokens).size
        val unionSize = leftTokens.union(rightTokens).size
        return if (unionSize == 0) 0f else intersectionSize.toFloat() / unionSize.toFloat()
    }

    private fun calculateJaccardBonus(jaccard: Float): Float {
        if (jaccard < MIN_JACCARD_TO_COUNT) return 0f
        return (jaccard - JACCARD_OFFSET)
            .coerceAtLeast(0f)
            .coerceAtMost(JACCARD_BONUS_CAP)
    }

    companion object {
        private const val MIN_JACCARD_TO_COUNT = 0.05f
        private const val JACCARD_OFFSET = 0.05f
        private const val JACCARD_BONUS_CAP = 0.15f
        private const val ENTITY_MATCH_BONUS = 0.05f
        private const val ENTITY_MISMATCH_PENALTY = 0.10f
    }
}
