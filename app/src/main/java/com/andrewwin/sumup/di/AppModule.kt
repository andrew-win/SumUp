package com.andrewwin.sumup.di

import android.content.Context
import androidx.work.WorkManager
import com.andrewwin.sumup.data.local.AppDatabase
import com.andrewwin.sumup.data.local.dao.AiModelDao
import com.andrewwin.sumup.data.local.dao.ArticleDao
import com.andrewwin.sumup.data.local.dao.ArticleSimilarityDao
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.dao.SummaryDao
import com.andrewwin.sumup.data.local.dao.UserPreferencesDao
import com.andrewwin.sumup.data.cache.InMemoryFeedCache
import com.andrewwin.sumup.data.remote.AiService
import com.andrewwin.sumup.data.remote.RssParser
import com.andrewwin.sumup.data.remote.TelegramParser
import com.andrewwin.sumup.data.remote.YouTubeParser
import com.andrewwin.sumup.data.remote.datasource.RemoteArticleDataSource
import com.andrewwin.sumup.data.repository.AiRepositoryImpl
import com.andrewwin.sumup.data.repository.ArticleRepositoryImpl
import com.andrewwin.sumup.data.repository.ModelRepositoryImpl
import com.andrewwin.sumup.data.repository.SourceRepositoryImpl
import com.andrewwin.sumup.data.repository.SummaryRepositoryImpl
import com.andrewwin.sumup.data.repository.UserPreferencesRepositoryImpl
import com.andrewwin.sumup.data.provider.AiPromptProviderImpl
import com.andrewwin.sumup.domain.ArticleImportanceScorer
import com.andrewwin.sumup.domain.DeduplicationService
import com.andrewwin.sumup.domain.repository.AiRepository
import com.andrewwin.sumup.domain.repository.ArticleRepository
import com.andrewwin.sumup.domain.repository.FeedCache
import com.andrewwin.sumup.domain.repository.ModelRepository
import com.andrewwin.sumup.domain.repository.SourceRepository
import com.andrewwin.sumup.domain.repository.SummaryRepository
import com.andrewwin.sumup.domain.repository.UserPreferencesRepository
import com.andrewwin.sumup.domain.provider.AiPromptProvider
import com.andrewwin.sumup.domain.usecase.GenerateSummaryUseCase
import com.andrewwin.sumup.domain.usecase.GenerateSummaryUseCaseImpl
import com.andrewwin.sumup.domain.usecase.RefreshArticlesUseCase
import com.andrewwin.sumup.domain.usecase.RefreshArticlesUseCaseImpl
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCase
import com.andrewwin.sumup.domain.usecase.settings.ManageModelUseCaseImpl
import com.andrewwin.sumup.domain.repository.SummaryScheduler
import com.andrewwin.sumup.data.local.scheduler.SummarySchedulerImpl
import com.andrewwin.sumup.domain.usecase.CleanArticleTextUseCase
import com.andrewwin.sumup.domain.usecase.BuildExtractiveSummaryUseCase
import com.andrewwin.sumup.domain.usecase.FormatArticleHeadlineUseCase
import com.andrewwin.sumup.domain.usecase.ai.FormatExtractiveSummaryUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
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
    fun provideSourceDao(db: AppDatabase): SourceDao = db.sourceDao()

    @Provides
    fun provideAiModelDao(db: AppDatabase): AiModelDao = db.aiModelDao()

    @Provides
    fun provideSummaryDao(db: AppDatabase): SummaryDao = db.summaryDao()

    @Provides
    fun provideUserPreferencesDao(db: AppDatabase): UserPreferencesDao = db.userPreferencesDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    @Singleton
    fun provideAiService(okHttpClient: OkHttpClient): AiService = AiService(okHttpClient)

    @Provides
    fun provideRssParser(): RssParser = RssParser()

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
    ): RemoteArticleDataSource = RemoteArticleDataSource(okHttpClient, rssParser, telegramParser, youtubeParser)

    @Provides
    @Singleton
    fun provideArticleRepository(
        articleDao: ArticleDao,
        articleSimilarityDao: ArticleSimilarityDao,
        sourceDao: SourceDao,
        remoteArticleDataSource: RemoteArticleDataSource,
        cleanArticleTextUseCase: CleanArticleTextUseCase
    ): ArticleRepository = ArticleRepositoryImpl(
        articleDao,
        articleSimilarityDao,
        sourceDao,
        remoteArticleDataSource,
        cleanArticleTextUseCase
    )

    @Provides
    @Singleton
    fun provideSourceRepository(
        sourceDao: SourceDao,
        remoteArticleDataSource: RemoteArticleDataSource
    ): SourceRepository = SourceRepositoryImpl(sourceDao, remoteArticleDataSource)

    @Provides
    @Singleton
    fun provideAiRepository(
        aiModelDao: AiModelDao,
        userPreferencesDao: UserPreferencesDao,
        aiService: AiService,
        formatExtractiveSummaryUseCase: FormatExtractiveSummaryUseCase,
        aiPromptProvider: AiPromptProvider
    ): AiRepository = AiRepositoryImpl(
        aiModelDao,
        userPreferencesDao,
        aiService,
        formatExtractiveSummaryUseCase,
        aiPromptProvider
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
    fun provideManageModelUseCase(modelRepository: ModelRepository): ManageModelUseCase =
        ManageModelUseCaseImpl(modelRepository)

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideArticleImportanceScorer(): ArticleImportanceScorer = ArticleImportanceScorer()

    @Provides
    @Singleton
    fun provideDeduplicationService(articleRepository: ArticleRepository): DeduplicationService =
        DeduplicationService(articleRepository)

    @Provides
    @Singleton
    fun provideFeedCache(): FeedCache = InMemoryFeedCache()

    @Provides
    @Singleton
    fun provideRefreshArticlesUseCase(articleRepository: ArticleRepository): RefreshArticlesUseCase =
        RefreshArticlesUseCaseImpl(articleRepository)

    @Provides
    @Singleton
    fun provideFormatArticleHeadlineUseCase(): FormatArticleHeadlineUseCase = FormatArticleHeadlineUseCase()

    @Provides
    @Singleton
    fun provideFormatExtractiveSummaryUseCase(@dagger.hilt.android.qualifiers.ApplicationContext context: Context): FormatExtractiveSummaryUseCase = 
        FormatExtractiveSummaryUseCase(context)

    @Provides
    @Singleton
    fun provideBuildExtractiveSummaryUseCase(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
        formatExtractiveSummaryUseCase: FormatExtractiveSummaryUseCase
    ): BuildExtractiveSummaryUseCase = BuildExtractiveSummaryUseCase(context, formatExtractiveSummaryUseCase)

    @Provides
    @Singleton
    fun provideAiPromptProvider(
        @ApplicationContext context: Context
    ): AiPromptProvider = AiPromptProviderImpl(context)

    @Provides
    @Singleton
    fun provideGenerateSummaryUseCase(
        articleRepository: ArticleRepository,
        aiRepository: AiRepository,
        formatArticleHeadlineUseCase: FormatArticleHeadlineUseCase,
        buildExtractiveSummaryUseCase: BuildExtractiveSummaryUseCase,
        userPreferencesRepository: UserPreferencesRepository
    ): GenerateSummaryUseCase = GenerateSummaryUseCaseImpl(
        articleRepository, 
        aiRepository, 
        formatArticleHeadlineUseCase, 
        buildExtractiveSummaryUseCase,
        userPreferencesRepository
    )

    @Provides
    @Singleton
    fun provideSummaryScheduler(workManager: WorkManager): SummaryScheduler =
        SummarySchedulerImpl(workManager)
}
