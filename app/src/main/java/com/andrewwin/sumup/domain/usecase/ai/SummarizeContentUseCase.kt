package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.ExtractiveSummarizer
import com.andrewwin.sumup.domain.summary.SummarySourceMeta
import com.andrewwin.sumup.domain.usecase.FormatArticleHeadlineUseCase
import com.andrewwin.sumup.domain.usecase.BuildExtractiveSummaryUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SummarizeContentUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val articleRepository: ArticleRepository,
    private val formatExtractiveSummaryUseCase: FormatExtractiveSummaryUseCase,
    private val formatArticleHeadlineUseCase: FormatArticleHeadlineUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val buildExtractiveSummaryUseCase: BuildExtractiveSummaryUseCase
) {
    suspend operator fun invoke(article: Article): Result<String> {
        return try {
            val prefs = userPreferencesRepository.preferences.first()
            val fullContent = articleRepository.fetchFullContent(article)
            
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceType = source?.type ?: SourceType.RSS
            val formattedHeadline = formatArticleHeadlineUseCase(article, sourceType)

            if (prefs.aiStrategy == AiStrategy.LOCAL) {
                val sentences = ExtractiveSummarizer.summarize(fullContent, prefs.summaryItemsPerNewsInFeed)
                val formatted = formatExtractiveSummaryUseCase.formatItem(
                    title = formattedHeadline.displayTitle,
                    sentences = sentences,
                    isScheduledReport = false
                )
                return Result.success(
                    appendSourceMetadata(
                        summaryText = formatted,
                        articles = listOf(article)
                    )
                )
            }

            val contentForAi = buildString {
                append(formattedHeadline.displayTitle)
                append(": ")
                append(if (formattedHeadline.displayContent.isNotBlank()) formattedHeadline.displayContent else fullContent)
            }

            val summary = aiRepository.summarize(
                content = contentForAi,
                pointsPerNews = prefs.summaryItemsPerNewsInFeed
            )
            Result.success(
                appendSourceMetadata(
                    summaryText = summary,
                    articles = listOf(article)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend operator fun invoke(articles: List<Article>): Result<String> {
        return try {
            val prefs = userPreferencesRepository.preferences.first()
            
            if (prefs.aiStrategy == AiStrategy.LOCAL) {
                val extractiveArticles = articles.take(prefs.summaryNewsInFeedExtractive.coerceAtLeast(1))
                val fullContentMap = mutableMapOf<String, String>()
                for (article in extractiveArticles) {
                    val source = articleRepository.getSourceById(article.sourceId)
                    val sourceType = source?.type ?: SourceType.RSS
                    val formatted = formatArticleHeadlineUseCase(article, sourceType)
                    fullContentMap[formatted.displayTitle] = articleRepository.fetchFullContent(article)
                }
                
                val summary = buildExtractiveSummaryUseCase(
                    headlines = fullContentMap.keys.toList(),
                    contentMap = fullContentMap,
                    topCount = prefs.summaryNewsInFeedExtractive,
                    sentencesPerArticle = prefs.summaryItemsPerNewsInFeed
                )
                return Result.success(
                    appendSourceMetadata(
                        summaryText = summary,
                        articles = extractiveArticles
                    )
                )
            }

            val cloudArticles = articles.take(prefs.summaryNewsInFeedCloud.coerceAtLeast(1))
            val contentBuilder = StringBuilder()
            val perArticleLimit = prefs.aiMaxCharsPerFeedArticle.coerceAtLeast(200)
            for (article in cloudArticles) {
                val source = articleRepository.getSourceById(article.sourceId)
                val sourceType = source?.type ?: SourceType.RSS
                val formatted = formatArticleHeadlineUseCase(article, sourceType)
                if (contentBuilder.isNotEmpty()) contentBuilder.append("\n\n")
                
                val fullContent = articleRepository.fetchFullContent(article)
                val rawContent = fullContent
                contentBuilder.append("${formatted.displayTitle}: ${rawContent.take(perArticleLimit)}")
            }
            
            val summary = aiRepository.summarize(
                content = contentBuilder.toString(),
                pointsPerNews = prefs.summaryItemsPerNewsInFeed
            )
            Result.success(
                appendSourceMetadata(
                    summaryText = summary,
                    articles = cloudArticles
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend operator fun invoke(content: String): Result<String> {
        return try {
            val prefs = userPreferencesRepository.preferences.first()
            Result.success(
                aiRepository.summarize(
                    content = content,
                    pointsPerNews = prefs.summaryItemsPerNewsInFeed
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun appendSourceMetadata(
        summaryText: String,
        articles: List<Article>
    ): String {
        if (summaryText.isBlank() || articles.isEmpty()) return summaryText
        data class SourceMeta(val titleKey: String, val sourceName: String, val sourceUrl: String)
        val metas = mutableListOf<SourceMeta>()
        for (article in articles) {
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceName = source?.name?.trim().orEmpty()
            val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
            if (sourceName.isBlank() || sourceUrl.isBlank()) continue
            val formatted = formatArticleHeadlineUseCase(article, source?.type ?: SourceType.RSS)
            metas.add(SourceMeta(normalizeKey(formatted.displayTitle), sourceName, sourceUrl))
        }
        if (metas.isEmpty()) return summaryText

        val sections = summaryText.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
        if (sections.isEmpty()) return summaryText

        val used = BooleanArray(metas.size)
        var fallbackIndex = 0
        val enrichedSections = sections.map { section ->
            val lines = section.lines().map { it.trimEnd() }
            if (lines.any { it.startsWith(SummarySourceMeta.PREFIX) }) return@map section
            val titleLine = lines.firstOrNull()?.trim().orEmpty()
            val titleKey = normalizeKey(titleLine.removeSuffix(":"))
            var exactIndex = -1
            for (i in metas.indices) {
                if (used[i]) continue
                if (metas[i].titleKey == titleKey) {
                    exactIndex = i
                    break
                }
            }
            val pickedIndex = if (exactIndex >= 0) {
                exactIndex
            } else {
                while (fallbackIndex < metas.size && used[fallbackIndex]) fallbackIndex++
                if (fallbackIndex < metas.size) fallbackIndex else -1
            }
            if (pickedIndex == -1) return@map section
            used[pickedIndex] = true
            val meta = metas[pickedIndex]
            "$section\n${SummarySourceMeta.PREFIX}${meta.sourceName}|${meta.sourceUrl}"
        }
        return enrichedSections.joinToString("\n\n")
    }

    private fun normalizeKey(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
