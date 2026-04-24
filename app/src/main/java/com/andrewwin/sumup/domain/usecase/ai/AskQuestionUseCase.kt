package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.support.DebugTrace
import com.andrewwin.sumup.domain.support.SummarySourceMeta
import javax.inject.Inject

class AskQuestionUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val articleRepository: ArticleRepository
) {
    suspend operator fun invoke(articles: List<Article>, question: String): Result<String> {
        return try {
            val structuredContent = buildString {
                articles.distinctBy { it.id }.forEachIndexed { index, article ->
                    val fullContent = articleRepository.fetchFullContent(article)
                    val source = articleRepository.getSourceById(article.sourceId)
                    val sourceName = source?.name?.trim().orEmpty()
                    val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
                    if (index > 0) append("\n\n")
                    append(
                        buildStructuredArticleContent(
                            sourceId = article.id.toString(),
                            sourceName = sourceName,
                            sourceUrl = sourceUrl,
                            title = article.title,
                            content = fullContent
                        )
                    )
                }
            }
            DebugTrace.d(
                "ai_qa",
                "invoke(list) question=${DebugTrace.preview(question, 140)} articles=${articles.size} payloadChars=${structuredContent.length} payloadPreview=${DebugTrace.preview(structuredContent, 500)}"
            )
            val raw = aiRepository.askQuestion(structuredContent, question)
            DebugTrace.d("ai_qa", "rawResponse(list) preview=${DebugTrace.preview(raw, 500)}")
            Result.success(
                formatQaJson(
                    raw = raw,
                    sourceRefs = extractSourceRefs(structuredContent),
                    askedQuestion = question
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
            DebugTrace.d(
                "ai_qa",
                "invoke(single) articleId=${article.id} question=${DebugTrace.preview(question, 140)} payloadChars=${structuredContent.length} payloadPreview=${DebugTrace.preview(structuredContent, 500)}"
            )
            val raw = aiRepository.askQuestion(structuredContent, question)
            DebugTrace.d("ai_qa", "rawResponse(single) preview=${DebugTrace.preview(raw, 500)}")
            Result.success(
                formatQaJson(
                    raw = raw,
                    sourceRefs = extractSourceRefs(structuredContent),
                    askedQuestion = question
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend operator fun invoke(content: String, question: String): Result<String> {
        return try {
            DebugTrace.d(
                "ai_qa",
                "invoke(content) question=${DebugTrace.preview(question, 140)} payloadChars=${content.length} payloadPreview=${DebugTrace.preview(content, 500)}"
            )
            val raw = aiRepository.askQuestion(content, question)
            DebugTrace.d("ai_qa", "rawResponse(content) preview=${DebugTrace.preview(raw, 500)}")
            Result.success(
                formatQaJson(
                    raw = raw,
                    sourceRefs = extractSourceRefs(content),
                    askedQuestion = question
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatQaJson(
        raw: String,
        sourceRefs: Map<String, QaSourceRef>,
        askedQuestion: String
    ): String {
        val qa = AiJsonResponseParser.parseQa(raw)
        val orderedSourceRefs = sourceRefs.values.toList()
        val safeQuestion = qa.question?.trim().takeUnless { it.isNullOrBlank() } ?: askedQuestion.trim()
        val shortAnswer = qa.shortAnswer?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: qa.answer?.trim().takeUnless { it.isNullOrBlank() }
            ?: "Недостатньо даних."

        val detailItems = (if (qa.details.isNotEmpty()) qa.details else qa.statements)
            .mapNotNull { statement ->
                val text = statement.text.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                text to statement.sources
            }

        val detailLines = mutableListOf<String>()
        if (detailItems.isEmpty()) {
            detailLines += "— У джерелах немає достатнього підтвердження для детального пояснення."
        } else {
            val fallbackSourceIds = qa.sources
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toList()

            detailItems.forEach { (text, sourceIds) ->
                detailLines += "— $text"
                val referenced = sourceIds
                    .asSequence()
                    .mapNotNull { resolveQaSourceRef(it, sourceRefs, orderedSourceRefs) }
                    .ifEmpty {
                        fallbackSourceIds
                            .asSequence()
                            .mapNotNull { resolveQaSourceRef(it, sourceRefs, orderedSourceRefs) }
                    }
                    .distinct()
                    .toList()
                DebugTrace.d(
                    "ai_qa",
                    "detailResolve text=${DebugTrace.preview(text, 120)} requested=${sourceIds.joinToString(",")} fallback=${fallbackSourceIds.joinToString(",")} resolved=${referenced.joinToString(",") { it.id }}"
                )

                referenced.forEach { ref ->
                    detailLines += "${SummarySourceMeta.PREFIX}${ref.name}|${ref.url}"
                }
            }
        }

        val formatted = buildString {
            appendLine("Питання")
            appendLine("— $safeQuestion")
            appendLine()
            appendLine("Коротка відповідь")
            appendLine("— $shortAnswer")
            appendLine()
            appendLine("Детальніше")
            detailLines.forEach { appendLine(it) }
        }.trim()

        DebugTrace.d(
            "ai_qa",
            "formatQaJson parsed question=${DebugTrace.preview(safeQuestion, 140)} short=${DebugTrace.preview(shortAnswer, 160)} detailItems=${detailItems.size} sourceRefs=${sourceRefs.keys.joinToString(",")} formattedPreview=${DebugTrace.preview(formatted, 500)}"
        )

        return formatted
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

    private fun resolveQaSourceRef(
        rawRef: String,
        sourceRefs: Map<String, QaSourceRef>,
        orderedSourceRefs: List<QaSourceRef>
    ): QaSourceRef? {
        val normalized = rawRef.trim()
        if (normalized.isBlank()) return null

        sourceRefs[normalized]?.let { return it }

        val digits = Regex("\\d+").find(normalized)?.value?.toLongOrNull()
        if (digits != null) {
            sourceRefs[digits.toString()]?.let { return it }

            // Some model responses return ordinal refs like s1/source_1.
            val ordinalIndex = (digits - 1L).toInt()
            if (ordinalIndex in orderedSourceRefs.indices) {
                return orderedSourceRefs[ordinalIndex]
            }
        }

        return null
    }

    private data class QaSourceRef(
        val id: String,
        val name: String,
        val url: String
    )
}









