package com.andrewwin.sumup.domain.usecase.ai

import javax.inject.Inject

class LimitTextsProportionallyUseCase @Inject constructor() {
    operator fun invoke(texts: List<String>, maxTotalChars: Int): List<String> {
        if (texts.isEmpty()) return emptyList()
        if (maxTotalChars <= 0) return texts.map { "" }

        val totalLength = texts.sumOf { it.length }
        if (totalLength <= maxTotalChars) return texts
        if (totalLength <= 0) return texts

        val allocations = texts.map { text ->
            ((text.length.toLong() * maxTotalChars) / totalLength).toInt()
        }.toMutableList()

        var remainingChars = maxTotalChars - allocations.sum()
        val priorityIndexes = texts.indices
            .sortedByDescending { index -> texts[index].length - allocations[index] }

        while (remainingChars > 0) {
            var distributedThisPass = false
            for (index in priorityIndexes) {
                if (remainingChars <= 0) break
                if (allocations[index] >= texts[index].length) continue
                allocations[index] += 1
                remainingChars -= 1
                distributedThisPass = true
            }
            if (!distributedThisPass) break
        }

        return texts.mapIndexed { index, text -> text.take(allocations[index]) }
    }
}
