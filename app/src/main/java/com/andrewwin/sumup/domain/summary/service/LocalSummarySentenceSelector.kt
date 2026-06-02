package com.andrewwin.sumup.domain.summary.service

import android.util.Log
import com.andrewwin.sumup.domain.ai.embedding.LocalEmbeddingProvider
import com.andrewwin.sumup.domain.article.deduplication.SimilarityScorer
import com.andrewwin.sumup.domain.summary.model.ExtractiveSentenceCandidate
import java.util.Locale
import javax.inject.Inject

class LocalSummarySentenceSelector @Inject constructor(
    private val localEmbeddingProvider: LocalEmbeddingProvider,
    private val similarityScorer: SimilarityScorer
) {

    suspend fun initialize(): Boolean =
        localEmbeddingProvider.initialize()

    suspend fun selectDistinct(
        candidates: List<ExtractiveSentenceCandidate>,
        maxCount: Int
    ): List<String> {
        return selectDistinctCandidates(candidates, maxCount).map { it.text }
    }

    suspend fun selectDistinctCandidates(
        candidates: List<ExtractiveSentenceCandidate>,
        maxCount: Int
    ): List<ExtractiveSentenceCandidate> {
        if (candidates.isEmpty() || maxCount <= 0) return emptyList()

        val sentenceCandidates = buildCandidates(candidates)
        if (sentenceCandidates.isEmpty()) return emptyList()

        val similarityMatrix = buildSimilarityMatrix(sentenceCandidates)
        logSimilarityMatrix(sentenceCandidates, similarityMatrix)

        val groups = buildSimilarityGroups(sentenceCandidates, similarityMatrix)
        logGroups(sentenceCandidates, groups)

        val representativeIndexes = groups
            .map { group -> selectRepresentativeIndex(group, sentenceCandidates) }
            .sortedByDescending { sentenceCandidates[it].extractiveCandidate.score }
            .take(maxCount)
            .sortedBy { sentenceCandidates[it].extractiveCandidate.originalIndex }

        representativeIndexes.forEachIndexed { order, index ->
            val candidate = sentenceCandidates[index]
            Log.d(
                DEBUG_TAG,
                "representative rank=${order + 1} index=$index score=${candidate.extractiveCandidate.score.format3()} " +
                    "text=\"${candidate.extractiveCandidate.text.safeSnippet()}\""
            )
        }

        return representativeIndexes.map { sentenceCandidates[it].extractiveCandidate }
    }

    suspend fun isNearDuplicate(candidateText: String, selectedTexts: List<String>): Boolean {
        if (selectedTexts.isEmpty()) return false

        val candidates = buildCandidates(
            listOf(ExtractiveSentenceCandidate(candidateText, 0, 0.0)) +
                selectedTexts.mapIndexed { index, text ->
                    ExtractiveSentenceCandidate(
                        text,
                        index + 1,
                        0.0
                    )
                }
        )
        if (candidates.isEmpty()) return false

        val similarityMatrix = buildSimilarityMatrix(candidates)
        for (index in 1 until candidates.size) {
            val selectedCandidate = candidates[index]
            if (similarityMatrix[0][index] >= SummaryLimits.LocalSummary.nearDuplicateThreshold) {
                Log.d(
                    DEBUG_TAG,
                    "near_duplicate candidate=\"${candidates[0].extractiveCandidate.text.safeSnippet()}\" " +
                        "selected=\"${selectedCandidate.extractiveCandidate.text.safeSnippet()}\" " +
                        "similarity=${similarityMatrix[0][index].format3()} threshold=${SummaryLimits.LocalSummary.nearDuplicateThreshold.format3()}"
                )
                return true
            }
        }
        return false
    }

    private suspend fun buildCandidates(candidates: List<ExtractiveSentenceCandidate>): List<SentenceCandidate> {
        return candidates.map { candidate ->
            val embedding = localEmbeddingProvider.computeLocalEmbedding(candidate.text)
                .takeIf { it.isNotEmpty() }
            SentenceCandidate(extractiveCandidate = candidate, embedding = embedding)
        }
    }

    private fun buildSimilarityMatrix(candidates: List<SentenceCandidate>): Array<FloatArray> {
        val size = candidates.size
        return Array(size) { row ->
            FloatArray(size) { column ->
                if (row == column) 1f else calculateCandidateSimilarity(candidates[row], candidates[column])
            }
        }
    }

    private fun calculateCandidateSimilarity(
        left: SentenceCandidate,
        right: SentenceCandidate
    ): Float {
        val leftEmbedding = left.embedding
        val rightEmbedding = right.embedding
        if (leftEmbedding == null || rightEmbedding == null) {
            return if (left.extractiveCandidate.text == right.extractiveCandidate.text) 1f else 0f
        }
        return similarityScorer.calculateSimilarity(leftEmbedding, rightEmbedding)
    }

    private fun buildSimilarityGroups(
        candidates: List<SentenceCandidate>,
        similarityMatrix: Array<FloatArray>
    ): List<Set<Int>> {
        val remainingIndexes = candidates.indices
            .sortedWith(
                compareByDescending<Int> { candidates[it].extractiveCandidate.score }
                    .thenByDescending { candidates[it].extractiveCandidate.text.length }
                    .thenBy { candidates[it].extractiveCandidate.originalIndex }
            )
            .toMutableSet()
        val groups = mutableListOf<Set<Int>>()

        while (remainingIndexes.isNotEmpty()) {
            val seedIndex = remainingIndexes.first()
            val group = linkedSetOf<Int>()

            group += seedIndex
            remainingIndexes.remove(seedIndex)

            val iterator = remainingIndexes.iterator()
            while (iterator.hasNext()) {
                val candidateIndex = iterator.next()
                if (candidateIndex.hasSimilarityToEverySentenceIn(group, similarityMatrix)) {
                    group += candidateIndex
                    iterator.remove()
                }
            }
            groups += group
        }

        return groups
    }

    private fun selectRepresentativeIndex(
        group: Set<Int>,
        candidates: List<SentenceCandidate>
    ): Int {
        return group.maxWithOrNull(
            compareBy<Int> { candidates[it].extractiveCandidate.score }
                .thenByDescending { candidates[it].extractiveCandidate.text.length }
                .thenBy { candidates[it].extractiveCandidate.originalIndex }
        ) ?: group.first()
    }

    private fun logSimilarityMatrix(
        candidates: List<SentenceCandidate>,
        similarityMatrix: Array<FloatArray>
    ) {
        Log.d(DEBUG_TAG, "matrix size=${candidates.size} threshold=${SummaryLimits.LocalSummary.nearDuplicateThreshold.format3()}")
        candidates.forEachIndexed { index, candidate ->
            Log.d(
                DEBUG_TAG,
                "matrix candidate index=$index score=${candidate.extractiveCandidate.score.format3()} " +
                    "text=\"${candidate.extractiveCandidate.text.safeSnippet()}\""
            )
        }
        for (row in similarityMatrix.indices) {
            for (column in row + 1 until similarityMatrix[row].size) {
                Log.d(
                    DEBUG_TAG,
                    "matrix pair left=$row right=$column similarity=${similarityMatrix[row][column].format3()} " +
                        "leftText=\"${candidates[row].extractiveCandidate.text.safeSnippet()}\" " +
                        "rightText=\"${candidates[column].extractiveCandidate.text.safeSnippet()}\""
                )
            }
        }
    }

    private fun logGroups(
        candidates: List<SentenceCandidate>,
        groups: List<Set<Int>>
    ) {
        groups.forEachIndexed { groupIndex, group ->
            val representativeIndex = selectRepresentativeIndex(group, candidates)
            Log.d(
                DEBUG_TAG,
                "group index=$groupIndex representative=$representativeIndex representativeScore=${candidates[representativeIndex].extractiveCandidate.score.format3()}"
            )
            group.sorted().forEach { candidateIndex ->
                val candidate = candidates[candidateIndex]
                Log.d(
                    DEBUG_TAG,
                    "group member group=$groupIndex index=$candidateIndex score=${candidate.extractiveCandidate.score.format3()} " +
                        "text=\"${candidate.extractiveCandidate.text.safeSnippet()}\""
                )
            }
        }
    }

    private fun Int.hasSimilarityToEverySentenceIn(
        group: Set<Int>,
        similarityMatrix: Array<FloatArray>
    ): Boolean {
        return group.all { memberIndex ->
            memberIndex == this || similarityMatrix[this][memberIndex] >= SummaryLimits.LocalSummary.nearDuplicateThreshold
        }
    }

    private fun Float.format3(): String = String.format(Locale.US, "%.3f", this)

    private fun Double.format3(): String = String.format(Locale.US, "%.3f", this)

    private fun String.safeSnippet(): String =
        replace('\n', ' ').replace(WHITESPACE_REGEX, " ").trim().take(DEBUG_TEXT_LIMIT)

    private data class SentenceCandidate(
        val extractiveCandidate: ExtractiveSentenceCandidate,
        val embedding: FloatArray?
    )

    private companion object {
        val WHITESPACE_REGEX = Regex("\\s+")
        const val DEBUG_TAG = "ExtractiveScoreDebug"
        const val DEBUG_TEXT_LIMIT = 180
    }
}
