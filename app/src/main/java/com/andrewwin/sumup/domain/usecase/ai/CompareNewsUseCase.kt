package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.domain.ai.SummaryResponseMapper
import com.andrewwin.sumup.domain.ai.AiPromptBuilder
import com.andrewwin.sumup.domain.ai.AdaptiveTextShrinker
import com.andrewwin.sumup.domain.ai.AiRequestSender
import com.andrewwin.sumup.domain.ai.ProportionalTextLimiter
import com.andrewwin.sumup.domain.ai.LocalEmbeddingProvider
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.news.SimilarityScorer
import com.andrewwin.sumup.domain.summary.SummaryItem
import com.andrewwin.sumup.domain.summary.SummaryLimits
import com.andrewwin.sumup.domain.summary.SummaryResult
import com.andrewwin.sumup.domain.summary.SummarySourceRef
import com.andrewwin.sumup.domain.summary.ExtractiveSummaryService
import com.andrewwin.sumup.domain.support.DispatcherProvider
import com.andrewwin.sumup.domain.support.LocalModelMissingException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject


class CompareNewsUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val articleRepository: ArticleRepository,
    private val shrinkTextForAdaptiveStrategyUseCase: AdaptiveTextShrinker,
    private val aiRequestSender: AiRequestSender,
    private val summaryResponseMapper: SummaryResponseMapper,
    private val limitTextsProportionallyUseCase: ProportionalTextLimiter,
    private val getExtractiveSummaryUseCase: ExtractiveSummaryService,
    private val localEmbeddingProvider: LocalEmbeddingProvider,
    private val similarityScorer: SimilarityScorer,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(articles: List<Article>): Result<SummaryResult.Compare> = withContext(dispatcherProvider.default) {
        if (articles.size < 2) {
            return@withContext Result.failure(IllegalStateException("Недостатньо джерел для порівняння."))
        }

        val prefs = userPreferencesRepository.preferences.first()
        val strategy = prefs.aiStrategy

        // 1. Local Strategy
        if (strategy == AiStrategy.LOCAL) {
            return@withContext runCatching { performLocalComparison(articles, prefs) }
        }

        val cloudArticles = articles.map { article ->
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceName = source?.name?.trim()?.ifBlank { "Джерело" } ?: "Джерело"
            val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
            val fullContent = articleRepository.fetchFullContent(article)
            val contentToProcess = fullContent.ifBlank { article.content }

            CloudCompareArticle(
                id = article.id,
                sourceName = sourceName,
                sourceUrl = sourceUrl,
                title = article.title,
                content = contentToProcess
            )
        }
        val totalContentLength = cloudArticles.sumOf { it.content.length }

        if (strategy == AiStrategy.ADAPTIVE && totalContentLength < prefs.adaptiveExtractiveOnlyBelowChars) {
            return@withContext runCatching { performLocalComparison(articles, prefs) }
        }

        val processedTexts = cloudArticles.map { article ->
            if (strategy == AiStrategy.ADAPTIVE) {
                shrinkTextForAdaptiveStrategyUseCase.shrinkByAdaptiveRange(
                    text = article.content,
                    prefs = prefs,
                    rangeLength = totalContentLength
                )
            } else {
                article.content
            }
        }
        val limitedTexts = limitTextsProportionallyUseCase(
            texts = processedTexts,
            maxTotalChars = prefs.aiMaxCharsNewsCluster
        )

        val cloudInput = buildString {
            for ((article, textForCloud) in cloudArticles.zip(limitedTexts)) {
                append("source_id: ${article.id}\n")
                append("source_name: ${article.sourceName}\n")
                append("source_url: ${article.sourceUrl}\n")
                append("title: ${article.title}\n")
                append("content: $textForCloud\n\n")
            }
        }

        val customPrompt = prefs.summaryPrompt.takeIf { prefs.isCustomSummaryPromptEnabled }
        val prompt = AiPromptBuilder.buildComparePrompt(prefs.summaryLanguage, customPrompt)

        val cloudResult = runCatching {
            val jsonResponse = aiRequestSender.sendSummaryRequest(prompt, cloudInput)
            summaryResponseMapper.parseCompare(jsonResponse, cloudInput)
        }

        return@withContext if (strategy == AiStrategy.ADAPTIVE) {
            cloudResult.recoverCatching { performLocalComparison(articles, prefs) }
        } else {
            cloudResult
        }
    }

    private data class CloudCompareArticle(
        val id: Long,
        val sourceName: String,
        val sourceUrl: String,
        val title: String,
        val content: String
    )

    private data class LocalClusterSentenceCandidate(
        val text: String,
        val source: SummarySourceRef,
        val articleId: Long
    )

    private suspend fun performLocalComparison(articles: List<Article>, prefs: com.andrewwin.sumup.data.local.entities.UserPreferences): SummaryResult.Compare {
        if (articles.isEmpty()) return SummaryResult.Compare(points = emptyList())

        if (!localEmbeddingProvider.initialize()) {
            throw LocalModelMissingException()
        }

        val candidatesByArticle = articles.associate { article ->
            article.id to buildLocalClusterSentenceCandidates(article)
        }

        val requiredSourceItems = buildRequiredLocalClusterSummaryItems(
            articles = articles,
            candidatesByArticle = candidatesByArticle
        )
        val selectedItems = requiredSourceItems.toMutableList()
        val selectedTexts = requiredSourceItems.map { it.text }.toMutableList()

        val remainingSlots = (SummaryLimits.LocalClusterSummary.maxSummarySentences - selectedItems.size)
            .coerceAtLeast(0)
        if (remainingSlots > 0) {
            val remainingCandidates = articles
                .flatMap { article -> candidatesByArticle[article.id].orEmpty() }
                .filterNot { candidate -> selectedTexts.any { it == candidate.text } }

            for (candidate in remainingCandidates) {
                if (selectedItems.size >= SummaryLimits.LocalClusterSummary.maxSummarySentences) break
                if (isNearDuplicateOfSelected(candidate.text, selectedTexts)) continue

                selectedItems += SummaryItem(
                    text = candidate.text,
                    sources = listOf(candidate.source)
                )
                selectedTexts += candidate.text
            }
        }

        return SummaryResult.Compare(
            main = selectedItems.firstOrNull()?.text,
            points = selectedItems.drop(SummaryLimits.Compare.mainSentences)
        )
    }

    private suspend fun buildLocalClusterSentenceCandidates(article: Article): List<LocalClusterSentenceCandidate> {
        val source = articleRepository.getSourceById(article.sourceId)
        val sourceName = source?.name?.trim()?.ifBlank { "Джерело" } ?: "Джерело"
        val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
        val sourceMeta = SummarySourceRef(name = sourceName, url = sourceUrl)

        val fullContent = articleRepository.fetchFullContent(article)
        return getExtractiveSummaryUseCase(
            fullContent,
            SummaryLimits.LocalClusterSummary.candidateSentencesPerSource
        )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .map { sentence ->
                LocalClusterSentenceCandidate(
                    text = sentence,
                    source = sourceMeta,
                    articleId = article.id
                )
            }
    }

    private suspend fun buildRequiredLocalClusterSummaryItems(
        articles: List<Article>,
        candidatesByArticle: Map<Long, List<LocalClusterSentenceCandidate>>
    ): List<SummaryItem> {
        val maxRequiredSources = SummaryLimits.LocalClusterSummary.maxSummarySentences /
            SummaryLimits.LocalClusterSummary.minSentencesPerSource
        val selectedTexts = mutableListOf<String>()
        val items = mutableListOf<SummaryItem>()

        for (article in articles.take(maxRequiredSources)) {
            val articleCandidates = candidatesByArticle[article.id].orEmpty()
            val selectedCandidate = articleCandidates.firstOrNull { candidate ->
                !isNearDuplicateOfSelected(candidate.text, selectedTexts)
            } ?: articleCandidates.firstOrNull()

            if (selectedCandidate != null) {
                items += SummaryItem(
                    text = selectedCandidate.text,
                    sources = listOf(selectedCandidate.source)
                )
                selectedTexts += selectedCandidate.text
            }
        }

        return items
    }

    private suspend fun isNearDuplicateOfSelected(
        candidateText: String,
        selectedTexts: List<String>
    ): Boolean {
        if (selectedTexts.isEmpty()) return false
        val candidateEmbedding = localEmbeddingProvider.computeLocalEmbedding(candidateText)
        if (candidateEmbedding.isEmpty()) return selectedTexts.any { it == candidateText }

        return selectedTexts.any { selectedText ->
            val selectedEmbedding = localEmbeddingProvider.computeLocalEmbedding(selectedText)
            selectedEmbedding.isNotEmpty() &&
                similarityScorer.calculateSimilarity(candidateEmbedding, selectedEmbedding) >=
                SummaryLimits.LocalClusterSummary.nearDuplicateThreshold
        }
    }
}
