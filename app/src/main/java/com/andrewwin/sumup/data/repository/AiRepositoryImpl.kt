package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.local.dao.AiModelDao
import com.andrewwin.sumup.data.local.dao.UserPreferencesDao
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.remote.AiService
import com.andrewwin.sumup.domain.ExtractiveSummarizer
import com.andrewwin.sumup.domain.exception.NoActiveModelException
import com.andrewwin.sumup.domain.exception.UnsupportedStrategyException
import com.andrewwin.sumup.domain.provider.AiPromptProvider
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.usecase.ai.AiJsonResponseParser
import com.andrewwin.sumup.domain.usecase.ai.FormatExtractiveSummaryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AiRepositoryImpl @Inject constructor(
    private val aiModelDao: AiModelDao,
    private val prefsDao: UserPreferencesDao,
    private val aiService: AiService,
    private val formatExtractiveSummaryUseCase: FormatExtractiveSummaryUseCase,
    private val aiPromptProvider: AiPromptProvider
) : AiRepository {
    private val bulletSymbol = "•"

    override val allConfigs: Flow<List<AiModelConfig>> = aiModelDao.getAllConfigs()

    override fun getConfigsByType(type: AiModelType): Flow<List<AiModelConfig>> =
        aiModelDao.getConfigsByType(type)

    override suspend fun fetchAvailableModels(provider: AiProvider, apiKey: String, type: AiModelType): List<String> =
        aiService.fetchModels(provider, apiKey, type)

    override suspend fun addConfig(config: AiModelConfig) = aiModelDao.insertConfig(config)
    override suspend fun updateConfig(config: AiModelConfig) = aiModelDao.updateConfig(config)
    override suspend fun deleteConfig(config: AiModelConfig) = aiModelDao.deleteConfig(config)

    override suspend fun summarize(content: String, pointsPerNews: Int?): String {
        val prefs = prefsDao.getUserPreferences().first()
        val strategy = prefs?.aiStrategy ?: AiStrategy.ADAPTIVE
        val maxTotalChars = (prefs?.aiMaxCharsTotal ?: DEFAULT_MAX_AI_CONTENT_LENGTH).coerceAtLeast(1000)
        val summaryPointsPerNews = pointsPerNews
            ?: (prefs?.summaryItemsPerNewsInScheduled ?: DEFAULT_SUMMARY_POINTS_PER_NEWS)
        val truncatedContent = content.take(maxTotalChars)
        val adaptiveExtractiveOnlyBelowChars =
            (prefs?.adaptiveExtractiveOnlyBelowChars ?: DEFAULT_ADAPTIVE_EXTRACTIVE_ONLY_BELOW_CHARS).coerceAtLeast(1)
        val adaptiveExtractiveCompressAboveChars =
            (prefs?.adaptiveExtractiveCompressAboveChars ?: DEFAULT_ADAPTIVE_EXTRACTIVE_COMPRESS_ABOVE_CHARS).coerceAtLeast(1)
        val adaptiveExtractiveCompressionPercent =
            (prefs?.adaptiveExtractiveCompressionPercent ?: DEFAULT_ADAPTIVE_EXTRACTIVE_COMPRESSION_PERCENT).coerceIn(1, 100)

        if (strategy == AiStrategy.LOCAL) {
            return formatExtractiveFallback(truncatedContent, summaryPointsPerNews)
        }

        val enabledConfigs = aiModelDao.getEnabledConfigsByType(AiModelType.SUMMARY)
        if (strategy == AiStrategy.ADAPTIVE && truncatedContent.length < adaptiveExtractiveOnlyBelowChars) {
            return formatExtractiveFallback(truncatedContent, summaryPointsPerNews)
        }
        if (enabledConfigs.isEmpty()) {
            return formatExtractiveFallback(truncatedContent, summaryPointsPerNews)
        }

        val cloudInput = if (strategy == AiStrategy.ADAPTIVE) {
            if (truncatedContent.length > adaptiveExtractiveCompressAboveChars) {
                val extractiveChars =
                    (truncatedContent.length * (adaptiveExtractiveCompressionPercent.toFloat() / 100f)).toInt().coerceAtLeast(1)
                val extractiveSentenceEstimate = estimateSentenceCountByTargetLength(
                    content = truncatedContent,
                    targetChars = extractiveChars,
                    defaultSentenceCount = summaryPointsPerNews
                )
                ExtractiveSummarizer
                    .summarize(truncatedContent, extractiveSentenceEstimate)
                    .joinToString(" ")
            } else {
                truncatedContent
            }
        } else {
            truncatedContent
        }

        for (config in enabledConfigs) {
            try {
                val prompt = if (prefs?.isCustomSummaryPromptEnabled == true) {
                    prefs.summaryPrompt.ifBlank { aiPromptProvider.defaultSummaryPrompt() }
                } else {
                    aiPromptProvider.defaultSummaryPrompt()
                }
                val strengthenedPrompt = buildSummaryPrompt(prompt, aiPromptProvider.strictJsonInstruction())

                val response = aiService.generateResponse(config, strengthenedPrompt, cloudInput)
                return runCatching {
                    formatCloudSummary(response, truncatedContent, summaryPointsPerNews)
                }.getOrElse {
                    continue
                }
            } catch (e: com.andrewwin.sumup.domain.exception.AiServiceException) {
                continue
            } catch (e: Exception) {
                throw e
            }
        }

        return formatExtractiveFallback(truncatedContent, summaryPointsPerNews)
    }

    override suspend fun askQuestion(content: String, question: String): String {
        val prefs = prefsDao.getUserPreferences().first()
        val maxTotalChars = (prefs?.aiMaxCharsTotal ?: DEFAULT_MAX_AI_CONTENT_LENGTH).coerceAtLeast(1000)
        
        if (prefs?.aiStrategy == AiStrategy.LOCAL) {
            throw UnsupportedStrategyException()
        }

        val enabledConfigs = aiModelDao.getEnabledConfigsByType(AiModelType.SUMMARY)
        if (enabledConfigs.isEmpty()) throw NoActiveModelException()

        for (config in enabledConfigs) {
            try {
                val prompt = buildString {
                    append(aiPromptProvider.questionPromptPrefix())
                    append(' ')
                    append(question)
                    append("\n\n")
                    append(aiPromptProvider.questionPromptSuffix())
                    append("\n\n")
                    append(aiPromptProvider.strictJsonInstruction())
                    append("\n")
                    append("Return JSON object with fields: answer (string), statements (array of {text, sources}), sources (array).")
                }
                return aiService.generateResponse(config, prompt, content.take(maxTotalChars))
            } catch (e: com.andrewwin.sumup.domain.exception.AiServiceException) {
                continue
            }
        }

        throw com.andrewwin.sumup.domain.exception.AllAiModelsFailedException()
    }

    override suspend fun embed(text: String): FloatArray? {
        val enabledConfigs = aiModelDao.getEnabledConfigsByType(AiModelType.EMBEDDING)
        if (enabledConfigs.isEmpty()) return null

        for (config in enabledConfigs) {
            try {
                return aiService.generateEmbedding(config, text)
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    override suspend fun hasEnabledEmbeddingConfig(): Boolean {
        return aiModelDao.getEnabledConfigsByType(AiModelType.EMBEDDING).isNotEmpty()
    }

    private fun estimateSentenceCountByTargetLength(
        content: String,
        targetChars: Int,
        defaultSentenceCount: Int
    ): Int {
        val totalChars = content.length.coerceAtLeast(1)
        val estimatedByRatio = ((defaultSentenceCount.toFloat() * targetChars.toFloat()) / totalChars.toFloat()).toInt()
        return estimatedByRatio.coerceIn(1, defaultSentenceCount.coerceAtLeast(1))
    }

    private fun formatExtractiveFallback(
        content: String,
        pointsPerNews: Int
    ): String {
        val blocks = content
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val parsedItems = blocks.mapNotNull { block ->
            val separatorIndex = block.indexOf(':')
            if (separatorIndex <= 0) return@mapNotNull null
            val title = block.substring(0, separatorIndex).trim()
            val body = block.substring(separatorIndex + 1).trim()
            if (title.isBlank() || body.isBlank()) null else title to body
        }

        if (parsedItems.isNotEmpty()) {
            return parsedItems.joinToString("\n\n") { (title, body) ->
                val sentences = ExtractiveSummarizer.summarize(body, pointsPerNews)
                formatExtractiveSummaryUseCase.formatItem(
                    title = title,
                    sentences = sentences,
                    isScheduledReport = true
                )
            }
        }

        val sentences = ExtractiveSummarizer.summarize(content, pointsPerNews)
        return sentences.joinToString("\n") { "$bulletSymbol $it" }
    }

    private fun formatCloudSummary(
        response: String,
        sourceContent: String,
        pointsPerNews: Int
    ): String {
        val parsed = AiJsonResponseParser.parseSummary(response)
        val sourceBlocks = parseTitledBlocks(sourceContent)
        val rendered = mutableListOf<String>()
        val perItemLimit = pointsPerNews.coerceAtLeast(1)

        parsed.items.forEach { item ->
            val title = sanitizeSectionTitle(item.title ?: parsed.headline ?: DEFAULT_TITLE)
            val bullets = item.bullets
                .map { normalizeBulletLine(it) }
                .filter { it.isNotBlank() }
                .distinctBy { normalizeComparable(it) }
                .take(perItemLimit)
            if (bullets.isEmpty()) return@forEach
            rendered += "$title:\n${bullets.joinToString("\n") { "$bulletSymbol $it" }}"
        }

        if (sourceBlocks.isNotEmpty() && rendered.size < sourceBlocks.size) {
            val existingTitles = rendered.map { section ->
                normalizeComparable(section.lineSequence().firstOrNull().orEmpty().removeSuffix(":"))
            }.toSet()
            val missing = sourceBlocks.filter { (title, _) ->
                normalizeComparable(title) !in existingTitles
            }
            missing.forEach { (title, body) ->
                val fallbackBullets = ExtractiveSummarizer
                    .summarize(body, perItemLimit)
                    .map { normalizeBulletLine(it) }
                    .filter { it.isNotBlank() }
                    .distinctBy { normalizeComparable(it) }
                    .take(perItemLimit)
                if (fallbackBullets.isNotEmpty()) {
                    rendered += "${sanitizeSectionTitle(title)}:\n${fallbackBullets.joinToString("\n") { "$bulletSymbol $it" }}"
                }
            }
        }

        if (rendered.isEmpty()) {
            throw IllegalStateException("Cloud summary JSON items are empty after normalization.")
        }
        return rendered.joinToString("\n\n")
    }

    private fun normalizeBulletLine(line: String): String {
        return line
            .removePrefix("●")
            .removePrefix("•")
            .removePrefix("-")
            .removePrefix("*")
            .replace(Regex("^\\d+[.)]\\s*"), "")
            .replace(Regex("^\\[[^\\]]+\\]\\s*"), "")
            .trim()
    }

    private fun parseTitledBlocks(content: String): List<Pair<String, String>> {
        return content
            .split(Regex("\\n\\s*\\n"))
            .mapNotNull { block ->
                val separatorIndex = block.indexOf(':')
                if (separatorIndex <= 0) return@mapNotNull null
                val title = block.substring(0, separatorIndex).trim()
                val body = block.substring(separatorIndex + 1).trim()
                if (title.isBlank() || body.isBlank()) null else title to body
            }
    }

    private fun extractPointsForTitle(
        title: String,
        lines: List<String>,
        maxPoints: Int
    ): List<String> {
        val limit = maxPoints.coerceAtLeast(1)
        if (lines.isEmpty()) return emptyList()
        val titleLower = title.lowercase()

        val titleIndex = lines.indexOfFirst { line ->
            val clean = line.removeSuffix(":").lowercase()
            clean == titleLower || clean.contains(titleLower) || titleLower.contains(clean)
        }
        if (titleIndex == -1) return emptyList()

        val points = mutableListOf<String>()
        for (i in (titleIndex + 1) until lines.size) {
            val line = lines[i]
            if (looksLikeTitleLine(line)) break
            val normalized = normalizeBulletLine(line)
            if (normalized.isNotBlank()) points.add(normalized)
            if (points.size >= limit) break
        }
        return points
    }

    private fun looksLikeTitleLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.endsWith(":")) return true
        if (trimmed.matches(Regex("^\\d+[.)]\\s+.+"))) return true
        return false
    }

    private fun buildSection(title: String, points: List<String>): String {
        val normalizedTitle = sanitizeSectionTitle(title)
        val titleKey = normalizeComparable(normalizedTitle)
        val normalizedPoints = points
            .map { normalizeBulletLine(it) }
            .filter { it.isNotBlank() }
            .filter { !isFooterLikeLine(it) }
            .filter { normalizeComparable(it) != titleKey }
            .filter { !normalizeComparable(it).startsWith(titleKey) }
            .take(MAX_POINTS_PER_SECTION)
        if (normalizedPoints.isEmpty()) return ""
        val body = normalizedPoints.joinToString("\n") { "$bulletSymbol $it" }
        return "$normalizedTitle:\n$body"
    }

    private fun sanitizeSectionTitle(title: String): String {
        val clean = title
            .replace(Regex("\\s+"), " ")
            .trim()
            .removeSuffix(":")
        return if (clean.length < MIN_TITLE_LENGTH_CHARS) DEFAULT_TITLE else clean
    }

    private fun normalizeComparable(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isFooterLikeLine(line: String): Boolean {
        val compact = line.lowercase().replace(Regex("\\s+"), " ").trim()
        return FOOTER_PATTERNS.any { it.containsMatchIn(compact) }
    }

    private fun buildSummaryPrompt(basePrompt: String, strictJsonInstruction: String): String {
        val strictRules = """
            |
            |$strictJsonInstruction
            |
            |Return JSON object only:
            |{
            |  "headline": "string",
            |  "items": [
            |    {
            |      "title": "string",
            |      "bullets": ["point 1", "point 2"],
            |      "source": "optional source name"
            |    }
            |  ]
            |}
            |Rules:
            |- bullets must be concise factual statements.
            |- use the same language as the source content.
            |- if the input has multiple article blocks, return one item per block/article.
            |- avoid duplicated bullets and near-duplicates.
            |- no markdown or extra prose outside JSON.
        """.trimMargin()
        return "$basePrompt\n\n$strictRules"
    }

    companion object {
        private const val DEFAULT_MAX_AI_CONTENT_LENGTH = 12000
        private const val DEFAULT_SUMMARY_POINTS_PER_NEWS = 5
        private const val DEFAULT_ADAPTIVE_EXTRACTIVE_ONLY_BELOW_CHARS = 500
        private const val DEFAULT_ADAPTIVE_EXTRACTIVE_COMPRESS_ABOVE_CHARS = 500
        private const val DEFAULT_ADAPTIVE_EXTRACTIVE_COMPRESSION_PERCENT = 30
        private const val MIN_TITLE_LENGTH_CHARS = 12
        private const val MAX_POINTS_PER_SECTION = 10
        private const val DEFAULT_TITLE = "Новина"
        private val FOOTER_PATTERNS = listOf(
            Regex("підписатися\\s+на"),
            Regex("подписат(ь|и)ся\\s+на"),
            Regex("subscribe"),
            Regex("t\\.me/"),
            Regex("telegram"),
            Regex("times\\s+of\\s+ukraine")
        )
    }
}
