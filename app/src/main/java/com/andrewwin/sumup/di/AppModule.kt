package com.andrewwin.sumup.di

import android.content.Context
import androidx.work.WorkManager
import com.andrewwin.sumup.data.mappers.AiSummaryResponseMapper
import com.andrewwin.sumup.data.ai.CloudAiRequestSender
import com.andrewwin.sumup.data.ai.CloudEmbeddingGenerator
import com.andrewwin.sumup.data.ai.LocalEmbeddingService
import com.andrewwin.sumup.data.export.AndroidPdfExportService
import com.andrewwin.sumup.data.local.AppDatabase
import com.andrewwin.sumup.data.local.dao.AiModelDao
import com.andrewwin.sumup.data.local.dao.ArticleDao
import com.andrewwin.sumup.data.local.dao.ArticleSimilarityDao
import com.andrewwin.sumup.data.local.dao.FeedClusterSnapshotDao
import com.andrewwin.sumup.data.local.dao.PreparedScheduledSummaryDao
import com.andrewwin.sumup.data.local.dao.SavedArticleDao
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.dao.SummaryDao
import com.andrewwin.sumup.data.local.dao.UserPreferencesDao
import com.andrewwin.sumup.data.local.scheduler.ScheduledSummaryTimeCalculator
import com.andrewwin.sumup.data.local.scheduler.SummarySchedulerImpl
import com.andrewwin.sumup.data.news.ArticleTextCleaner
import com.andrewwin.sumup.data.provider.AppDispatcherProvider
import com.andrewwin.sumup.data.remote.AiService
import com.andrewwin.sumup.data.remote.ai.*
import com.andrewwin.sumup.domain.entities.ai.AiProvider
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.remote.RemoteArticleDataSource
import com.andrewwin.sumup.data.remote.RemoteSourceDataSource
import com.andrewwin.sumup.data.remote.rss.RssRemoteDataSource
import com.andrewwin.sumup.data.remote.telegram.TelegramRemoteDataSource
import com.andrewwin.sumup.data.remote.youtube.YouTubeRemoteDataSource
import com.andrewwin.sumup.data.remote.RemoteFullContent
import com.andrewwin.sumup.data.remote.RssParser
import com.andrewwin.sumup.data.remote.TelegramParser
import com.andrewwin.sumup.data.remote.YouTubeParser
import com.andrewwin.sumup.data.repository.ArticleRepositoryImpl
import com.andrewwin.sumup.data.repository.FeedClusterSnapshotStore
import com.andrewwin.sumup.data.repository.ModelRepositoryImpl
import com.andrewwin.sumup.data.repository.PublicSubscriptionsSyncManager
import com.andrewwin.sumup.data.repository.SourceRepositoryImpl
import com.andrewwin.sumup.data.repository.SuggestedThemesStateRepositoryImpl
import com.andrewwin.sumup.data.repository.SummaryRepositoryImpl
import com.andrewwin.sumup.data.repository.UserPreferencesRepositoryImpl
import com.andrewwin.sumup.data.security.SecretEncryptionManager
import com.andrewwin.sumup.domain.ai.AiRequestSender
import com.andrewwin.sumup.domain.ai.CloudEmbeddingProvider
import com.andrewwin.sumup.domain.ai.LocalEmbeddingProvider
import com.andrewwin.sumup.domain.ai.LocalModelManager
import com.andrewwin.sumup.domain.ai.LocalModelManagerImpl
import com.andrewwin.sumup.domain.ai.SummaryResponseMapper
import com.andrewwin.sumup.domain.export.PdfExportService
import com.andrewwin.sumup.domain.feed.UpdateArticlesFromSources
import com.andrewwin.sumup.domain.feed.UpdateArticlesFromSourcesImpl
import com.andrewwin.sumup.domain.feed.dedup.FeedDeduplicationProcessor
import com.andrewwin.sumup.domain.news.ArticleContentCleaner
import com.andrewwin.sumup.domain.news.ArticleImportanceScorer
import com.andrewwin.sumup.domain.news.ArticleTitleFormatter
import com.andrewwin.sumup.domain.news.DedupRuntimeCoordinator
import com.andrewwin.sumup.domain.news.SimilarityScorer
import com.andrewwin.sumup.domain.repository.AiModelConfigRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.FeedClusterSnapshotRepository
import com.andrewwin.sumup.domain.repository.ModelRepository
import com.andrewwin.sumup.domain.repository.PublicSubscriptionsCatalog
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.SuggestedThemesStateRepository
import com.andrewwin.sumup.domain.repository.SummaryRepository
import com.andrewwin.sumup.domain.repository.SummaryScheduler
import com.andrewwin.sumup.domain.repository.UserDataSyncRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.summary.ExtractiveSummaryService
import com.andrewwin.sumup.domain.summary.ExtractiveSummaryTextFormatter
import com.andrewwin.sumup.domain.summary.SummaryResultFormatter
import com.andrewwin.sumup.domain.summary.scheduled.DefaultScheduledSummaryTextGenerator
import com.andrewwin.sumup.domain.summary.scheduled.ScheduledSummaryResultProvider
import com.andrewwin.sumup.domain.summary.scheduled.ScheduledSummaryTextGenerator
import com.andrewwin.sumup.domain.usecase.feed.RefreshFeedUseCase
import com.andrewwin.sumup.domain.usecase.feed.RefreshFeedUseCaseImpl
import com.andrewwin.sumup.domain.usecase.sources.GetRecommendationsUseCase
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
    fun provideArticleSimilarityDao(db: AppDatabase): ArticleSimilarityDao = db.articleSimilarityDao()

    @Provides
    fun provideFeedClusterSnapshotDao(db: AppDatabase): FeedClusterSnapshotDao = db.feedClusterSnapshotDao()

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
    fun provideRssParser(
        @Named(NEWS_OK_HTTP_CLIENT) okHttpClient: OkHttpClient
    ): RssParser = RssParser(okHttpClient)

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
        val handlers = mapOf(
            SourceType.RSS to RssRemoteDataSource(okHttpClient, displayNameOkHttpClient, rssParser),
            SourceType.TELEGRAM to TelegramRemoteDataSource(okHttpClient, displayNameOkHttpClient, telegramParser),
            SourceType.YOUTUBE to YouTubeRemoteDataSource(okHttpClient, displayNameOkHttpClient, youtubeParser)
        )
        return RemoteArticleDataSource(handlers)
    }

    @Provides
    @Singleton
    fun provideArticleRepository(
        articleDao: ArticleDao,
        articleSimilarityDao: ArticleSimilarityDao,
        feedClusterSnapshotDao: FeedClusterSnapshotDao,
        savedArticleDao: SavedArticleDao,
        sourceDao: SourceDao,
        userPreferencesDao: UserPreferencesDao,
        remoteArticleDataSource: RemoteArticleDataSource,
        cleanArticleTextUseCase: ArticleContentCleaner,
        articleTitleFormatter: ArticleTitleFormatter,
        articleImportanceScorer: ArticleImportanceScorer
    ): ArticleRepository = ArticleRepositoryImpl(
        articleDao,
        articleSimilarityDao,
        feedClusterSnapshotDao,
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
    fun provideFeedClusterSnapshotRepository(
        impl: FeedClusterSnapshotStore
    ): FeedClusterSnapshotRepository = impl

    @Provides
    @Singleton
    fun provideUserDataSyncRepository(
        impl: com.andrewwin.sumup.data.sync.SettingsSyncService
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
    fun providePdfExportService(impl: AndroidPdfExportService): PdfExportService = impl

    @Provides
    @Singleton
    fun provideSummaryResponseMapper(impl: AiSummaryResponseMapper): SummaryResponseMapper = impl

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
        getRecommendationsUseCase: GetRecommendationsUseCase,
        suggestedThemesStateRepository: SuggestedThemesStateRepository,
        userPreferencesRepository: UserPreferencesRepository,
        dispatcherProvider: com.andrewwin.sumup.domain.support.DispatcherProvider
    ): RefreshFeedUseCase = RefreshFeedUseCaseImpl(
        updateArticlesFromSources = updateArticlesFromSources,
        feedDeduplicationProcessor = feedDeduplicationProcessor,
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
    ): SummaryScheduler = SummarySchedulerImpl(context, workManager, timeCalculator)

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
}
