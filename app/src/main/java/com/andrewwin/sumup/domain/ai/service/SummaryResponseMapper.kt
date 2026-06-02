package com.andrewwin.sumup.domain.ai.service

import com.andrewwin.sumup.domain.summary.model.SummaryResult

interface SummaryResponseMapper {
    fun parseSingle(jsonResponse: String, content: String): SummaryResult.Single
    fun parseCompare(jsonResponse: String, content: String): SummaryResult.Compare
    fun parseFeed(jsonResponse: String, content: String): SummaryResult.Digest
    fun parseQuestion(jsonResponse: String, content: String, question: String): SummaryResult.QA
}
