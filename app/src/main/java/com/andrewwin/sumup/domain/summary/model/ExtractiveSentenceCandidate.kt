package com.andrewwin.sumup.domain.summary.model

data class ExtractiveSentenceCandidate(
    val text: String,
    val originalIndex: Int,
    val score: Double
)