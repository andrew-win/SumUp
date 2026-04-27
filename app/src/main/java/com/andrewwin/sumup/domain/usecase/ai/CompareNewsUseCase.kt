package com.andrewwin.sumup.domain.usecase.ai

import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.DeduplicationStrategy
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.service.EmbeddingUtils
import com.andrewwin.sumup.domain.service.SimilarityScorer
import com.andrewwin.sumup.domain.support.DispatcherProvider
import com.andrewwin.sumup.domain.support.LocalModelMissingException
import com.andrewwin.sumup.domain.usecase.common.GetExtractiveSummaryUseCase
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject


class CompareNewsUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val articleRepository: ArticleRepository,
    private val shrinkTextForAdaptiveStrategyUseCase: ShrinkTextForAdaptiveStrategyUseCase,
    private val sendCloudAiRequestUseCase: SendCloudAiRequestUseCase,
    private val parseAiJsonResponseUseCase: ParseAiJsonResponseUseCase,
    private val generateLocalEmbeddingUseCase: GenerateLocalEmbeddingUseCase,
    private val getExtractiveSummaryUseCase: GetExtractiveSummaryUseCase,
    private val similarityScorer: SimilarityScorer,
    private val manageModelUseCase: ManageModelUseCase,
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

        // 2. Cloud or Adaptive
        val cloudInput = buildString {
            for (article in articles) {
                val source = articleRepository.getSourceById(article.sourceId)
                val sourceName = source?.name?.trim()?.ifBlank { "Джерело" } ?: "Джерело"
                val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
                
                val fullContent = articleRepository.fetchFullContent(article)
                val contentToProcess = fullContent.ifBlank { article.content }
                
                val textForCloud = if (strategy == AiStrategy.ADAPTIVE) {
                    shrinkTextForAdaptiveStrategyUseCase(contentToProcess, prefs)
                } else {
                    contentToProcess.take(prefs.aiMaxCharsPerArticle)
                }

                append("source_id: ${article.id}\n")
                append("source_name: $sourceName\n")
                append("source_url: $sourceUrl\n")
                append("title: ${article.title}\n")
                append("content: $textForCloud\n\n")
            }
        }

        val prompt = AiPromptBuilder.buildComparePrompt(prefs.summaryLanguage)

        return@withContext runCatching {
            val jsonResponse = sendCloudAiRequestUseCase(prompt, cloudInput)
            parseAiJsonResponseUseCase.parseCompare(jsonResponse, cloudInput)
        }.recoverCatching {
            performLocalComparison(articles, prefs)
        }
    }

    private data class CompareCandidate(
        val text: String,
        val source: SummarySourceRef,
        val articleId: Long
    )

    private suspend fun performLocalComparison(articles: List<Article>, prefs: com.andrewwin.sumup.data.local.entities.UserPreferences): SummaryResult.Compare {
        if (articles.isEmpty()) return SummaryResult.Compare(emptyList(), emptyList())

        if (!manageModelUseCase.isModelExists()) {
            throw LocalModelMissingException()
        }

        val candidates = mutableListOf<CompareCandidate>()
        
        for (article in articles) {
            val source = articleRepository.getSourceById(article.sourceId)
            val sourceName = source?.name?.trim()?.ifBlank { "Джерело" } ?: "Джерело"
            val sourceUrl = article.url.takeIf { it.isNotBlank() } ?: source?.url.orEmpty()
            val sourceMeta = SummarySourceRef(name = sourceName, url = sourceUrl)
            
            val fullContent = articleRepository.fetchFullContent(article)
            // Збільшуємо кількість речень для аналізу, щоб знайти унікальні деталі
            val sentences = getExtractiveSummaryUseCase(fullContent, SummaryLimits.Compare.sentencesLocalCompare)
            val prepared = sentences.map { it.trim() }.filter { it.isNotBlank() }
            prepared.forEach { sentence ->
                candidates += CompareCandidate(text = sentence, source = sourceMeta, articleId = article.id)
            }
        }

        if (candidates.isEmpty()) return SummaryResult.Compare(emptyList(), emptyList())

        val embeddings = candidates.map { candidate ->
            generateLocalEmbeddingUseCase(candidate.text) ?: FloatArray(EmbeddingUtils.EMBEDDING_DIM)
        }
        val featuresCache = candidates.map { EmbeddingUtils.extractTextFeatures(it.text) }

        // Попередньо розраховуємо найкращий бал схожості з іншими статтями для кожного речення
        val bestScoreToOtherArticle = FloatArray(candidates.size) { 0f }
        val threshold = SummaryLimits.Compare.localSimilarityThreshold

        // Сортуємо кандидатів за довжиною, щоб довші речення ставали лідерами кластерів
        val sortedIndices = candidates.indices.sortedByDescending { candidates[it].text.length }
        
        val visited = BooleanArray(candidates.size)
        val clusters = mutableListOf<List<Int>>()

        for (leaderIdx in sortedIndices) {
            if (visited[leaderIdx]) continue
            
            val cluster = mutableListOf<Int>()
            cluster.add(leaderIdx)
            visited[leaderIdx] = true

            val leaderEmb = embeddings[leaderIdx]
            val leaderFeatures = featuresCache[leaderIdx]
            val leaderArticleId = candidates[leaderIdx].articleId

            for (memberIdx in sortedIndices) {
                if (visited[memberIdx]) continue
                
                // Порівнюємо лише з РІЗНИХ статей
                if (candidates[memberIdx].articleId == leaderArticleId) continue

                val score = similarityScorer.calculateSimilarity(
                    articleA = Article(sourceId = 0, title = "", content = "", url = "", publishedAt = 0),
                    embeddingA = leaderEmb,
                    articleB = Article(sourceId = 0, title = "", content = "", url = "", publishedAt = 0),
                    embeddingB = embeddings[memberIdx],
                    strategy = DeduplicationStrategy.LOCAL,
                    featuresA = leaderFeatures,
                    featuresB = featuresCache[memberIdx]
                )

                // Оновлюємо глобальний бал схожості для обох речень
                bestScoreToOtherArticle[leaderIdx] = maxOf(bestScoreToOtherArticle[leaderIdx], score)
                bestScoreToOtherArticle[memberIdx] = maxOf(bestScoreToOtherArticle[memberIdx], score)

                if (score >= threshold) {
                    cluster.add(memberIdx)
                    visited[memberIdx] = true
                }
            }
            clusters.add(cluster)
        }

        val commonCandidates = mutableListOf<Pair<SummaryItem, Float>>()
        val uniqueCandidates = mutableListOf<Pair<SummaryItem, Float>>()

        for (cluster in clusters) {
            val leaderIdx = cluster.first()
            val clusterSources = cluster.map { candidates[it].source }.distinctBy { it.url }
            val distinctArticlesInCluster = cluster.map { candidates[it].articleId }.distinct().size
            
            val item = SummaryItem(text = candidates[leaderIdx].text.trim(), sources = clusterSources)
            // Беремо максимальний бал схожості лідера з будь-якою іншою СТАТТЕЮ (не тільки всередині кластера)
            val clusterMaxScore = bestScoreToOtherArticle[leaderIdx]

            if (distinctArticlesInCluster > 1) {
                commonCandidates.add(item to clusterMaxScore)
            } else {
                uniqueCandidates.add(item to clusterMaxScore)
            }
        }

        // Спільне: Топ за найбільшими балами схожості
        val commonItems = commonCandidates
            .sortedByDescending { it.second }
            .map { it.first }
            .take(SummaryLimits.Compare.maxCommon)

        // Унікальне: Топ за найменшими балами схожості (найбільш відмінні від інших статей речення)
        val differentItems = uniqueCandidates
            .sortedBy { it.second }
            .map { it.first }
            .take(SummaryLimits.Compare.maxUnique)

        if (commonItems.isEmpty()) {
            val fallbackItem = buildCentralFallbackItem(candidates)
            return SummaryResult.Compare(
                common = listOf(fallbackItem),
                unique = differentItems
            )
        }

        return SummaryResult.Compare(
            common = commonItems,
            unique = differentItems
        )
    }

    private fun normalizeKey(value: String): String = value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun buildCentralFallbackItem(candidates: List<CompareCandidate>): SummaryItem {
        if (candidates.isEmpty()) {
            return SummaryItem(text = "Немає достатньо даних.", sources = emptyList())
        }
        val normalizedFrequencies = candidates
            .groupingBy { normalizeKey(it.text) }
            .eachCount()
        val selectedText = candidates
            .asSequence()
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
            .maxWithOrNull(
                compareBy<String> { normalizedFrequencies[normalizeKey(it)] ?: 0 }
                    .thenBy { it.length }
            )
            ?: "Немає достатньо даних."
        return SummaryItem(
            text = selectedText,
            sources = candidates.map { it.source }.distinctBy { it.url }
        )
    }
}
