package com.andrewwin.sumup.di

import android.content.Context
import androidx.work.WorkManager
import com.andrewwin.sumup.data.ai.prompt.AssetsPromptTemplateRepository
import com.andrewwin.sumup.data.mappers.AiSummaryResponseMapper
import com.andrewwin.sumup.data.remote.ai.CloudAiRequestSender
import com.andrewwin.sumup.data.remote.ai.CloudEmbeddingGenerator
import com.andrewwin.sumup.data.local.ai.LocalEmbeddingService
import com.andrewwin.sumup.data.export.PdfExportServiceImpl
import com.andrewwin.sumup.data.local.AppDatabase
import com.andrewwin.sumup.data.local.dao.AiModelDao
import com.andrewwin.sumup.data.local.dao.ArticleDao
import com.andrewwin.sumup.data.local.dao.ArticleEmbeddingDao
import com.andrewwin.sumup.data.local.dao.ArticleSimilarityDao
import com.andrewwin.sumup.data.local.dao.PreparedScheduledSummaryDao
import com.andrewwin.sumup.data.local.dao.SavedArticleDao
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.dao.SummaryDao
import com.andrewwin.sumup.data.local.dao.UserPreferencesDao
import com.andrewwin.sumup.data.local.scheduler.ScheduledSummaryTimeCalculator
import com.andrewwin.sumup.data.local.scheduler.ScheduledSummaryAlarmStore
import com.andrewwin.sumup.data.local.scheduler.SummarySchedulerImpl
import com.andrewwin.sumup.data.local.cleaners.ArticleTextCleaner
import com.andrewwin.sumup.data.provider.AppDispatcherProvider
import com.andrewwin.sumup.data.remote.ai.AiService
import com.andrewwin.sumup.domain.ai.model.AiProvider
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.sources.RemoteArticleDataSource
import com.andrewwin.sumup.data.remote.ai.handlers.ChatGPTHandler
import com.andrewwin.sumup.data.remote.ai.handlers.ClaudeHandler
import com.andrewwin.sumup.data.remote.ai.handlers.CohereHandler
import com.andrewwin.sumup.data.remote.ai.handlers.GeminiHandler
import com.andrewwin.sumup.data.remote.ai.handlers.GroqHandler
import com.andrewwin.sumup.data.remote.ai.handlers.OpenRouterHandler
import com.andrewwin.sumup.data.remote.firebase.sync.SettingsSyncService
import com.andrewwin.sumup.data.remote.sources.rss.RssFetcher
import com.andrewwin.sumup.data.remote.sources.rss.RssSourceGateway
import com.andrewwin.sumup.data.remote.sources.telegram.TelegramFetcher
import com.andrewwin.sumup.data.remote.sources.telegram.TelegramSourceGateway
import com.andrewwin.sumup.data.remote.sources.youtube.YouTubeFetcher
import com.andrewwin.sumup.data.remote.sources.youtube.YouTubeSourceGateway
import com.andrewwin.sumup.data.remote.sources.rss.RssParser
import com.andrewwin.sumup.data.remote.sources.telegram.TelegramParser
import com.andrewwin.sumup.data.remote.sources.youtube.YouTubeParser
import com.andrewwin.sumup.data.repository.ArticleRepositoryImpl
import com.andrewwin.sumup.data.repository.ModelRepositoryImpl
import com.andrewwin.sumup.data.repository.PublicSubscriptionsSyncManager
import com.andrewwin.sumup.data.repository.SourceRepositoryImpl
import com.andrewwin.sumup.data.repository.SuggestedThemesStateRepositoryImpl
import com.andrewwin.sumup.data.repository.SummaryRepositoryImpl
import com.andrewwin.sumup.data.repository.UserPreferencesRepositoryImpl
import com.andrewwin.sumup.data.security.SecretEncryptionManager
import com.andrewwin.sumup.domain.ai.service.AiRequestSender
import com.andrewwin.sumup.domain.ai.embedding.CloudEmbeddingProvider
import com.andrewwin.sumup.domain.ai.embedding.LocalEmbeddingProvider
import com.andrewwin.sumup.domain.ai.service.LocalModelManager
import com.andrewwin.sumup.domain.ai.service.LocalModelManagerImpl
import com.andrewwin.sumup.domain.ai.service.SummaryResponseMapper
import com.andrewwin.sumup.domain.export.service.PdfExportService
import com.andrewwin.sumup.domain.feed.pipeline.UpdateArticlesFromSources
import com.andrewwin.sumup.domain.feed.pipeline.UpdateArticlesFromSourcesImpl
import com.andrewwin.sumup.domain.feed.dedup.FeedDeduplicationProcessor
import com.andrewwin.sumup.domain.article.processing.ArticleContentCleaner
import com.andrewwin.sumup.domain.article.processing.ArticleImportanceScorer
import com.andrewwin.sumup.domain.article.processing.ArticleTitleFormatter
import com.andrewwin.sumup.domain.article.deduplication.DedupRuntimeCoordinator
import com.andrewwin.sumup.domain.article.deduplication.SimilarityScorer
import com.andrewwin.sumup.domain.ai.repository.AiModelConfigRepository
import com.andrewwin.sumup.domain.article.repository.ArticleRepository
import com.andrewwin.sumup.domain.ai.repository.ModelRepository
import com.andrewwin.sumup.domain.ai.prompt.PromptTemplateRepository
import com.andrewwin.sumup.domain.source.repository.PublicSubscriptionsCatalog
import com.andrewwin.sumup.domain.source.repository.SourceRepository
import com.andrewwin.sumup.domain.settings.repository.SuggestedThemesStateRepository
import com.andrewwin.sumup.domain.summary.repository.SummaryRepository
import com.andrewwin.sumup.domain.summary.repository.SummaryScheduler
import com.andrewwin.sumup.domain.sync.repository.UserDataSyncRepository
import com.andrewwin.sumup.domain.settings.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.summary.service.ExtractiveSummaryService
import com.andrewwin.sumup.domain.summary.formatter.ExtractiveSummaryTextFormatter
import com.andrewwin.sumup.domain.summary.formatter.SummaryResultFormatter
import com.andrewwin.sumup.domain.summary.scheduled.DefaultScheduledSummaryTextGenerator
import com.andrewwin.sumup.domain.summary.scheduled.ScheduledSummaryResultProvider
import com.andrewwin.sumup.domain.summary.scheduled.ScheduledSummaryTextGenerator
import com.andrewwin.sumup.domain.feed.usecase.RefreshFeedUseCase
import com.andrewwin.sumup.domain.feed.usecase.RefreshFeedUseCaseImpl
import com.andrewwin.sumup.domain.source.usecase.GetRecommendationsUseCase
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getDatabase(context)

    @Provides
    fun provideArticleDao(db: AppDatabase): ArticleDao = db.articleDao()

    @Provides
    fun provideArticleEmbeddingDao(db: AppDatabase): ArticleEmbeddingDao = db.articleEmbeddingDao()

    @Provides
    fun provideArticleSimilarityDao(db: AppDatabase): ArticleSimilarityDao = db.articleSimilarityDao()

    @Provides
    fun providePreparedScheduledSummaryDao(db: AppDatabase): PreparedScheduledSummaryDao =
        db.preparedScheduledSummaryDao()

    @Provides
    fun provideSavedArticleDao(db: AppDatabase): SavedArticleDao = db.savedArticleDao()

    @Provides
    fun provideSourceDao(db: AppDatabase): SourceDao = db.sourceDao()

    @Provides
    fun provideAiModelDao(db: AppDatabase): AiModelDao = db.aiModelDao()

    @Provides
    fun provideSummaryDao(db: AppDatabase): SummaryDao = db.summaryDao()

    @Provides
    fun provideUserPreferencesDao(db: AppDatabase): UserPreferencesDao = db.userPreferencesDao()

    @Provides
    @Singleton
    @Named(AI_OK_HTTP_CLIENT)
    fun provideAiOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(AI_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AI_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(AI_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(AI_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request())
            }
            .build()

    @Provides
    @Singleton
    @Named(NEWS_OK_HTTP_CLIENT)
    fun provideNewsOkHttpClient(
        @Named(AI_OK_HTTP_CLIENT) okHttpClient: OkHttpClient
    ): OkHttpClient = okHttpClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", NEWS_USER_AGENT)
                .header("Accept", NEWS_ACCEPT_HEADER)
                .header("Accept-Language", NEWS_ACCEPT_LANGUAGE_HEADER)
                .build()
            chain.proceed(request)
        }
        .connectTimeout(NEWS_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NEWS_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(NEWS_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(NEWS_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @Named(DISPLAY_NAME_OK_HTTP_CLIENT)
    fun provideDisplayNameOkHttpClient(
        @Named(AI_OK_HTTP_CLIENT) okHttpClient: OkHttpClient
    ): OkHttpClient = okHttpClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", NEWS_USER_AGENT)
                .header("Accept", NEWS_ACCEPT_HEADER)
                .header("Accept-Language", NEWS_ACCEPT_LANGUAGE_HEADER)
                .build()
            chain.proceed(request)
        }
        .connectTimeout(DISPLAY_NAME_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(DISPLAY_NAME_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(DISPLAY_NAME_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(DISPLAY_NAME_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideAiService(@Named(AI_OK_HTTP_CLIENT) okHttpClient: OkHttpClient): AiService {
        val handlers = mapOf(
            AiProvider.GEMINI to GeminiHandler(okHttpClient),
            AiProvider.CHATGPT to ChatGPTHandler(okHttpClient),
            AiProvider.GROQ to GroqHandler(okHttpClient),
            AiProvider.OPENROUTER to OpenRouterHandler(okHttpClient),
            AiProvider.CLAUDE to ClaudeHandler(okHttpClient),
            AiProvider.COHERE to CohereHandler(okHttpClient)
        )
        return AiService(handlers)
    }

    @Provides
    fun provideRssParser(): RssParser = RssParser()

    @Provides
    fun provideTelegramParser(): TelegramParser = TelegramParser()

    @Provides
    fun provideYouTubeParser(): YouTubeParser = YouTubeParser()

    @Provides
    @Singleton
    fun provideRemoteArticleDataSource(
        @Named(NEWS_OK_HTTP_CLIENT) okHttpClient: OkHttpClient,
        @Named(DISPLAY_NAME_OK_HTTP_CLIENT) displayNameOkHttpClient: OkHttpClient,
        rssParser: RssParser,
        telegramParser: TelegramParser,
        youtubeParser: YouTubeParser
    ): RemoteArticleDataSource {
        val gateways = mapOf(
            SourceType.RSS to RssSourceGateway(
                rssFetcher = RssFetcher(okHttpClient),
                displayNameRssFetcher = RssFetcher(displayNameOkHttpClient),
                rssParser = rssParser
            ),
            SourceType.TELEGRAM to TelegramSourceGateway(
                telegramFetcher = TelegramFetcher(okHttpClient),
                displayNameTelegramFetcher = TelegramFetcher(displayNameOkHttpClient),
                telegramParser = telegramParser
            ),
            SourceType.YOUTUBE to YouTubeSourceGateway(
                youtubeFetcher = YouTubeFetcher(okHttpClient),
                displayNameYouTubeFetcher = YouTubeFetcher(displayNameOkHttpClient),
                youtubeParser = youtubeParser
            )
        )
        return RemoteArticleDataSource(gateways)
    }

    @Provides
    @Singleton
    fun provideArticleRepository(
        articleDao: ArticleDao,
        articleEmbeddingDao: ArticleEmbeddingDao,
        articleSimilarityDao: ArticleSimilarityDao,
        savedArticleDao: SavedArticleDao,
        sourceDao: SourceDao,
        userPreferencesDao: UserPreferencesDao,
        remoteArticleDataSource: RemoteArticleDataSource,
        cleanArticleTextUseCase: ArticleContentCleaner,
        articleTitleFormatter: ArticleTitleFormatter,
        articleImportanceScorer: ArticleImportanceScorer
    ): ArticleRepository = ArticleRepositoryImpl(
        articleDao,
        articleEmbeddingDao,
        articleSimilarityDao,
        savedArticleDao,
        sourceDao,
        userPreferencesDao,
        remoteArticleDataSource,
        cleanArticleTextUseCase,
        articleTitleFormatter,
        articleImportanceScorer
    )

    @Provides
    @Singleton
    fun provideSourceRepository(
        sourceDao: SourceDao,
        remoteArticleDataSource: RemoteArticleDataSource,
        cleanArticleTextUseCase: ArticleContentCleaner
    ): SourceRepository = SourceRepositoryImpl(
        sourceDao,
        remoteArticleDataSource,
        cleanArticleTextUseCase
    )

    @Provides
    @Singleton
    fun provideAiModelConfigRepository(
        aiModelDao: AiModelDao,
        aiService: AiService,
        secretEncryptionManager: SecretEncryptionManager
    ): AiModelConfigRepository = com.andrewwin.sumup.data.repository.AiModelConfigRepositoryImpl(
        aiModelDao,
        aiService,
        secretEncryptionManager
    )

    @Provides
    @Singleton
    fun provideModelRepository(
        @ApplicationContext context: Context
    ): ModelRepository = ModelRepositoryImpl(context)

    @Provides
    @Singleton
    fun provideSummaryRepository(
        summaryDao: SummaryDao,
        preparedScheduledSummaryDao: PreparedScheduledSummaryDao
    ): SummaryRepository = SummaryRepositoryImpl(summaryDao, preparedScheduledSummaryDao)

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(userPreferencesDao: UserPreferencesDao): UserPreferencesRepository =
        UserPreferencesRepositoryImpl(userPreferencesDao)

    @Provides
    @Singleton
    fun provideSuggestedThemesStateRepository(
        @ApplicationContext context: Context
    ): SuggestedThemesStateRepository = SuggestedThemesStateRepositoryImpl(context)

    @Provides
    @Singleton
    fun provideUserDataSyncRepository(
        impl: SettingsSyncService
    ): UserDataSyncRepository = impl

    @Provides
    @Singleton
    fun provideLocalModelManager(modelRepository: ModelRepository): LocalModelManager =
        LocalModelManagerImpl(modelRepository)

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideArticleImportanceScorer(): ArticleImportanceScorer = ArticleImportanceScorer()

    @Provides
    @Singleton
    fun provideLocalEmbeddingProvider(@ApplicationContext context: Context): LocalEmbeddingProvider =
        LocalEmbeddingService(context)

    @Provides
    @Singleton
    fun provideCloudEmbeddingProvider(impl: CloudEmbeddingGenerator): CloudEmbeddingProvider = impl

    @Provides
    @Singleton
    fun provideAiRequestSender(impl: CloudAiRequestSender): AiRequestSender = impl

    @Provides
    @Singleton
    fun providePdfExportService(impl: PdfExportServiceImpl): PdfExportService = impl

    @Provides
    @Singleton
    fun provideSummaryResponseMapper(impl: AiSummaryResponseMapper): SummaryResponseMapper = impl

    @Provides
    @Singleton
    fun providePromptTemplateRepository(impl: AssetsPromptTemplateRepository): PromptTemplateRepository = impl

    @Provides
    @Singleton
    fun providePublicSubscriptionsCatalog(impl: PublicSubscriptionsSyncManager): PublicSubscriptionsCatalog = impl

    @Provides
    @Singleton
    fun provideSimilarityScorer(
        articleRepository: ArticleRepository,
        localEmbeddingProvider: LocalEmbeddingProvider,
        cloudEmbeddingProvider: CloudEmbeddingProvider,
        dedupRuntimeCoordinator: DedupRuntimeCoordinator
    ): SimilarityScorer = SimilarityScorer(
        articleRepository,
        localEmbeddingProvider,
        cloudEmbeddingProvider,
        dedupRuntimeCoordinator
    )

    @Provides
    @Singleton
    fun provideUpdateArticlesFromSources(articleRepository: ArticleRepository): UpdateArticlesFromSources =
        UpdateArticlesFromSourcesImpl(articleRepository)

    @Provides
    @Singleton
    fun provideRefreshFeedUseCase(
        updateArticlesFromSources: UpdateArticlesFromSources,
        feedDeduplicationProcessor: FeedDeduplicationProcessor,
        articleRepository: ArticleRepository,
        similarityScorer: SimilarityScorer,
        getRecommendationsUseCase: GetRecommendationsUseCase,
        suggestedThemesStateRepository: SuggestedThemesStateRepository,
        userPreferencesRepository: UserPreferencesRepository,
        dispatcherProvider: com.andrewwin.sumup.domain.support.DispatcherProvider
    ): RefreshFeedUseCase = RefreshFeedUseCaseImpl(
        updateArticlesFromSources = updateArticlesFromSources,
        feedDeduplicationProcessor = feedDeduplicationProcessor,
        articleRepository = articleRepository,
        similarityScorer = similarityScorer,
        getRecommendationsUseCase = getRecommendationsUseCase,
        suggestedThemesStateRepository = suggestedThemesStateRepository,
        userPreferencesRepository = userPreferencesRepository,
        dispatcherProvider = dispatcherProvider
    )

    @Provides
    @Singleton
    fun provideArticleTextCleaner(
        dispatcherProvider: com.andrewwin.sumup.domain.support.DispatcherProvider
    ): ArticleContentCleaner = ArticleTextCleaner(dispatcherProvider)

    @Provides
    @Singleton
    fun provideExtractiveSummaryTextFormatter(
        getExtractiveSummaryUseCase: ExtractiveSummaryService,
        dispatcherProvider: com.andrewwin.sumup.domain.support.DispatcherProvider
    ): ExtractiveSummaryTextFormatter = ExtractiveSummaryTextFormatter(
        getExtractiveSummaryUseCase,
        dispatcherProvider
    )

    @Provides
    @Singleton
    fun provideSummaryResultFormatter(): SummaryResultFormatter = SummaryResultFormatter()

    @Provides
    @Singleton
    fun provideScheduledSummaryTextGenerator(
        scheduledSummaryResultProvider: ScheduledSummaryResultProvider,
        formatSummaryResultUseCase: SummaryResultFormatter
    ): ScheduledSummaryTextGenerator = DefaultScheduledSummaryTextGenerator(
        scheduledSummaryResultProvider,
        formatSummaryResultUseCase
    )



    @Provides
    @Singleton
    fun provideSummaryScheduler(
        @ApplicationContext context: Context,
        workManager: WorkManager,
        timeCalculator: ScheduledSummaryTimeCalculator
    ): SummaryScheduler = SummarySchedulerImpl(
        context = context,
        workManager = workManager,
        timeCalculator = timeCalculator,
        alarmStore = ScheduledSummaryAlarmStore(context)
    )

    @Provides
    @Singleton
    fun provideScheduledSummaryTimeCalculator(): ScheduledSummaryTimeCalculator =
        ScheduledSummaryTimeCalculator()

    @Provides
    @Singleton
    fun provideDispatcherProvider(): com.andrewwin.sumup.domain.support.DispatcherProvider =
        AppDispatcherProvider()

    private const val AI_OK_HTTP_CLIENT = "aiOkHttpClient"
    private const val NEWS_OK_HTTP_CLIENT = "newsOkHttpClient"
    private const val DISPLAY_NAME_OK_HTTP_CLIENT = "displayNameOkHttpClient"
    private const val AI_CONNECT_TIMEOUT_SECONDS = 20L
    private const val AI_READ_TIMEOUT_SECONDS = 60L
    private const val AI_WRITE_TIMEOUT_SECONDS = 60L
    private const val AI_CALL_TIMEOUT_SECONDS = 75L
    private const val NEWS_CONNECT_TIMEOUT_SECONDS = 5L
    private const val NEWS_READ_TIMEOUT_SECONDS = 10L
    private const val NEWS_WRITE_TIMEOUT_SECONDS = 5L
    private const val NEWS_CALL_TIMEOUT_SECONDS = 12L
    private const val DISPLAY_NAME_TIMEOUT_SECONDS = 7L
    private const val NEWS_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val NEWS_ACCEPT_HEADER =
        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    private const val NEWS_ACCEPT_LANGUAGE_HEADER = "uk-UA,uk;q=0.9,en-US;q=0.8,en;q=0.7"
}
