package com.andrewwin.sumup.data.repository

import com.andrewwin.sumup.data.local.dao.AiModelDao
import com.andrewwin.sumup.data.local.dao.UserPreferencesDao
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.local.entities.SummaryLanguage
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.remote.AiService
import com.andrewwin.sumup.data.security.SecretEncryptionManager
import com.andrewwin.sumup.domain.service.ExtractiveSummarizer
import com.andrewwin.sumup.domain.support.SummarySourceMeta
import com.andrewwin.sumup.domain.support.AiServiceException
import com.andrewwin.sumup.domain.support.DebugTrace
import com.andrewwin.sumup.domain.support.NoActiveModelException
import com.andrewwin.sumup.domain.support.UnsupportedStrategyException
import com.andrewwin.sumup.domain.support.AiPromptProvider
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.usecase.ai.AiJsonContract
import com.andrewwin.sumup.domain.usecase.ai.AiJsonResponseParser
import com.andrewwin.sumup.domain.usecase.ai.AiPromptCatalog
import com.andrewwin.sumup.domain.usecase.ai.AiSummaryPromptProfile
import com.andrewwin.sumup.domain.usecase.ai.FormatExtractiveSummaryUseCase
import com.andrewwin.sumup.domain.usecase.ai.SummaryResponseJson
import com.andrewwin.sumup.domain.usecase.ai.SummaryThemeItemJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AiRepositoryImpl @Inject constructor(
    private val aiModelDao: AiModelDao,
    private val prefsDao: UserPreferencesDao,
    private val aiService: AiService,
    private val formatExtractiveSummaryUseCase: FormatExtractiveSummaryUseCase,
    private val aiPromptProvider: AiPromptProvider,
    private val secretEncryptionManager: SecretEncryptionManager
) : AiRepository {
    private val _lastUsedSummaryModelName = MutableStateFlow<String?>(null)

    private data class SourceArticleBlock(
        val sourceId: String? = null,
        val sourceName: String? = null,
        val sourceUrl: String? = null,
        val title: String,
        val content: String
    )

    private data class ThemeDefinition(
        val key: String,
        val title: String,
        val emojis: List<String>,
        val keywords: List<String>
    )

    private data class RenderedThemeItem(
        val title: String,
        val source: SourceArticleBlock
    )

    private data class RenderedThemeBlock(
        val title: String,
        val emojis: MutableList<String>,
        val items: MutableList<RenderedThemeItem>
    )

    private val bulletSymbol = "—"

    override val allConfigs: Flow<List<AiModelConfig>> = aiModelDao.getAllConfigs().map { configs ->
        configs.map(::decryptConfig)
    }

    override val lastUsedSummaryModelName: StateFlow<String?> = _lastUsedSummaryModelName

    override fun getConfigsByType(type: AiModelType): Flow<List<AiModelConfig>> =
        aiModelDao.getConfigsByType(type).map { configs -> configs.map(::decryptConfig) }

    override suspend fun fetchAvailableModels(provider: AiProvider, apiKey: String, type: AiModelType): List<String> =
        aiService.fetchModels(provider, apiKey, type)

    override suspend fun addConfig(config: AiModelConfig) = aiModelDao.insertConfig(encryptConfig(config))
    override suspend fun updateConfig(config: AiModelConfig) = aiModelDao.updateConfig(encryptConfig(config))
    override suspend fun deleteConfig(config: AiModelConfig) = aiModelDao.deleteConfig(config)
    override suspend fun migrateLegacyApiKeys() {
        aiModelDao.getAllConfigs().first().forEach { config ->
            if (!secretEncryptionManager.isLocallyEncrypted(config.apiKey) && config.apiKey.isNotBlank()) {
                aiModelDao.updateConfig(encryptConfig(config))
            }
        }
    }

    override suspend fun summarize(content: String, pointsPerNews: Int?): String {
        val prefs = prefsDao.getUserPreferences().first()
        val strategy = prefs?.aiStrategy ?: AiStrategy.ADAPTIVE
        val maxTotalChars = (prefs?.aiMaxCharsTotal ?: DEFAULT_MAX_AI_CONTENT_LENGTH).coerceAtLeast(1000)
        val fallbackPointsPerNews = pointsPerNews
            ?: (prefs?.extractiveSentencesInFeed ?: DEFAULT_SUMMARY_POINTS_PER_NEWS)
        val truncatedContent = content.take(maxTotalChars)
        if (strategy == AiStrategy.LOCAL) {
            return formatExtractiveFallback(truncatedContent, fallbackPointsPerNews)
        }

        val enabledConfigs = getEnabledConfigsByTypeDecrypted(AiModelType.SUMMARY)
        if (enabledConfigs.isEmpty()) {
            return formatExtractiveFallback(truncatedContent, fallbackPointsPerNews)
        }

        val cloudInput = truncatedContent
        val sourceBlocks = parseSourceBlocksForSummary(truncatedContent)
        val promptProfile = when {
            sourceBlocks.size <= 1 -> AiSummaryPromptProfile.SINGLE_ARTICLE_ANALYTICAL
            else -> AiSummaryPromptProfile.DIGEST_ANALYTICAL
        }
        DebugTrace.d(
            "ai_summary",
            "summarize strategy=$strategy sourceBlocks=${sourceBlocks.size} pointsPerNews=$pointsPerNews promptProfile=$promptProfile customPrompt=${prefs?.isCustomSummaryPromptEnabled == true} contentChars=${truncatedContent.length}"
        )
        if (promptProfile == AiSummaryPromptProfile.SINGLE_ARTICLE_ANALYTICAL) {
            DebugTrace.d(
                "ai_summary",
                "singleArticle sourceTitle=${DebugTrace.preview(sourceBlocks.firstOrNull()?.title, 140)} sourceContentChars=${sourceBlocks.firstOrNull()?.content?.length ?: truncatedContent.length}"
            )
        }

        for (config in enabledConfigs) {
            try {
                val prompt = if (prefs?.isCustomSummaryPromptEnabled == true) {
                    prefs.summaryPrompt.ifBlank { aiPromptProvider.defaultSummaryPrompt() }
                } else {
                    aiPromptProvider.defaultSummaryPrompt()
                }
                val strengthenedPrompt = AiPromptCatalog.buildSummaryPrompt(
                    basePrompt = prompt,
                    summaryLanguage = prefs?.summaryLanguage ?: SummaryLanguage.UK,
                    profile = promptProfile
                )
                DebugTrace.d(
                    "ai_summary",
                    "request provider=${config.provider} model=${config.modelName} promptPreview=${DebugTrace.preview(strengthenedPrompt, 400)}"
                )

                val response = aiService.generateResponse(
                    config = config,
                    prompt = strengthenedPrompt,
                    content = cloudInput,
                    expectJson = true
                )
                _lastUsedSummaryModelName.value = config.modelName.takeIf { it.isNotBlank() }
                DebugTrace.d("ai_summary", "rawResponse=${DebugTrace.preview(response)}")
                return runCatching {
                    formatCloudSummary(response, truncatedContent, preferredBulletsPerItem = pointsPerNews)
                }.getOrElse {
                    DebugTrace.e("ai_summary", "formatCloudSummary failed, trying next model", it)
                    continue
                }
            } catch (e: AiServiceException) {
                DebugTrace.e("ai_summary", "provider failed provider=${config.provider} model=${config.modelName}", e)
                continue
            } catch (e: Exception) {
                DebugTrace.e("ai_summary", "unexpected summarize failure", e)
                throw e
            }
        }

        DebugTrace.w("ai_summary", "cloud summarization unavailable, fallback to extractive")
        return formatExtractiveFallback(truncatedContent, fallbackPointsPerNews)
    }

    override suspend fun askQuestion(content: String, question: String): String {
        val prompt = AiPromptCatalog.buildQuestionPrompt(
            question = question,
            questionPrefix = aiPromptProvider.questionPromptPrefix(),
            questionSuffix = aiPromptProvider.questionPromptSuffix()
        )
        return askWithPrompt(content, prompt)
    }

    override suspend fun askWithPrompt(content: String, prompt: String): String {
        val prefs = prefsDao.getUserPreferences().first()
        val maxTotalChars = (prefs?.aiMaxCharsTotal ?: DEFAULT_MAX_AI_CONTENT_LENGTH).coerceAtLeast(1000)

        if (prefs?.aiStrategy == AiStrategy.LOCAL) {
            throw UnsupportedStrategyException()
        }

        val enabledConfigs = getEnabledConfigsByTypeDecrypted(AiModelType.SUMMARY)
        if (enabledConfigs.isEmpty()) throw NoActiveModelException()

        for (config in enabledConfigs) {
            try {
                val response = aiService.generateResponse(
                    config = config,
                    prompt = prompt,
                    content = content.take(maxTotalChars),
                    expectJson = true
                )
                _lastUsedSummaryModelName.value = config.modelName.takeIf { it.isNotBlank() }
                return response
            } catch (e: AiServiceException) {
                continue
            }
        }

        throw com.andrewwin.sumup.domain.support.AllAiModelsFailedException()
    }

    override suspend fun embed(text: String): FloatArray? {
        val enabledConfigs = getEnabledConfigsByTypeDecrypted(AiModelType.EMBEDDING)
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
        return getEnabledConfigsByTypeDecrypted(AiModelType.EMBEDDING).isNotEmpty()
    }

    private suspend fun getEnabledConfigsByTypeDecrypted(type: AiModelType): List<AiModelConfig> =
        aiModelDao.getEnabledConfigsByType(type).map(::decryptConfig)

    private fun encryptConfig(config: AiModelConfig): AiModelConfig =
        config.copy(apiKey = secretEncryptionManager.encryptLocal(config.apiKey))

    private fun decryptConfig(config: AiModelConfig): AiModelConfig =
        config.copy(apiKey = secretEncryptionManager.decryptLocal(config.apiKey))

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
        DebugTrace.d("ai_summary", "formatExtractiveFallback pointsPerNews=$pointsPerNews")
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
            val sourceBlocks = parseSourceBlocksForSummary(content)
            buildHeuristicThemeSummary(sourceBlocks)?.let {
                DebugTrace.d("ai_summary", "formatExtractiveFallback branch=heuristicThemes preview=${DebugTrace.preview(it)}")
                return it
            }
            return parsedItems.joinToString("\n\n") { (title, body) ->
                val sentences = ExtractiveSummarizer.summarize(body, pointsPerNews)
                formatExtractiveSummaryUseCase.formatItem(
                    title = title,
                    sentences = sentences,
                    isScheduledReport = true,
                    maxBullets = pointsPerNews
                )
            }
        }

        val sentences = ExtractiveSummarizer.summarize(content, pointsPerNews)
        return sentences.joinToString("\n") { "$bulletSymbol $it" }.also {
            DebugTrace.d("ai_summary", "formatExtractiveFallback branch=plainBullets preview=${DebugTrace.preview(it)}")
        }
    }

    private fun formatCloudSummary(
        response: String,
        sourceContent: String,
        preferredBulletsPerItem: Int?
    ): String {
        val parsed = AiJsonResponseParser.parseSummary(response)
        val sourceBlocks = parseSourceBlocksForSummary(sourceContent)
        val isMultiArticle = sourceBlocks.size > 1
        DebugTrace.d(
            "ai_summary",
            "formatCloudSummary parsedItems=${parsed.items.size} parsedThemes=${parsed.themes.size} sourceBlocks=${sourceBlocks.size} isMultiArticle=$isMultiArticle"
        )
        if (isMultiArticle) {
            renderThemedSummary(parsed, sourceBlocks)?.let {
                DebugTrace.d("ai_summary", "formatCloudSummary branch=themes preview=${DebugTrace.preview(it)}")
                return it
            }
        }
        val rendered = mutableListOf<String>()
        val perItemLimit = if (isMultiArticle) {
            MULTI_ARTICLE_SENTENCE_LIMIT
        } else {
            preferredBulletsPerItem?.coerceIn(MIN_SINGLE_ARTICLE_CLOUD_BULLETS, MAX_SINGLE_ARTICLE_CLOUD_BULLETS)
                ?: DEFAULT_SINGLE_ARTICLE_CLOUD_BULLETS
        }
        val minBulletsPerItem = if (sourceBlocks.size <= 1) MIN_SINGLE_ARTICLE_CLOUD_BULLETS else MIN_MULTI_ARTICLE_CLOUD_BULLETS
        val maxSections = sourceBlocks.size.coerceIn(1, DEFAULT_MAX_CLOUD_SECTIONS)

        parsed.items.forEach { item ->
            if (rendered.size >= maxSections) return@forEach
            val sourceFallbackTitle = sourceBlocks.getOrNull(rendered.size)?.title
            val rawTitle = item.title ?: parsed.headline ?: sourceFallbackTitle ?: DEFAULT_TITLE
            val title = sanitizeSectionTitle(rawTitle)
            val effectiveTitle = if (isMultiArticle && isGenericSectionTitle(title)) {
                sanitizeSectionTitle(sourceFallbackTitle ?: title)
            } else {
                title
            }
            val rawBullets = item.bullets
            val bullets = if (isMultiArticle) {
                dedupeBullets(
                    rawBullets = rawBullets,
                    title = effectiveTitle,
                    limit = perItemLimit,
                    minCount = minBulletsPerItem
                )
            } else {
                ensureSingleArticleBullets(
                    rawBullets = rawBullets,
                    title = effectiveTitle,
                    sourceBlock = sourceBlocks.firstOrNull(),
                    limit = perItemLimit
                )
            }
            if (!isMultiArticle) {
                DebugTrace.d(
                    "ai_summary",
                    "singleArticle formatCloudSummary itemTitle=${DebugTrace.preview(effectiveTitle, 120)} rawBullets=${rawBullets.size} finalBullets=${bullets.size} rawFirst=${DebugTrace.preview(rawBullets.firstOrNull(), 150)} finalFirst=${DebugTrace.preview(bullets.firstOrNull(), 150)}"
                )
            }
            if (bullets.isEmpty()) return@forEach
            rendered += if (isMultiArticle) {
                val paragraph = bullets.joinToString(" ").trim()
                if (isGenericSectionTitle(effectiveTitle)) {
                    paragraph
                } else {
                    "$effectiveTitle:\n$paragraph"
                }
            } else {
                "$effectiveTitle:\n${bullets.joinToString("\n") { "$bulletSymbol $it" }}"
            }
        }

        if (sourceBlocks.isNotEmpty() && rendered.size < maxSections) {
            val existingTitles = rendered.map { section ->
                normalizeComparable(section.lineSequence().firstOrNull().orEmpty().removeSuffix(":"))
            }.toSet()
            val missing = sourceBlocks.filter { block ->
                normalizeComparable(block.title) !in existingTitles
            }
            missing.forEach { block ->
                if (rendered.size >= maxSections) return@forEach
                val fallbackBullets = ExtractiveSummarizer
                    .summarize(block.content, perItemLimit)
                    .let {
                        if (isMultiArticle) {
                            dedupeBullets(it, sanitizeSectionTitle(block.title), perItemLimit, minBulletsPerItem)
                        } else {
                            ensureSingleArticleBullets(
                                rawBullets = it,
                                title = sanitizeSectionTitle(block.title),
                                sourceBlock = block,
                                limit = perItemLimit
                            )
                        }
                    }
                if (fallbackBullets.isNotEmpty()) {
                    rendered += if (isMultiArticle) {
                        "${sanitizeSectionTitle(block.title)}:\n${fallbackBullets.joinToString(" ").trim()}"
                    } else {
                        "${sanitizeSectionTitle(block.title)}:\n${fallbackBullets.joinToString("\n") { "$bulletSymbol $it" }}"
                    }
                }
            }
        }

        if (rendered.isEmpty()) {
            throw IllegalStateException("Cloud summary JSON items are empty after normalization.")
        }
        return rendered.joinToString("\n\n").also {
            DebugTrace.d("ai_summary", "formatCloudSummary branch=legacyItems preview=${DebugTrace.preview(it)}")
        }
    }

    private fun renderThemedSummary(
        parsed: SummaryResponseJson,
        sourceBlocks: List<SourceArticleBlock>
    ): String? {
        if (sourceBlocks.size <= 1) return null
        if (parsed.themes.isEmpty()) {
            val legacyTitles = parsed.items.mapIndexedNotNull { index, item ->
                val sourceId = item.sourceId ?: sourceBlocks.getOrNull(index)?.sourceId ?: return@mapIndexedNotNull null
                val title = item.title?.trim()?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
                sourceId to title
            }.toMap()
            return buildHeuristicThemeSummary(sourceBlocks, legacyTitles)?.also {
                DebugTrace.d("ai_summary", "renderThemedSummary fallbackFromLegacyItems=true")
            }
        }

        val sourceById = sourceBlocks.mapNotNull { block ->
            block.sourceId?.let { it to block }
        }.toMap()
        val sourceByTitle = sourceBlocks.associateBy { normalizeComparable(it.title) }
        val usedSourceKeys = mutableSetOf<String>()
        val usedThemeTitles = mutableSetOf<String>()
        val usedEmojis = mutableSetOf<String>()
        val renderedThemes = mutableListOf<RenderedThemeBlock>()

        parsed.themes.forEach { theme ->
            if (renderedThemes.size >= MAX_THEMES) return@forEach
            val normalizedThemeTitle = sanitizeThemeTitle(theme.title ?: parsed.headline ?: DEFAULT_THEME_TITLE)
            if (!usedThemeTitles.add(normalizeComparable(normalizedThemeTitle))) return@forEach

            val renderedItems = mutableListOf<RenderedThemeItem>()
            val usedThemeItemKeys = mutableSetOf<String>()
            theme.items.forEach { item ->
                if (renderedItems.size >= MAX_THEME_ITEMS) return@forEach
                val sourceBlock = resolveThemeSourceBlock(
                    item = item,
                    sourceById = sourceById,
                    sourceByTitle = sourceByTitle
                ) ?: return@forEach
                val sourceKey = buildSourceIdentity(sourceBlock)
                if (!usedSourceKeys.add(sourceKey)) return@forEach
                val itemTitle = sanitizeThemeItemTitle(item.title ?: sourceBlock.title)
                val itemKey = buildString {
                    append(normalizeComparable(itemTitle))
                    append('|')
                    append(sourceKey)
                }
                if (!usedThemeItemKeys.add(itemKey)) {
                    usedSourceKeys.remove(sourceKey)
                    return@forEach
                }
                renderedItems += RenderedThemeItem(title = itemTitle, source = sourceBlock)
            }

            if (renderedItems.isEmpty()) return@forEach
            val emojis = theme.emojis
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .filter { usedEmojis.add(it) }
                .take(MAX_THEME_EMOJIS)
            renderedThemes += RenderedThemeBlock(
                title = normalizedThemeTitle,
                emojis = emojis.toMutableList(),
                items = renderedItems.toMutableList()
            )
        }

        val unusedSources = sourceBlocks.filter { source ->
            buildSourceIdentity(source) !in usedSourceKeys
        }
        buildHeuristicThemeGroups(unusedSources).forEach { (definition, blocks) ->
            val normalizedTitle = sanitizeThemeTitle(definition.title)
            val targetTheme = renderedThemes.firstOrNull {
                normalizeComparable(it.title) == normalizeComparable(normalizedTitle)
            } ?: run {
                if (renderedThemes.size >= MAX_THEMES) return@forEach
                if (!usedThemeTitles.add(normalizeComparable(normalizedTitle))) return@forEach
                RenderedThemeBlock(
                    title = normalizedTitle,
                    emojis = mutableListOf<String>().also { list ->
                        definition.emojis
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .forEach { emoji ->
                                if (list.size < MAX_THEME_EMOJIS && usedEmojis.add(emoji)) {
                                    list += emoji
                                }
                            }
                    },
                    items = mutableListOf()
                ).also(renderedThemes::add)
            }

            if (targetTheme.emojis.size < MAX_THEME_EMOJIS) {
                definition.emojis
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { emoji ->
                        if (targetTheme.emojis.size < MAX_THEME_EMOJIS && usedEmojis.add(emoji)) {
                            targetTheme.emojis += emoji
                        }
                    }
            }

            blocks.forEach { block ->
                if (targetTheme.items.size >= MAX_THEME_ITEMS) return@forEach
                val sourceKey = buildSourceIdentity(block)
                if (!usedSourceKeys.add(sourceKey)) return@forEach
                targetTheme.items += RenderedThemeItem(
                    title = sanitizeThemeItemTitle(titleOverridesBySourceId = emptyMap(), block = block),
                    source = block
                )
            }
        }

        val normalizedRenderedThemes = renderedThemes
            .mapNotNull { theme ->
                val items = theme.items
                    .distinctBy { buildSourceIdentity(it.source) }
                    .take(MAX_THEME_ITEMS)
                if (items.isEmpty()) {
                    null
                } else {
                    theme.copy(items = items.toMutableList())
                }
            }
            .take(MAX_THEMES)

        if (normalizedRenderedThemes.isEmpty()) return null
        return normalizedRenderedThemes.joinToString("\n\n") { theme ->
            buildString {
                if (theme.emojis.isNotEmpty()) {
                    append(theme.emojis.joinToString(separator = ""))
                    append(' ')
                }
                append(theme.title)
                theme.items.forEach { item ->
                    append("\n")
                    append(THEME_ITEM_MARKER)
                    append(' ')
                    append(item.title)
                    val sourceName = item.source.sourceName?.trim().orEmpty()
                    val sourceUrl = item.source.sourceUrl?.trim().orEmpty()
                    if (sourceName.isNotBlank() && sourceUrl.isNotBlank()) {
                        append("\n")
                        append(SummarySourceMeta.PREFIX)
                        append(sourceName)
                        append('|')
                        append(sourceUrl)
                    }
                }
            }
        }.also {
            DebugTrace.d("ai_summary", "renderThemedSummary parsedThemeBlocks=${normalizedRenderedThemes.size}")
        }
    }

    private fun sanitizeThemeItemTitle(
        titleOverridesBySourceId: Map<String, String>,
        block: SourceArticleBlock
    ): String {
        return sanitizeThemeItemTitle(
            block.sourceId?.let(titleOverridesBySourceId::get) ?: block.title
        )
    }

    private fun buildHeuristicThemeGroups(
        sourceBlocks: List<SourceArticleBlock>
    ): List<Pair<ThemeDefinition, List<SourceArticleBlock>>> {
        if (sourceBlocks.isEmpty()) return emptyList()
        val grouped = sourceBlocks.groupBy { block -> classifyTheme(block).key }
        val selectedGroups = grouped.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<SourceArticleBlock>>> { it.value.size }
                    .thenBy { themeDefinition(it.key).title }
            )
            .filter { it.value.size >= MIN_THEME_ITEMS }
            .take(MAX_THEMES)

        return if (selectedGroups.isNotEmpty()) {
            selectedGroups.map { themeDefinition(it.key) to it.value.take(MAX_THEME_ITEMS) }
        } else {
            listOf(GENERIC_THEME_DEFINITION to sourceBlocks.take(MAX_THEME_ITEMS))
        }
    }

    private fun buildHeuristicThemeSummary(
        sourceBlocks: List<SourceArticleBlock>,
        titleOverridesBySourceId: Map<String, String> = emptyMap()
    ): String? {
        if (sourceBlocks.size <= 1) return null
        val groupsToRender = buildHeuristicThemeGroups(sourceBlocks)
        return groupsToRender.joinToString("\n\n") { (definition, blocks) ->
            buildString {
                append(definition.emojis.joinToString(separator = ""))
                append(' ')
                append(definition.title)
                blocks.take(MAX_THEME_ITEMS).forEach { block ->
                    append("\n")
                    append(THEME_ITEM_MARKER)
                    append(' ')
                    append(sanitizeThemeItemTitle(titleOverridesBySourceId, block))
                    val sourceName = block.sourceName?.trim().orEmpty()
                    val sourceUrl = block.sourceUrl?.trim().orEmpty()
                    if (sourceName.isNotBlank() && sourceUrl.isNotBlank()) {
                        append("\n")
                        append(SummarySourceMeta.PREFIX)
                        append(sourceName)
                        append('|')
                        append(sourceUrl)
                    }
                }
            }
        }.also {
            DebugTrace.d(
                "ai_summary",
                "buildHeuristicThemeSummary themes=${groupsToRender.size} groups=${groupsToRender.joinToString { it.first.key + ":" + it.second.size }}"
            )
        }
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

    private fun ensureSingleArticleBullets(
        rawBullets: List<String>,
        title: String,
        sourceBlock: SourceArticleBlock?,
        limit: Int
    ): List<String> {
        val clampedLimit = limit.coerceIn(MIN_SINGLE_ARTICLE_CLOUD_BULLETS, MAX_SINGLE_ARTICLE_CLOUD_BULLETS)
        val sourceFallback = sourceBlock?.content
            ?.let { ExtractiveSummarizer.summarize(it, clampedLimit) }
            .orEmpty()
        val result = dedupeBullets(
            rawBullets = rawBullets + sourceFallback,
            title = title,
            limit = clampedLimit,
            minCount = MIN_SINGLE_ARTICLE_CLOUD_BULLETS,
            maxChars = MAX_SINGLE_ARTICLE_BULLET_CHARS
        )
        DebugTrace.d(
            "ai_summary",
            "ensureSingleArticleBullets title=${DebugTrace.preview(title, 120)} limit=$clampedLimit raw=${rawBullets.size} fallback=${sourceFallback.size} result=${result.size} rawFirst=${DebugTrace.preview(rawBullets.firstOrNull(), 140)} fallbackFirst=${DebugTrace.preview(sourceFallback.firstOrNull(), 140)} resultFirst=${DebugTrace.preview(result.firstOrNull(), 140)}"
        )
        return result
    }

    private fun parseSourceBlocksForSummary(content: String): List<SourceArticleBlock> {
        val structured = parseStructuredSourceBlocks(content)
        if (structured.isNotEmpty()) return structured
        return parseTitledBlocks(content).map { (title, body) ->
            SourceArticleBlock(title = title, content = body)
        }
    }

    private fun parseStructuredSourceBlocks(content: String): List<SourceArticleBlock> {
        return content
            .split(Regex("\\n\\s*\\n"))
            .mapNotNull { block ->
                var sourceId: String? = null
                var sourceName: String? = null
                var sourceUrl: String? = null
                var title: String? = null
                var currentField: String? = null
                val contentBuffer = StringBuilder()

                block.lines().forEach { rawLine ->
                    val line = rawLine.trimEnd()
                    when {
                        line.startsWith("source_id:", ignoreCase = true) -> {
                            sourceId = line.substringAfter(':').trim().ifBlank { null }
                            currentField = null
                        }
                        line.startsWith("source_name:", ignoreCase = true) -> {
                            sourceName = line.substringAfter(':').trim().ifBlank { null }
                            currentField = null
                        }
                        line.startsWith("source_url:", ignoreCase = true) -> {
                            sourceUrl = line.substringAfter(':').trim().ifBlank { null }
                            currentField = null
                        }
                        line.startsWith("title:", ignoreCase = true) -> {
                            title = line.substringAfter(':').trim().ifBlank { null }
                            currentField = null
                        }
                        line.startsWith("content:", ignoreCase = true) -> {
                            val value = line.substringAfter(':').trim()
                            if (value.isNotBlank()) {
                                if (contentBuffer.isNotEmpty()) contentBuffer.append('\n')
                                contentBuffer.append(value)
                            }
                            currentField = "content"
                        }
                        currentField == "content" -> {
                            if (line.isNotBlank()) {
                                if (contentBuffer.isNotEmpty()) contentBuffer.append('\n')
                                contentBuffer.append(line.trim())
                            }
                        }
                    }
                }

                val resolvedTitle = title?.trim().orEmpty()
                val resolvedContent = contentBuffer.toString().trim()
                if (resolvedTitle.isBlank() || resolvedContent.isBlank()) {
                    null
                } else {
                    SourceArticleBlock(
                        sourceId = sourceId,
                        sourceName = sourceName,
                        sourceUrl = sourceUrl,
                        title = resolvedTitle,
                        content = resolvedContent
                    )
                }
            }
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

    private fun resolveThemeSourceBlock(
        item: SummaryThemeItemJson,
        sourceById: Map<String, SourceArticleBlock>,
        sourceByTitle: Map<String, SourceArticleBlock>
    ): SourceArticleBlock? {
        item.sourceId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(sourceById::get)
            ?.let { return it }

        return item.title
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { normalizeComparable(it) }
            ?.let(sourceByTitle::get)
    }

    private fun buildSourceIdentity(block: SourceArticleBlock): String {
        return block.sourceId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: block.sourceUrl
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: normalizeComparable(block.title)
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

    private fun sanitizeThemeTitle(title: String): String {
        val clean = title
            .replace(Regex("\\s+"), " ")
            .trim()
            .removePrefix(THEME_ITEM_MARKER)
            .trim()
            .removeSuffix(":")
        return if (clean.length < MIN_THEME_TITLE_LENGTH_CHARS) DEFAULT_THEME_TITLE else clean
    }

    private fun sanitizeThemeItemTitle(title: String): String {
        val clean = title
            .replace(Regex("\\s+"), " ")
            .trim()
            .removePrefix(THEME_ITEM_MARKER)
            .trim()
            .removeSuffix(".")
            .removeSuffix(":")
        if (clean.isBlank()) return DEFAULT_THEME_ITEM_TITLE
        if (clean.length <= MAX_THEME_ITEM_TITLE_CHARS) return clean
        val clipped = clean.take(MAX_THEME_ITEM_TITLE_CHARS).trimEnd()
        val lastSpace = clipped.lastIndexOf(' ')
        val base = if (lastSpace > MAX_THEME_ITEM_TITLE_CHARS / 2) {
            clipped.substring(0, lastSpace)
        } else {
            clipped
        }
        return base.trimEnd(' ', ',', ';', ':') + "…"
    }

    private fun classifyTheme(block: SourceArticleBlock): ThemeDefinition {
        val haystack = normalizeComparable("${block.title} ${block.content}")
        val best = THEME_DEFINITIONS
            .map { definition ->
                definition to definition.keywords.count { keyword -> haystack.contains(keyword) }
            }
            .maxWithOrNull(compareBy<Pair<ThemeDefinition, Int>> { it.second }.thenByDescending { it.first.keywords.size })
            ?.takeIf { it.second > 0 }
            ?.first
        return best ?: themeDefinition("generic")
    }

    private fun themeDefinition(key: String): ThemeDefinition {
        return THEME_DEFINITIONS.firstOrNull { it.key == key } ?: GENERIC_THEME_DEFINITION
    }

    private fun isGenericSectionTitle(title: String): Boolean {
        return normalizeComparable(title) in GENERIC_SECTION_TITLES
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

    private fun dedupeBullets(
        rawBullets: List<String>,
        title: String,
        limit: Int,
        minCount: Int = 1,
        maxChars: Int = MAX_CLOUD_BULLET_CHARS
    ): List<String> {
        val titleKey = normalizeComparable(title)
        val result = mutableListOf<String>()
        val seen = mutableListOf<Set<String>>()
        val relaxedCandidates = mutableListOf<String>()
        for (raw in rawBullets) {
            val normalized = compactBullet(normalizeBulletLine(raw), maxChars)
            if (normalized.isBlank()) continue
            val key = normalizeComparable(normalized)
            if (key.isBlank()) continue
            if (key == titleKey || key.startsWith(titleKey)) continue
            relaxedCandidates += normalized
            val tokens = key.split(" ").filter { it.length > 2 }.toSet()
            if (tokens.isEmpty()) continue
            val isNearDuplicate = seen.any { jaccardSimilarity(it, tokens) >= 0.9f }
            if (isNearDuplicate) continue
            result += normalized
            seen += tokens
            if (result.size >= limit) break
        }
        if (result.size < minCount.coerceAtLeast(1)) {
            val existingKeys = result.map { normalizeComparable(it) }.toMutableSet()
            for (candidate in relaxedCandidates) {
                if (result.size >= limit) break
                if (result.size >= minCount && minCount <= 1) break
                val key = normalizeComparable(candidate)
                if (key.isBlank() || key in existingKeys) continue
                result += candidate
                existingKeys += key
            }
        }
        return result
    }

    private fun compactBullet(value: String, maxChars: Int = MAX_CLOUD_BULLET_CHARS): String {
        val sentence = value
            .replace(Regex("\\s+"), " ")
            .trim()
            .removeSuffix(":")
        if (sentence.length <= maxChars) return sentence
        val clipped = sentence.take(maxChars).trimEnd()
        val lastSpace = clipped.lastIndexOf(' ')
        val base = if (lastSpace > maxChars / 2) {
            clipped.substring(0, lastSpace)
        } else {
            clipped
        }
        return base.trimEnd(' ', ',', ';', ':') + "…"
    }

    private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val intersection = a.intersect(b).size.toFloat()
        val union = a.union(b).size.toFloat().coerceAtLeast(1f)
        return intersection / union
    }

    companion object {
        private const val DEFAULT_MAX_AI_CONTENT_LENGTH = 12000
        private const val DEFAULT_SUMMARY_POINTS_PER_NEWS = 5
        private const val DEFAULT_ADAPTIVE_EXTRACTIVE_ONLY_BELOW_CHARS = 500
        private const val DEFAULT_ADAPTIVE_EXTRACTIVE_COMPRESS_ABOVE_CHARS = 500
        private const val DEFAULT_ADAPTIVE_EXTRACTIVE_COMPRESSION_PERCENT = 30
        private const val MIN_TITLE_LENGTH_CHARS = 12
        private const val DEFAULT_TITLE = "Новина"
        private const val MAX_POINTS_PER_SECTION = 10
        private const val DEFAULT_CLOUD_BULLETS_PER_SECTION = 4
        private const val DEFAULT_SINGLE_ARTICLE_CLOUD_BULLETS = 4
        private const val MULTI_ARTICLE_SENTENCE_LIMIT = 2
        private const val DEFAULT_MAX_CLOUD_SECTIONS = 4
        private const val MAX_CLOUD_BULLET_CHARS = 320
        private const val MIN_CLOUD_BULLET_CHARS = 100
        private const val MAX_SINGLE_ARTICLE_BULLET_CHARS = 250
        private const val MIN_THEME_ITEMS = 1
        private const val MAX_THEME_ITEMS = 3
        private const val MAX_THEMES = 4
        private const val MAX_THEME_EMOJIS = 4
        private const val MAX_THEME_ITEM_TITLE_CHARS = 125
        private const val MIN_THEME_TITLE_LENGTH_CHARS = 6
        private const val MIN_SINGLE_ARTICLE_CLOUD_BULLETS = 2
        private const val MAX_SINGLE_ARTICLE_CLOUD_BULLETS = 5
        private const val MIN_MULTI_ARTICLE_CLOUD_BULLETS = 2
        private val GENERIC_SECTION_TITLES = setOf("новина", "news", "article", "стаття")
        private const val DEFAULT_THEME_TITLE = "Ключова тема"
        private const val DEFAULT_THEME_ITEM_TITLE = "Ключова новина"
        private const val THEME_ITEM_MARKER = "—"
        private val GENERIC_THEME_DEFINITION = ThemeDefinition(
            key = "generic",
            title = "Ключові новини",
            emojis = listOf("📰", "📌", "🧭"),
            keywords = emptyList()
        )
        private val THEME_DEFINITIONS = listOf(
            ThemeDefinition(
                key = "ukraine_war",
                title = "Війна Росії проти України",
                emojis = listOf("🇺🇦", "⚔️", "🛡️"),
                keywords = listOf(
                    "зсу", "сили оборони", "україн", "росі", "окуп", "обстр", "удар", "дрон",
                    "ракет", "військ", "фронт", "атак", "ппо", "безпілот", "army", "strike", "missile", "drone"
                )
            ),
            ThemeDefinition(
                key = "odesa_region",
                title = "Одещина та регіон",
                emojis = listOf("🌊", "🏖️", "📍"),
                keywords = listOf(
                    "одес", "odesa", "дністров", "білгород", "чорномор", "лиман", "південн", "izmail", "izm", "порт"
                )
            ),
            ThemeDefinition(
                key = "world_politics",
                title = "Світова політика",
                emojis = listOf("🌍", "🏛️", "🗳️"),
                keywords = listOf(
                    "трамп", "сша", "usa", "єс", "eu", "нато", "китай", "china", "іран", "ізраїл", "israel",
                    "санкц", "вибор", "summit", "president", "parliament"
                )
            ),
            ThemeDefinition(
                key = "technology",
                title = "Технології",
                emojis = listOf("💻", "🤖", "🚀"),
                keywords = listOf(
                    "технолог", "technology", "ai", "штучн", "openai", "google", "apple", "microsoft",
                    "chip", "чип", "software", "програм", "robot", "робот", "startup", "стартап"
                )
            ),
            ThemeDefinition(
                key = "economy_business",
                title = "Економіка та бізнес",
                emojis = listOf("💰", "📈", "🏭"),
                keywords = listOf(
                    "економ", "бізнес", "компан", "ринок", "market", "price", "ціна", "експорт", "import",
                    "tariff", "інвест", "financ", "bank", "нафт", "газ", "oil", "gdp"
                )
            ),
            ThemeDefinition(
                key = "incidents_society",
                title = "Інциденти та суспільство",
                emojis = listOf("🚨", "⚖️", "🏥"),
                keywords = listOf(
                    "затрим", "незакон", "поліц", "суд", "crime", "incident", "авар", "пожеж", "лікар",
                    "museum", "музей", "school", "освіт", "культур", "соц"
                )
            )
        )
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






