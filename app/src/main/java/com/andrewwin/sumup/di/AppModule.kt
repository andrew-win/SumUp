package com.andrewwin.sumup.di

import android.content.Context
import androidx.work.WorkManager
import com.andrewwin.sumup.data.local.AppDatabase
import com.andrewwin.sumup.data.local.dao.AiModelDao
import com.andrewwin.sumup.data.local.dao.ArticleDao
import com.andrewwin.sumup.data.local.dao.ArticleSimilarityDao
import com.andrewwin.sumup.data.local.dao.SavedArticleDao
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.dao.SummaryDao
import com.andrewwin.sumup.data.local.dao.UserPreferencesDao
import com.andrewwin.sumup.data.local.scheduler.SummarySchedulerImpl
import com.andrewwin.sumup.data.provider.AiPromptProviderImpl
import com.andrewwin.sumup.data.remote.AiService
import com.andrewwin.sumup.data.remote.RssParser
import com.andrewwin.sumup.data.remote.TelegramParser
import com.andrewwin.sumup.data.remote.YouTubeParser
import com.andrewwin.sumup.data.remote.RemoteArticleDataSource
import com.andrewwin.sumup.data.repository.AiRepositoryImpl
import com.andrewwin.sumup.data.repository.ArticleRepositoryImpl
import com.andrewwin.sumup.data.repository.ModelRepositoryImpl
import com.andrewwin.sumup.data.repository.SourceRepositoryImpl
import com.andrewwin.sumup.data.repository.SuggestedThemesStateRepositoryImpl
import com.andrewwin.sumup.data.repository.SummaryRepositoryImpl
import com.andrewwin.sumup.data.repository.UserPreferencesRepositoryImpl
import com.andrewwin.sumup.data.security.SecretEncryptionManager
import com.andrewwin.sumup.domain.service.ArticleImportanceScorer
import com.andrewwin.sumup.domain.service.DeduplicationService
import com.andrewwin.sumup.domain.support.AiPromptProvider
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.ModelRepository
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.SuggestedThemesStateRepository
import com.andrewwin.sumup.domain.repository.SummaryRepository
import com.andrewwin.sumup.domain.repository.SummaryScheduler
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.usecase.common.BuildExtractiveSummaryUseCase
import com.andrewwin.sumup.domain.usecase.common.CleanArticleTextUseCase
import com.andrewwin.sumup.domain.usecase.common.CollectScheduledSummaryArticlesUseCase
import com.andrewwin.sumup.domain.usecase.common.FormatArticleHeadlineUseCase
import com.andrewwin.sumup.domain.usecase.common.GenerateSummaryUseCase
import com.andrewwin.sumup.domain.usecase.common.GenerateSummaryUseCaseImpl
import com.andrewwin.sumup.domain.usecase.common.RefreshArticlesUseCase
import com.andrewwin.sumup.domain.usecase.common.RefreshArticlesUseCaseImpl
import com.andrewwin.sumup.domain.usecase.ai.FormatExtractiveSummaryUseCase
import com.andrewwin.sumup.domain.usecase.ai.SummarizationEngineUseCase
import com.andrewwin.sumup.domain.usecase.feed.RefreshFeedUseCase
import com.andrewwin.sumup.domain.usecase.feed.RefreshFeedUseCaseImpl
import com.andrewwin.sumup.domain.usecase.sources.GetSuggestedThemesUseCase
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCase
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCaseImpl
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit
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
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val startNanos = System.nanoTime()
                val response = chain.proceed(request)
                val tookMs = (System.nanoTime() - startNanos) / 1_000_000
                response
            }
            .build()

    @Provides
    @Singleton
    fun provideAiService(okHttpClient: OkHttpClient): AiService = AiService(okHttpClient)

    @Provides
    fun provideRssParser(okHttpClient: OkHttpClient): RssParser = RssParser(okHttpClient)

    @Provides
    fun provideTelegramParser(): TelegramParser = TelegramParser()

    @Provides
    fun provideYouTubeParser(): YouTubeParser = YouTubeParser()

    @Provides
    @Singleton
    fun provideRemoteArticleDataSource(
        okHttpClient: OkHttpClient,
        rssParser: RssParser,
        telegramParser: TelegramParser,
        youtubeParser: YouTubeParser
    ): RemoteArticleDataSource = RemoteArticleDataSource(
        okHttpClient,
        rssParser,
        telegramParser,
        youtubeParser
    )

    @Provides
    @Singleton
    fun provideArticleRepository(
        articleDao: ArticleDao,
        articleSimilarityDao: ArticleSimilarityDao,
        savedArticleDao: SavedArticleDao,
        sourceDao: SourceDao,
        userPreferencesDao: UserPreferencesDao,
        remoteArticleDataSource: RemoteArticleDataSource,
        cleanArticleTextUseCase: CleanArticleTextUseCase
    ): ArticleRepository = ArticleRepositoryImpl(
        articleDao,
        articleSimilarityDao,
        savedArticleDao,
        sourceDao,
        userPreferencesDao,
        remoteArticleDataSource,
        cleanArticleTextUseCase
    )

    @Provides
    @Singleton
    fun provideSourceRepository(
        sourceDao: SourceDao,
        remoteArticleDataSource: RemoteArticleDataSource,
        cleanArticleTextUseCase: CleanArticleTextUseCase
    ): SourceRepository = SourceRepositoryImpl(
        sourceDao,
        remoteArticleDataSource,
        cleanArticleTextUseCase
    )

    @Provides
    @Singleton
    fun provideAiRepository(
        aiModelDao: AiModelDao,
        userPreferencesDao: UserPreferencesDao,
        aiService: AiService,
        formatExtractiveSummaryUseCase: FormatExtractiveSummaryUseCase,
        aiPromptProvider: AiPromptProvider,
        secretEncryptionManager: SecretEncryptionManager
    ): AiRepository = AiRepositoryImpl(
        aiModelDao,
        userPreferencesDao,
        aiService,
        formatExtractiveSummaryUseCase,
        aiPromptProvider,
        secretEncryptionManager
    )

    @Provides
    @Singleton
    fun provideModelRepository(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): ModelRepository = ModelRepositoryImpl(context, okHttpClient)

    @Provides
    @Singleton
    fun provideSummaryRepository(summaryDao: SummaryDao): SummaryRepository =
        SummaryRepositoryImpl(summaryDao)

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
    fun provideManageModelUseCase(modelRepository: ModelRepository): ManageModelUseCase =
        ManageModelUseCaseImpl(modelRepository)

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
    fun provideDeduplicationService(
        articleRepository: ArticleRepository,
        aiRepository: AiRepository
    ): DeduplicationService =
        DeduplicationService(articleRepository, aiRepository)

    @Provides
    @Singleton
    fun provideRefreshArticlesUseCase(articleRepository: ArticleRepository): RefreshArticlesUseCase =
        RefreshArticlesUseCaseImpl(articleRepository)

    @Provides
    @Singleton
    fun provideRefreshFeedUseCase(
        refreshArticlesUseCase: RefreshArticlesUseCase,
        getSuggestedThemesUseCase: GetSuggestedThemesUseCase,
        suggestedThemesStateRepository: SuggestedThemesStateRepository,
        dispatcherProvider: com.andrewwin.sumup.domain.support.DispatcherProvider
    ): RefreshFeedUseCase = RefreshFeedUseCaseImpl(
        refreshArticlesUseCase = refreshArticlesUseCase,
        getSuggestedThemesUseCase = getSuggestedThemesUseCase,
        suggestedThemesStateRepository = suggestedThemesStateRepository,
        dispatcherProvider = dispatcherProvider
    )

    @Provides
    @Singleton
    fun provideFormatArticleHeadlineUseCase(): FormatArticleHeadlineUseCase = FormatArticleHeadlineUseCase()

    @Provides
    @Singleton
    fun provideFormatExtractiveSummaryUseCase(): FormatExtractiveSummaryUseCase =
        FormatExtractiveSummaryUseCase()

    @Provides
    @Singleton
    fun provideCleanArticleTextUseCase(
        dispatcherProvider: com.andrewwin.sumup.domain.support.DispatcherProvider
    ): CleanArticleTextUseCase = CleanArticleTextUseCase(dispatcherProvider)

    @Provides
    @Singleton
    fun provideBuildExtractiveSummaryUseCase(
        formatExtractiveSummaryUseCase: FormatExtractiveSummaryUseCase,
        dispatcherProvider: com.andrewwin.sumup.domain.support.DispatcherProvider
    ): BuildExtractiveSummaryUseCase = BuildExtractiveSummaryUseCase(formatExtractiveSummaryUseCase, dispatcherProvider)

    @Provides
    @Singleton
    fun provideAiPromptProvider(
        @ApplicationContext context: Context
    ): AiPromptProvider = AiPromptProviderImpl(context)

    @Provides
    @Singleton
    fun provideGenerateSummaryUseCase(
        collectScheduledSummaryArticlesUseCase: CollectScheduledSummaryArticlesUseCase,
        userPreferencesRepository: UserPreferencesRepository,
        summarizationEngineUseCase: SummarizationEngineUseCase
    ): GenerateSummaryUseCase = GenerateSummaryUseCaseImpl(
        collectScheduledSummaryArticlesUseCase,
        userPreferencesRepository,
        summarizationEngineUseCase
    )

    @Provides
    @Singleton
    fun provideSummaryScheduler(workManager: WorkManager): SummaryScheduler =
        SummarySchedulerImpl(workManager)

    @Provides
    @Singleton
    fun provideEmbeddingService(): com.andrewwin.sumup.domain.repository.EmbeddingService =
        com.andrewwin.sumup.data.repository.EmbeddingServiceImpl()

    @Provides
    @Singleton
    fun provideDispatcherProvider(): com.andrewwin.sumup.domain.support.DispatcherProvider =
        com.andrewwin.sumup.data.coroutines.AppDispatcherProvider()
}






