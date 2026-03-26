package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.summary.SummarySourceMeta
import javax.inject.Inject

class AskQuestionUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val articleRepository: ArticleRepository
) {
    suspend operator fun invoke(article: Article, question: String): Result<String> {
        return try {
            val fullContent = articleRepository.fetchFullContent(article)
            val raw = aiRepository.askQuestion(fullContent, question)
            Result.success(formatQaJson(raw))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend operator fun invoke(content: String, question: String): Result<String> {
        return try {
            val raw = aiRepository.askQuestion(content, question)
            Result.success(formatQaJson(raw))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatQaJson(raw: String): String {
        val qa = AiJsonResponseParser.parseQa(raw)
        val sections = mutableListOf<String>()

        qa.answer?.takeIf { it.isNotBlank() }?.let { sections += it.trim() }

        if (qa.statements.isNotEmpty()) {
            val lines = mutableListOf<String>()
            val sourceUrls = linkedSetOf<String>()
            qa.statements.forEach { statement ->
                lines += "• ${statement.text.trim()}"
                statement.sources.forEach { source ->
                    val value = source.trim()
                    if (value.startsWith("http://") || value.startsWith("https://")) {
                        sourceUrls += value
                    }
                }
            }
            sourceUrls.forEach { url ->
                lines += "${SummarySourceMeta.PREFIX}$url|$url"
            }
            sections += lines.joinToString("\n")
        }

        if (sections.isEmpty()) throw IllegalStateException("QA JSON contains no answer.")
        return sections.joinToString("\n\n")
    }
}
