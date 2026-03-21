package com.andrewwin.sumup.domain.usecase.sources

import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.datasource.RemoteArticleDataSource
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.EmbeddingService
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import android.util.Log
import android.content.Context
import android.content.SharedPreferences
import com.andrewwin.sumup.domain.ArticleImportanceScorer
import dagger.hilt.android.qualifiers.ApplicationContext

data class ThemeSource(val url: String, val type: SourceType)

enum class SuggestedTheme(val title: String, val sources: List<ThemeSource>) {
    SPORT(
        "Спорт", listOf(
            ThemeSource("t.me/right_inside_ua", SourceType.TELEGRAM),
            ThemeSource("https://www.suspilne.media/sport/rss/latest.rss", SourceType.RSS),
            ThemeSource("https://www.rss.ua.tribuna.com/ru/feed.xml", SourceType.RSS)
        )
    ),
    TECH(
        "Технології та наука", listOf(
            ThemeSource("https://www.ilenta.com/uk/news/news.rss", SourceType.RSS),
            ThemeSource("https://www.mezha.ua/feed/", SourceType.RSS),
            ThemeSource("https://www.holosameryky.com/api/zvoypl-vomx-tpeuktm", SourceType.RSS)
        )
    ),
    POLITICS(
        "Політика та суспільні події", listOf(
            ThemeSource("https://www.rbc.ua/static/rss/ukrnet.politics.ukr.rss.xml", SourceType.RSS),
            ThemeSource("https://www.suspilne.media/rss/all.rss", SourceType.RSS),
            ThemeSource("https://www.holosameryky.com/api/zqoy_l-vomx-tpeikty", SourceType.RSS)
        )
    ),
    WEATHER(
        "Погода", listOf(
            ThemeSource("t.me/uhmc2022", SourceType.TELEGRAM),
            ThemeSource("t.me/lviv_rchm", SourceType.TELEGRAM),
            ThemeSource("t.me/HMC_Odesa", SourceType.TELEGRAM)
        )
    )
}

data class ThemeSuggestion(
    val theme: SuggestedTheme,
    val score: Float,
    val isSubscribed: Boolean = false,
    val isRecommended: Boolean = false
)

@Singleton
class GetSuggestedThemesUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val sourceRepository: SourceRepository,
    private val embeddingService: EmbeddingService,
    private val manageModelUseCase: ManageModelUseCase,
    private val remoteArticleDataSource: RemoteArticleDataSource,
    private val articleImportanceScorer: ArticleImportanceScorer,
    @ApplicationContext private val context: Context
) {
    private val tag = "GetSuggestedThemes"
    private val prefs: SharedPreferences = context.getSharedPreferences("suggested_themes_prefs", Context.MODE_PRIVATE)

    operator fun invoke(forceRefresh: Boolean = false): Flow<List<ThemeSuggestion>> = flow {
        Log.d(tag, "Started GetSuggestedThemesUseCase (forceRefresh=$forceRefresh)")
        val groupsWithSources = sourceRepository.groupsWithSources.first()
        val allSources = groupsWithSources.flatMap { it.sources }
        val allSourcesUrls = allSources.map { it.url }.toSet()
        val sourceIdByUrl = allSources.associate { it.url to it.id }
        val currentSourcesHash = allSourcesUrls.hashCode()
        val sourceTypeMap = allSources.associate { it.id to it.type }

        val lastSavedHash = prefs.getInt("sourcesHash", -1)
        val savedThemeUrls = prefs.getStringSet("savedThemes", null)

        val isModelLoaded = manageModelUseCase.isModelExists()
        Log.d(tag, "isModelLoaded: $isModelLoaded")

        if (!isModelLoaded) {
            emit(SuggestedTheme.entries.map { 
                ThemeSuggestion(it, 0f, isSubscribed = it.sources.all { s -> allSourcesUrls.contains(s.url) })
            })
            return@flow
        }
        
        // If we have a cache and sources haven't changed, and not forcing refresh, return cache
        if (!forceRefresh && savedThemeUrls != null && currentSourcesHash == lastSavedHash) {
            Log.d(tag, "Returning themes from SharedPreferences.")
            val cached = SuggestedTheme.entries.map { 
                 ThemeSuggestion(it, score = 10f, isSubscribed = it.sources.all { s -> allSourcesUrls.contains(s.url) }, isRecommended = savedThemeUrls.contains(it.title)) 
            }.sortedByDescending { if (it.isRecommended) 1 else 0 }
            emit(cached)
            return@flow
        }

        // Initial emit: if no cache, emit all. If cache exists (even if out of date or forcing), emit it while recalculating
        if (savedThemeUrls == null) {
            emit(SuggestedTheme.entries.map { 
                ThemeSuggestion(it, 0f, isSubscribed = it.sources.all { s -> allSourcesUrls.contains(s.url) })
            })
        } else {
            val cached = SuggestedTheme.entries.map { 
                 ThemeSuggestion(it, score = 10f, isSubscribed = it.sources.all { s -> allSourcesUrls.contains(s.url) }, isRecommended = savedThemeUrls.contains(it.title)) 
            }.sortedByDescending { if (it.isRecommended) 1 else 0 }
            emit(cached)
        }
        
        val allEnabledArticles = articleRepository.getEnabledArticlesOnce()
        val userArticles = allEnabledArticles
            .groupBy { it.sourceId }
            .flatMap { entry -> 
                entry.value.sortedByDescending { it.publishedAt }
                     .filter { 
                         val importance = articleImportanceScorer.score(it, sourceTypeMap[it.sourceId] ?: SourceType.RSS)
                         importance >= ArticleImportanceScorer.IMPORTANCE_THRESHOLD
                     }
                     .take(7)
            }

        Log.d(tag, "Found ${userArticles.size} user articles to compare")
        if (userArticles.isEmpty()) {
            emit(SuggestedTheme.entries.map { 
                ThemeSuggestion(it, 0f, isSubscribed = it.sources.all { s -> allSourcesUrls.contains(s.url) })
            })
            return@flow
        }

        val modelPath = manageModelUseCase.getModelPath()
        val initialized = embeddingService.initialize(modelPath)
        Log.d(tag, "Embedding service initialized: $initialized")
        if (!initialized) {
            emit(SuggestedTheme.entries.map { 
                ThemeSuggestion(it, 0f, isSubscribed = it.sources.all { s -> allSourcesUrls.contains(s.url) })
            })
            return@flow
        }

        Log.d(tag, "Calculating embeddings for user articles...")
        val userEmbeddings = userArticles.mapNotNull { 
           val emb = embeddingService.getEmbedding(it.title)
           it.title to normalize(emb)
        }

        val suggestions = mutableListOf<ThemeSuggestion>()
        for (theme in SuggestedTheme.entries) {
            Log.d(tag, "Processing theme: ${theme.title}")
            val firstSource = theme.sources.first()
            
            // Optimization: if we already have articles for this source, use them instead of fetching
            val themeArticles = if (allSourcesUrls.contains(firstSource.url)) {
                val sId = sourceIdByUrl[firstSource.url]
                allEnabledArticles.filter { it.sourceId == sId }
            } else {
                try {
                    remoteArticleDataSource.fetchArticles(-1L, firstSource.url, firstSource.type)
                } catch (e: Exception) {
                    Log.e(tag, "Failed to fetch articles for theme ${theme.title}", e)
                    emptyList()
                }
            }

            if (themeArticles.isEmpty()) {
                Log.d(tag, "No articles found for theme: ${theme.title}")
                suggestions.add(ThemeSuggestion(theme, 0f, isSubscribed = theme.sources.all { allSourcesUrls.contains(it.url) }))
                continue
            }
            
            Log.d(tag, "Calculating embeddings for ${themeArticles.size} articles of theme: ${theme.title}")
            val themeEmbeddings = themeArticles
                .filter {
                    val importance = articleImportanceScorer.score(it, firstSource.type)
                    importance >= ArticleImportanceScorer.IMPORTANCE_THRESHOLD
                }
                .take(7)
                .mapNotNull { 
                    var titleForEmbedding = it.title
                    if (theme == SuggestedTheme.WEATHER) {
                        titleForEmbedding = titleForEmbedding.split(Regex("\\s+"))
                            .filter { word ->
                                val cleanWord = word.trim { !it.isLetter() }
                                cleanWord.isEmpty() || !cleanWord.first().isUpperCase()
                            }
                            .joinToString(" ")
                            .trim()
                        if (titleForEmbedding.isBlank()) titleForEmbedding = "погода"
                        Log.d(tag, "Weather processed title for embedding: '$titleForEmbedding' (original: '${it.title}')")
                    }
                    val emb = embeddingService.getEmbedding(titleForEmbedding)
                    it.title to normalize(emb)
                }
            
            var matchScore = 0f
            for ((uTitle, uEmb) in userEmbeddings) {
                var bestSim = 0f
                var bestMatchTitle = ""
                for ((tTitle, tEmb) in themeEmbeddings) {
                    val sim = dotProduct(uEmb, tEmb)
                    if (sim > bestSim) {
                        bestSim = sim
                        bestMatchTitle = tTitle
                    }
                }
                if (bestSim >= 0.4) {
                    matchScore += bestSim
                    Log.d(tag, "MATCH for theme [${theme.title}]! User article: '$uTitle' <-> Theme article: '$bestMatchTitle' == $bestSim")
                } else {
                    Log.d(tag, "No match for theme [${theme.title}] article: '$uTitle'. Best was '$bestMatchTitle' == $bestSim")
                }
            }
            Log.d(tag, "Theme: ${theme.title}, Final Match Score: $matchScore")
            suggestions.add(ThemeSuggestion(theme, matchScore, isSubscribed = theme.sources.all { allSourcesUrls.contains(it.url) }))
        }

        val sorted = suggestions.sortedByDescending { it.score }
        val resultList = sorted.map { it.copy(isRecommended = it.score >= 2f) }.sortedByDescending { if (it.isRecommended) 1 else 0 }
        Log.d(tag, "Final suggested themes: ${resultList.map { it.theme.title to it.score }}")
        
        prefs.edit()
             .putInt("sourcesHash", currentSourcesHash)
             .putStringSet("savedThemes", resultList.filter { it.isRecommended }.map { it.theme.title }.toSet())
             .apply()

        emit(resultList)
    }

    private fun normalize(vector: FloatArray): FloatArray {
        val mag = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        return if (mag > 0f) FloatArray(vector.size) { vector[it] / mag } else vector
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }
}
