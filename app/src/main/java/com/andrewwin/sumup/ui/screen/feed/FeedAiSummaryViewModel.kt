package com.andrewwin.sumup.ui.screen.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.UserPreferences
import com.andrewwin.sumup.domain.repository.AiModelConfigRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.support.AllAiModelsFailedException
import com.andrewwin.sumup.domain.support.LocalModelMissingException
import com.andrewwin.sumup.domain.support.NoActiveModelException
import com.andrewwin.sumup.domain.support.UnsupportedStrategyException
import com.andrewwin.sumup.domain.usecase.ai.AskQuestionAboutNewsUseCase
import com.andrewwin.sumup.domain.usecase.ai.CompareNewsUseCase
import com.andrewwin.sumup.domain.usecase.ai.GetFeedSummaryUseCase
import com.andrewwin.sumup.domain.usecase.ai.SummarizeSingleArticleUseCase
import com.andrewwin.sumup.domain.usecase.ai.SummaryResult
import com.andrewwin.sumup.domain.usecase.common.FormatSummaryResultUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

@HiltViewModel
class FeedAiSummaryViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val articleRepository: ArticleRepository,
    private val summarizeSingleArticleUseCase: SummarizeSingleArticleUseCase,
    private val getFeedSummaryUseCase: GetFeedSummaryUseCase,
    private val compareNewsUseCase: CompareNewsUseCase,
    private val askQuestionAboutNewsUseCase: AskQuestionAboutNewsUseCase,
    private val formatSummaryResultUseCase: FormatSummaryResultUseCase,
    private val aiModelConfigRepository: AiModelConfigRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val feedAiSessionCache: FeedAiSessionCache
) : AndroidViewModel(application) {

    private val summaryMode = savedStateHandle.get<String>(ARG_MODE)
        ?.trim()
        ?.uppercase()
        ?.let { runCatching { FeedAiSummaryMode.valueOf(it) }.getOrNull() }
        ?: FeedAiSummaryMode.ARTICLE
    private val articleIds = decodeIds(savedStateHandle.get<String>(ARG_IDS))

    private val _aiResult = MutableStateFlow<AiPresentationResult?>(null)
    val aiResult: StateFlow<AiPresentationResult?> = _aiResult.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _userQuestion = MutableStateFlow("")
    val userQuestion: StateFlow<String> = _userQuestion.asStateFlow()

    private val _summaryTitle = MutableStateFlow<String?>(null)
    val summaryTitle: StateFlow<String?> = _summaryTitle.asStateFlow()

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val activeSummaryModelName: StateFlow<String?> = aiModelConfigRepository.getConfigsByType(AiModelType.SUMMARY)
        .map { configs ->
            configs.firstOrNull { it.isEnabled }?.modelName?.takeIf { it.isNotBlank() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isFeedMode: Boolean = summaryMode == FeedAiSummaryMode.FEED

    init {
        viewModelScope.launch {
            openCachedOrGenerate()
        }
    }

    fun onQuestionChange(question: String) {
        _userQuestion.value = question
    }

    fun regenerate() {
        when (summaryMode) {
            FeedAiSummaryMode.ARTICLE -> summarizeArticle(forceRefresh = true)
            FeedAiSummaryMode.CLUSTER -> summarizeCluster(forceRefresh = true)
            FeedAiSummaryMode.FEED -> summarizeFeed(forceRefresh = true)
        }
    }

    fun askQuestion() {
        val question = _userQuestion.value.trim()
        if (question.isBlank()) return

        launchAi {
            val articles = resolveArticles()
            if (articles.isEmpty()) {
                Result.success(SummaryResult.Error(localizeError(IllegalStateException("No articles"))))
            } else {
                askQuestionAboutNewsUseCase(articles, question)
            }
        }
    }

    private suspend fun openCachedOrGenerate() {
        val articles = resolveArticles()
        _summaryTitle.value = articles.firstOrNull()?.title

        val cached = when (summaryMode) {
            FeedAiSummaryMode.ARTICLE -> articleIds.firstOrNull()?.let { feedAiSessionCache.getArticleSummary(it) }
            FeedAiSummaryMode.CLUSTER -> feedAiSessionCache.getClusterSummary(articleIds)
            FeedAiSummaryMode.FEED -> feedAiSessionCache.getFeedSummary(articleIds)
        }
        if (cached != null) {
            _aiResult.value = cached
            return
        }

        regenerate()
    }

    private fun summarizeArticle(forceRefresh: Boolean) {
        val articleId = articleIds.firstOrNull() ?: return
        launchAi {
            val article = resolveArticles().firstOrNull()
                ?: return@launchAi Result.failure(IllegalStateException("Article not found"))
            summarizeSingleArticleUseCase(article).onSuccess { result ->
                if (forceRefresh || feedAiSessionCache.getArticleSummary(articleId) == null) {
                    feedAiSessionCache.putArticleSummary(
                        articleId,
                        AiPresentationResult(result = result, rawText = formatSummaryResultUseCase(result))
                    )
                }
            }
        }
    }

    private fun summarizeCluster(forceRefresh: Boolean) {
        if (articleIds.isEmpty()) return
        launchAi {
            val articles = resolveArticles()
            compareNewsUseCase(articles).onSuccess { result ->
                if (forceRefresh || feedAiSessionCache.getClusterSummary(articleIds) == null) {
                    feedAiSessionCache.putClusterSummary(
                        articleIds,
                        AiPresentationResult(result = result, rawText = formatSummaryResultUseCase(result))
                    )
                }
            }
        }
    }

    private fun summarizeFeed(forceRefresh: Boolean) {
        if (articleIds.isEmpty()) return
        launchAi {
            val articles = resolveArticles()
            getFeedSummaryUseCase.summarizeArticles(articles).onSuccess { result ->
                if (forceRefresh || feedAiSessionCache.getFeedSummary(articleIds) == null) {
                    feedAiSessionCache.putFeedSummary(
                        articleIds,
                        AiPresentationResult(result = result, rawText = formatSummaryResultUseCase(result))
                    )
                }
            }
        }
    }

    private fun launchAi(block: suspend () -> Result<SummaryResult>) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResult.value = null
            val result = block()
                .map { summary ->
                    AiPresentationResult(
                        result = summary,
                        rawText = formatSummaryResultUseCase(summary)
                    )
                }
                .getOrElse { error ->
                    val localized = localizeError(error)
                    AiPresentationResult(
                        result = SummaryResult.Error(localized),
                        rawText = localized
                    )
                }
            _aiResult.value = result
            _isAiLoading.value = false
        }
    }

    private suspend fun resolveArticles(): List<Article> {
        if (articleIds.isEmpty()) return emptyList()
        val articleMap = articleRepository.allArticles.first().associateBy { it.id }
        return articleIds.mapNotNull { articleMap[it] }
    }

    private fun localizeError(e: Throwable): String {
        val context = getApplication<Application>()
        return when (e) {
            is NoActiveModelException -> context.getString(R.string.error_no_active_model)
            is AllAiModelsFailedException -> context.getString(R.string.error_all_ai_models_failed)
            is LocalModelMissingException -> context.getString(R.string.error_local_model_missing)
            is UnsupportedStrategyException -> context.getString(R.string.error_unsupported_strategy)
            else -> "${context.getString(R.string.ai_error_prefix)} ${e.localizedMessage.orEmpty()}"
        }
    }

    private fun decodeIds(encodedIds: String?): List<Long> {
        if (encodedIds.isNullOrBlank()) return emptyList()
        val decoded = URLDecoder.decode(encodedIds, StandardCharsets.UTF_8.toString())
        return decoded.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .distinct()
    }

    companion object {
        const val ARG_MODE = "mode"
        const val ARG_IDS = "ids"
    }
}
