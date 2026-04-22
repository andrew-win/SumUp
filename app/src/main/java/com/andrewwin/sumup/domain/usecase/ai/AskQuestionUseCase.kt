package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.support.SummarySourceMeta
import javax.inject.Inject

class AskQuestionUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val articleRepository: ArticleRepository
) {
    suspend operator fun invoke(article: Article, question: String): Result<String> {
        return try {
            val fullContent = articleRepository.fetchFullContent(article)
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceName = source?.name?.trim().orEmpty()
            val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
            val structuredContent = buildStructuredArticleContent(
                sourceId = article.id.toString(),
                sourceName = sourceName,
                sourceUrl = sourceUrl,
                title = article.title,
                content = fullContent
            )
            val raw = aiRepository.askQuestion(structuredContent, question)
            Result.success(
                formatQaJson(
                    raw = raw,
                    sourceRefs = extractSourceRefs(structuredContent)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend operator fun invoke(content: String, question: String): Result<String> {
        return try {
            val raw = aiRepository.askQuestion(content, question)
            Result.success(
                formatQaJson(
                    raw = raw,
                    sourceRefs = extractSourceRefs(content)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatQaJson(
        raw: String,
        sourceRefs: Map<String, QaSourceRef>
    ): String {
        val qa = AiJsonResponseParser.parseQa(raw)
        val sections = mutableListOf<String>()

        qa.answer?.takeIf { it.isNotBlank() }?.let { sections += it.trim() }

        if (qa.statements.isNotEmpty()) {
            val lines = mutableListOf<String>()
            val fallbackSourceIds = qa.sources
                .map { it.trim() }
                .filter { it.isNotBlank() }
            qa.statements.forEach { statement ->
                lines += "• ${statement.text.trim()}"
                val citedSourceId = statement.sources
                    .asSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotBlank() && sourceRefs.containsKey(it) }
                    ?: fallbackSourceIds.firstOrNull { sourceRefs.containsKey(it) }

                citedSourceId
                    ?.let(sourceRefs::get)
                    ?.also { ref ->
                        lines += "${SummarySourceMeta.PREFIX}${ref.name}|${ref.url}"
                    }
            }
            sections += lines.joinToString("\n")
        }

        if (sections.isEmpty()) throw IllegalStateException("QA JSON contains no answer.")
        return sections.joinToString("\n\n")
    }

    private fun buildStructuredArticleContent(
        sourceId: String,
        sourceName: String,
        sourceUrl: String,
        title: String,
        content: String
    ): String {
        return buildString {
            append("source_id: ").append(sourceId).append('\n')
            if (sourceName.isNotBlank()) {
                append("source_name: ").append(sourceName).append('\n')
            }
            if (sourceUrl.isNotBlank()) {
                append("source_url: ").append(sourceUrl).append('\n')
            }
            append("title: ").append(title.trim()).append('\n')
            append("content: ").append(content.trim())
        }
    }

    private fun extractSourceRefs(content: String): Map<String, QaSourceRef> {
        return content
            .split(Regex("\\n\\s*\\n"))
            .mapNotNull { block ->
                var sourceId: String? = null
                var sourceName: String? = null
                var sourceUrl: String? = null

                block.lineSequence().forEach { rawLine ->
                    val line = rawLine.trim()
                    when {
                        line.startsWith("source_id:", ignoreCase = true) -> {
                            sourceId = line.substringAfter(':').trim().ifBlank { null }
                        }
                        line.startsWith("source_name:", ignoreCase = true) -> {
                            sourceName = line.substringAfter(':').trim().ifBlank { null }
                        }
                        line.startsWith("source_url:", ignoreCase = true) -> {
                            sourceUrl = line.substringAfter(':').trim().ifBlank { null }
                        }
                    }
                }

                val id = sourceId ?: return@mapNotNull null
                val url = sourceUrl ?: return@mapNotNull null
                val name = sourceName?.takeIf { it.isNotBlank() } ?: url
                QaSourceRef(
                    id = id,
                    name = name,
                    url = url
                )
            }
            .associateBy { it.id }
    }

    private data class QaSourceRef(
        val id: String,
        val name: String,
        val url: String
    )
}









