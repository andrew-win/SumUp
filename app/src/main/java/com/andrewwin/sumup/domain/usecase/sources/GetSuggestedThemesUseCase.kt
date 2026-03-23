package com.andrewwin.sumup.domain.usecase.sources

import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.remote.datasource.RemoteArticleDataSource
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.EmbeddingService
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
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

enum class SuggestedTheme(val title: String, val sources: List<ThemeSource>, val anchors: List<String>) {
    SPORT(
        "Спорт",
        listOf(
            ThemeSource("t.me/right_inside_ua", SourceType.TELEGRAM),
            ThemeSource("https://www.suspilne.media/sport/rss/latest.rss", SourceType.RSS),
            ThemeSource("https://www.rss.ua.tribuna.com/ru/feed.xml", SourceType.RSS)
        ),
        listOf(
            "Збірна виграла матч у фіналі чемпіонату з футболу.",
            "Спортсмен здобув золоту медаль на олімпійських іграх.",
            "Баскетбольна команда перемогла у турнірі.",
            "футбол баскетбол олімпіада турнір чемпіонат гравець тренер матч"
        )
    ),
    TECH(
        "Технології",
        listOf(
            ThemeSource("https://www.ilenta.com/uk/news/news.rss", SourceType.RSS),
            ThemeSource("https://www.mezha.ua/feed/", SourceType.RSS),
            ThemeSource("https://www.holosameryky.com/api/zvoypl-vomx-tpeuktm", SourceType.RSS)
        ),
        listOf(
            "Процесор із вбудованим відеоядром: представили значно потужнішу модель.",
            "Компанія представила революційний смартфон з новим чипом із можливостями штучного інтелекту.",
            "Інновація у сфері космосу: дослідники відкрили нову планету за допомогою телескопа.",
            "технології наука штучний інтелект гаджети програмування дослідження"
        )
    ),
    POLITICS(
        "Політика",
        listOf(
            ThemeSource("https://www.rbc.ua/static/rss/ukrnet.politics.ukr.rss.xml", SourceType.RSS),
            ThemeSource("https://www.suspilne.media/rss/all.rss", SourceType.RSS),
            ThemeSource("https://www.holosameryky.com/api/zqoy_l-vomx-tpeikty", SourceType.RSS)
        ),
        listOf(
            "Парламент прийняв новий закон після голосування.",
            "Президент підписав указ і призначив нового міністра.",
            "Опозиція вимагає проведення дострокових виборів.",
            "вибори уряд парламент депутат міністр президент закон голосування партія"
        )
    ),
    WEATHER(
        "Погода",
        listOf(
            ThemeSource("t.me/uhmc2022", SourceType.TELEGRAM),
            ThemeSource("t.me/lviv_rchm", SourceType.TELEGRAM),
            ThemeSource("t.me/HMC_Odesa", SourceType.TELEGRAM)
        ),
        listOf(
            "Синоптики прогнозують сильний дощ і похолодання.",
            "Завтра очікується сніг та мороз до -15 градусів.",
            "Циклон принесе штормовий вітер і грозу.",
            "температура опади сніг дощ вітер хмарно сонячно прогноз погоди"
        )
    ),
    ECONOMY(
        "Економіка і фінанси",
        listOf(
            ThemeSource("https://www.holosameryky.com/api/zpoytl-vomx-tpe_ktr", SourceType.RSS),
            ThemeSource("t.me/fair_price_channel", SourceType.TELEGRAM),
            ThemeSource("https://t.me/MSUa_official", SourceType.TELEGRAM)
        ),
        listOf(
            "Національний банк підвищив облікову ставку до 15 відсотків.",
            "Курс долара зріс на міжбанківському ринку.",
            "ВВП країни скоротився на два відсотки у третьому кварталі.",
            "гривня долар євро інфляція бюджет кредит банк ставка ринок економіка"
        )
    ),
    HEALTH(
        "Здоров'я та медицина",
        listOf(
            ThemeSource("https://t.me/PHC_Ukraine", SourceType.TELEGRAM),
            ThemeSource("https://t.me/zemits_ukraine_official", SourceType.TELEGRAM),
        ),
        listOf(
            "Лікарі розробили нову методику лікування онкологічних захворювань.",
            "МОЗ рекомендує вакцинуватися від грипу перед зимовим сезоном.",
            "Вчені провели клінічні випробування нового препарату.",
            "лікар лікарня вакцина хвороба лікування медицина здоров'я препарат вірус"
        )
    ),
    CRYPTO(
        "Крипта",
        listOf(
            ThemeSource("https://t.me/probitcoinua", SourceType.TELEGRAM),
            ThemeSource("https://https://t.me/Kripta_Ukraina", SourceType.TELEGRAM),
        ),
        listOf(
            "Біткоїн досяг нового історичного максимуму і перевищив сто тисяч доларів.",
            "Ethereum оновив мережу і знизив комісії за транзакції.",
            "SEC схвалила перший біткоїн ETF для роздрібних інвесторів.",
            "біткоїн ethereum крипто блокчейн токен defi гаманець майнінг altcoin"
        )
    ),
    SPACE(
        "Космос",
        listOf(
            ThemeSource("https://www.nasa.gov/rss/dyn/breaking_news.rss", SourceType.RSS),
            ThemeSource("https://www.space.com/feeds/all", SourceType.RSS)
        ),
        listOf(
            "SpaceX успішно запустила ракету Starship на навколоземну орбіту.",
            "Телескоп Джеймс Вебб сфотографував нову екзопланету.",
            "Астронавти МКС провели вихід у відкритий космос для ремонту.",
            "ракета орбіта NASA SpaceX астронавт супутник місяць марс телескоп запуск"
        )
    ),
    CARS(
        "Автомобілі",
        listOf(
            ThemeSource("https://t.me/w8shippingukraine", SourceType.TELEGRAM),
            ThemeSource("https://https://t.me/AUTOSCOUTCOMUA", SourceType.TELEGRAM)
        ),
        listOf(
            "Tesla представила нову модель електромобіля з запасом ходу 700 км.",
            "BMW відкликає тисячі автомобілів через несправність гальм.",
            "Продажі електромобілів в Україні зросли на тридцять відсотків.",
            "автомобіль електромобіль tesla двигун салон тест-драйв колеса кузов пробіг"
        )
    ),
    BOOKS(
        "Книги та література",
        listOf(
            ThemeSource("https://t.me/ukrlib", SourceType.TELEGRAM),
            ThemeSource("https://t.me/booksua11", SourceType.TELEGRAM)
        ),
        listOf(
            "Новий роман відомого письменника став бестселером місяця.",
            "Букерівську премію отримала книга про події Другої світової війни.",
            "Видавництво випустило перший переклад класичного твору українською.",
            "книга роман автор письменник видавництво читання бестселер премія літературa"
        )
    ),
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
    private val aiRepository: AiRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
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
        val lastRecommendationAt = prefs.getLong(KEY_LAST_RECOMMENDATION_AT, 0L)
        val now = System.currentTimeMillis()
        val shouldRecalculate = forceRefresh ||
            savedThemeUrls == null ||
            currentSourcesHash != lastSavedHash ||
            (now - lastRecommendationAt) >= ONE_DAY_MS

        if (!shouldRecalculate) {
            val cached = SuggestedTheme.entries.map {
                ThemeSuggestion(
                    it,
                    score = 10f,
                    isSubscribed = it.sources.all { s -> allSourcesUrls.contains(s.url) },
                    isRecommended = savedThemeUrls?.contains(it.title) == true
                )
            }.sortedByDescending { if (it.isRecommended) 1 else 0 }
            emit(cached)
            return@flow
        }

        val hasCloudEmbedding = aiRepository.hasEnabledEmbeddingConfig()
        val isModelLoaded = hasCloudEmbedding || manageModelUseCase.isModelExists()
        Log.d(tag, "vectorization available: $isModelLoaded, cloud=$hasCloudEmbedding")

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

        val localEmbeddingReady = if (hasCloudEmbedding) {
            true
        } else {
            val modelPath = manageModelUseCase.getModelPath()
            embeddingService.initialize(modelPath)
        }
        Log.d(tag, "Embedding service initialized: $localEmbeddingReady")
        if (!localEmbeddingReady) {
            emit(SuggestedTheme.entries.map { 
                ThemeSuggestion(it, 0f, isSubscribed = it.sources.all { s -> allSourcesUrls.contains(s.url) })
            })
            return@flow
        }

        val prefsData = userPreferencesRepository.preferences.first()
        val simThreshold = (
            if (hasCloudEmbedding) {
                prefsData.cloudDeduplicationThreshold - 0.01f
            } else {
                prefsData.localDeduplicationThreshold - 0.2f
            }
        ).coerceIn(0f, 0.99f)

        Log.d(tag, "Calculating embeddings for user articles...")
        val userEmbeddings = userArticles.mapNotNull { 
           val emb = getEmbedding(it.title, hasCloudEmbedding)
           it.title to normalize(emb)
        }

        Log.d(tag, "Calculating static anchor embeddings for all themes...")
        val staticAnchorEmbeddings: Map<SuggestedTheme, FloatArray> = SuggestedTheme.entries.associateWith { theme ->
            val anchorEmbs = theme.anchors.map { normalize(getEmbedding(it, hasCloudEmbedding)) }
            val vectorSize = anchorEmbs.first().size
            val sumVector = FloatArray(vectorSize)
            for (emb in anchorEmbs) {
                for (i in 0 until vectorSize) sumVector[i] += emb[i]
            }
            normalize(FloatArray(vectorSize) { sumVector[it] / anchorEmbs.size })
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
                    remoteArticleDataSource.fetchArticles(
                        Source(
                            id = -1L,
                            groupId = -1L,
                            name = firstSource.url,
                            url = firstSource.url,
                            type = firstSource.type
                        )
                    )
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
                    val emb = getEmbedding(titleForEmbedding, hasCloudEmbedding)
                    normalize(emb)
                }

            if (themeEmbeddings.isEmpty()) {
                Log.d(tag, "No valid theme embeddings for: ${theme.title}")
                suggestions.add(ThemeSuggestion(theme, 0f, isSubscribed = theme.sources.all { allSourcesUrls.contains(it.url) }))
                continue
            }

            // Combine static anchors + fetched article embeddings into one anchor vector
            val staticEmb = staticAnchorEmbeddings[theme]!!
            val vectorSize = staticEmb.size
            val allEmbs = themeEmbeddings + listOf(staticEmb)
            val sumVector = FloatArray(vectorSize)
            for (emb in allEmbs) {
                for (i in 0 until vectorSize) {
                    sumVector[i] += emb[i]
                }
            }
            val themeAnchorEmb = normalize(FloatArray(vectorSize) { sumVector[it] / allEmbs.size })
            Log.d(tag, "Anchor for [${theme.title}]: combined ${themeEmbeddings.size} articles + ${theme.anchors.size} static anchors")
            
            var matchScore = 0f
            for ((uTitle, uEmb) in userEmbeddings) {
                val sim = dotProduct(uEmb, themeAnchorEmb)
                if (sim >= simThreshold) {
                    matchScore += sim
                    Log.d(tag, "MATCH [${theme.title}] '$uTitle' sim=$sim")
                } else {
                    Log.d(tag, "No match [${theme.title}] '$uTitle' sim=$sim")
                }
            }
            Log.d(tag, "Theme: ${theme.title}, Final Match Score: $matchScore")
            suggestions.add(ThemeSuggestion(theme, matchScore, isSubscribed = theme.sources.all { allSourcesUrls.contains(it.url) }))
        }

        val sorted = suggestions.sortedByDescending { it.score }
        val resultList = sorted.map { it.copy(isRecommended = it.score >= 1.5f) }.sortedByDescending { if (it.isRecommended) 1 else 0 }
        Log.d(tag, "Final suggested themes: ${resultList.map { it.theme.title to it.score }}")
        
        prefs.edit()
             .putInt("sourcesHash", currentSourcesHash)
             .putStringSet("savedThemes", resultList.filter { it.isRecommended }.map { it.theme.title }.toSet())
             .putLong(KEY_LAST_RECOMMENDATION_AT, now)
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

    private suspend fun getEmbedding(text: String, hasCloudEmbedding: Boolean): FloatArray {
        if (hasCloudEmbedding) {
            aiRepository.embed(text)?.let { return it }
        }
        return embeddingService.getEmbedding(text)
    }

    companion object {
        private const val KEY_LAST_RECOMMENDATION_AT = "lastRecommendationAt"
        private const val ONE_DAY_MS = 24L * 60 * 60 * 1000
    }
}
