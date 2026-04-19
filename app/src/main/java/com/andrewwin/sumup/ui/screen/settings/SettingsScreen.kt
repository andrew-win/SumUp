package com.andrewwin.sumup.ui.screen.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.data.local.entities.AiStrategy
import com.andrewwin.sumup.data.local.entities.AppLanguage
import com.andrewwin.sumup.data.local.entities.AppThemeMode
import com.andrewwin.sumup.data.local.entities.DeduplicationStrategy
import com.andrewwin.sumup.data.local.entities.SummaryLanguage
import com.andrewwin.sumup.ui.components.AppExplanationDialog
import com.andrewwin.sumup.ui.components.AppHelpToggleAction
import com.andrewwin.sumup.ui.components.AppTopBar
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import android.content.pm.PackageManager

internal const val SETTINGS_SWITCH_SCALE = 0.85f

val AiProvider.iconRes: Int
    get() = when (this) {
        AiProvider.GEMINI -> R.drawable.ic_gemini_ai_provider
        AiProvider.GROQ -> R.drawable.ic_groq_ai_provider
        AiProvider.OPENROUTER -> R.drawable.ic_openrouter_ai_provider
        AiProvider.COHERE -> R.drawable.ic_cohere_ai_provider
        AiProvider.CHATGPT -> R.drawable.ic_chatgpt_ai_provider
        AiProvider.CLAUDE -> R.drawable.ic_claude_ai_provider
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val summaryConfigs by viewModel.summaryConfigs.collectAsState()
    val embeddingConfigs by viewModel.embeddingConfigs.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val transferState by viewModel.transferState.collectAsState()
    val authUiState by viewModel.authUiState.collectAsState()
    val isCloudSyncEnabled by viewModel.isCloudSyncEnabled.collectAsState()
    val syncIntervalHours by viewModel.syncIntervalHours.collectAsState()
    val backupSelectionState by viewModel.backupSelection.collectAsState()
    
    var showConfigDialog by remember { mutableStateOf<Pair<AiModelConfig?, AiModelType>?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var summaryPrompt by remember(userPreferences.summaryPrompt) { mutableStateOf(userPreferences.summaryPrompt) }
    var isMergeImport by rememberSaveable { mutableStateOf(true) }
    var syncIntervalExpanded by remember { mutableStateOf(false) }
    
    var showClearArticlesDialog by remember { mutableStateOf(false) }
    var showClearEmbeddingsDialog by remember { mutableStateOf(false) }
    var showClearScheduledSummariesDialog by remember { mutableStateOf(false) }
    var showResetSettingsDialog by remember { mutableStateOf(false) }
    var showEmailAuthDialog by remember { mutableStateOf(false) }
    var selectedGroup by rememberSaveable { mutableStateOf<SettingsGroup?>(null) }
    var isHelpMode by rememberSaveable { mutableStateOf(false) }
    var helpDescription by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = selectedGroup != null) {
        selectedGroup = null
    }

    LaunchedEffect(selectedGroup) {
        if (selectedGroup != null && isHelpMode) {
            isHelpMode = false
        }
    }

    val backupSelection = BackupSelection(
        includeSources = backupSelectionState.includeSources,
        includeSubscriptions = backupSelectionState.includeSubscriptions,
        includeSettingsNoApi = backupSelectionState.includeSettingsNoApi,
        includeApiKeys = backupSelectionState.includeApiKeys
    )

    val webClientId = remember(context) {
        // Use direct resource reference so resource shrinker keeps this value in release builds.
        runCatching { context.getString(R.string.default_web_client_id) }.getOrDefault("")
    }
    val googleSignInClient = remember(context, webClientId) {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
        if (webClientId.isNotBlank()) builder.requestIdToken(webClientId)
        GoogleSignIn.getClient(context, builder.build())
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importSettingsAndSources(
                uri = uri,
                merge = isMergeImport,
                selection = backupSelection
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportSettingsAndSources(
                uri = uri,
                selection = backupSelection
            )
        }
    }

    val googleAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            Toast.makeText(context, "Google вхід скасовано або не завершено", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        var signInError: ApiException? = null
        val account = runCatching { task.getResult(ApiException::class.java) }
            .onFailure { throwable ->
                if (throwable is ApiException) {
                    signInError = throwable
                }
            }
            .getOrNull()
        val token = account?.idToken
        val statusCode = signInError?.statusCode
        if (!token.isNullOrBlank()) {
            viewModel.signInWithGoogleIdToken(token)
        } else {
            val reason = when {
                webClientId.isBlank() ->
                    "Не знайдено default_web_client_id. Перевірте google-services.json для цього build variant."
                statusCode != null ->
                    "Google вхід не повернув токен (statusCode=$statusCode). Перевірте SHA/package/OAuth Web client ID."
                else ->
                    "Google вхід не повернув idToken. Перевірте web client ID, SHA та package name."
            }
            Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.updateScheduledSummaryPushEnabled(true)
        } else {
            Toast.makeText(context, context.getString(R.string.settings_notification_permission_denied), Toast.LENGTH_SHORT).show()
            viewModel.updateScheduledSummaryPushEnabled(false)
        }
    }

    LaunchedEffect(transferState) {
        when (val state = transferState) {
            is TransferState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetTransferState()
            }
            is TransferState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetTransferState()
            }
            else -> Unit
        }
    }

    LaunchedEffect(userPreferences.isScheduledSummaryPushEnabled) {
        if (
            userPreferences.isScheduledSummaryPushEnabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Local state for sliders to avoid lag
    var aiMaxCharsPerArticle by rememberSaveable(userPreferences.aiMaxCharsPerArticle) { mutableStateOf(userPreferences.aiMaxCharsPerArticle.toFloat()) }
    var aiMaxCharsPerFeedArticle by rememberSaveable(userPreferences.aiMaxCharsPerFeedArticle) { mutableStateOf(userPreferences.aiMaxCharsPerFeedArticle.toFloat()) }
    var aiMaxCharsTotal by rememberSaveable(userPreferences.aiMaxCharsTotal) { mutableStateOf(userPreferences.aiMaxCharsTotal.toFloat()) }
    var summaryNewsInFeedExtractive by rememberSaveable(userPreferences.summaryNewsInFeedExtractive) { mutableStateOf(userPreferences.summaryNewsInFeedExtractive.toFloat()) }
    var summaryNewsInScheduledExtractive by rememberSaveable(userPreferences.summaryNewsInScheduledExtractive) { mutableStateOf(userPreferences.summaryNewsInScheduledExtractive.toFloat()) }
    var showLastSummariesCount by rememberSaveable(userPreferences.showLastSummariesCount) { mutableStateOf(userPreferences.showLastSummariesCount.toFloat()) }
    var showInfographicNewsCount by rememberSaveable(userPreferences.showInfographicNewsCount) { mutableStateOf(userPreferences.showInfographicNewsCount.toFloat()) }
    var localDeduplicationThreshold by rememberSaveable(userPreferences.localDeduplicationThreshold) { mutableStateOf(userPreferences.localDeduplicationThreshold) }
    var cloudDeduplicationThreshold by rememberSaveable(userPreferences.cloudDeduplicationThreshold) { mutableStateOf(userPreferences.cloudDeduplicationThreshold) }
    var minMentions by rememberSaveable(userPreferences.minMentions) { mutableStateOf(userPreferences.minMentions.toFloat()) }
    var adaptiveExtractiveOnlyBelowChars by rememberSaveable(userPreferences.adaptiveExtractiveOnlyBelowChars) { mutableStateOf(userPreferences.adaptiveExtractiveOnlyBelowChars.toFloat()) }
    var adaptiveExtractiveCompressAboveChars by rememberSaveable(userPreferences.adaptiveExtractiveCompressAboveChars) { mutableStateOf(userPreferences.adaptiveExtractiveCompressAboveChars.toFloat()) }
    var adaptiveExtractiveCompressionPercent by rememberSaveable(userPreferences.adaptiveExtractiveCompressionPercent) { mutableStateOf(userPreferences.adaptiveExtractiveCompressionPercent.toFloat()) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(stringResource(selectedGroup?.titleRes ?: R.string.nav_settings)) },
                navigationIcon = {
                    if (selectedGroup != null) {
                        IconButton(onClick = { selectedGroup = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                        }
                    }
                },
                actions = {
                    if (selectedGroup == null) {
                        AppHelpToggleAction(
                            isHelpMode = isHelpMode,
                            onToggle = { isHelpMode = !isHelpMode }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Crossfade(targetState = selectedGroup, label = "settingsGroupTransition") { activeGroup ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .animateContentSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (activeGroup == null) 20.dp else 16.dp)
            ) {
                if (activeGroup == null) {
                    item {
                        SettingsHomeGroupsContent(
                            isHelpMode = isHelpMode,
                            onGroupClick = { selectedGroup = it },
                            onHelpRequest = { group ->
                                helpDescription = settingsGroupHelpDescription(context, group)
                            }
                        )
                    }
                    return@LazyColumn
                }
            if (activeGroup == SettingsGroup.ACCOUNT) item {
                SettingsAccountGroup(
                    authUiState = authUiState,
                    isCloudSyncEnabled = isCloudSyncEnabled,
                    syncIntervalHours = syncIntervalHours,
                    backupSelection = backupSelection,
                    transferState = transferState,
                    onSyncIntervalSelect = { viewModel.updateSyncIntervalHours(it) },
                    onSyncEnabledChange = { enabled ->
                        viewModel.setCloudSyncEnabled(enabled, backupSelection)
                    },
                    onBackupSelectionChange = viewModel::updateBackupSelection,
                    onSignInOutClick = {
                        if (authUiState.isSignedIn) {
                            viewModel.signOut()
                        } else {
                            showEmailAuthDialog = true
                        }
                    },
                    onSyncNowClick = { viewModel.syncNow(backupSelection) },
                    onImportClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onExportClick = { exportLauncher.launch(it) }
                )
            }

            if (activeGroup == SettingsGroup.GENERAL) item {
                SettingsGeneralGroupContent(
                    userPreferences = userPreferences,
                    onAppLanguageChange = viewModel::updateAppLanguage,
                    onSummaryLanguageChange = viewModel::updateSummaryLanguage,
                    onThemeModeChange = viewModel::updateAppThemeMode
                )
            }
            if (activeGroup == SettingsGroup.API_KEYS) item {
                SettingsApiKeysGroupContent(
                    summaryConfigs = summaryConfigs,
                    embeddingConfigs = embeddingConfigs,
                    onAddSummaryConfig = { showConfigDialog = null to AiModelType.SUMMARY },
                    onEditSummaryConfig = { showConfigDialog = it to AiModelType.SUMMARY },
                    onDeleteSummaryConfig = viewModel::deleteAiConfig,
                    onToggleSummaryConfig = viewModel::toggleAiConfig,
                    onAddEmbeddingConfig = { showConfigDialog = null to AiModelType.EMBEDDING },
                    onEditEmbeddingConfig = { showConfigDialog = it to AiModelType.EMBEDDING },
                    onDeleteEmbeddingConfig = viewModel::deleteAiConfig,
                    onToggleEmbeddingConfig = viewModel::toggleAiConfig
                )
            }
            if (activeGroup == SettingsGroup.AI_PROCESSING) item {
                SettingsAiProcessingGroupContent(
                    userPreferences = userPreferences,
                    summaryPrompt = summaryPrompt,
                    aiMaxCharsPerArticle = aiMaxCharsPerArticle,
                    aiMaxCharsPerFeedArticle = aiMaxCharsPerFeedArticle,
                    aiMaxCharsTotal = aiMaxCharsTotal,
                    summaryNewsInFeedExtractive = summaryNewsInFeedExtractive,
                    summaryNewsInScheduledExtractive = summaryNewsInScheduledExtractive,
                    adaptiveExtractiveOnlyBelowChars = adaptiveExtractiveOnlyBelowChars,
                    adaptiveExtractiveCompressAboveChars = adaptiveExtractiveCompressAboveChars,
                    adaptiveExtractiveCompressionPercent = adaptiveExtractiveCompressionPercent,
                    onAiStrategyChange = viewModel::updateAiStrategy,
                    onAiMaxCharsPerArticleChange = { aiMaxCharsPerArticle = it },
                    onAiMaxCharsPerArticleCommitted = {
                        viewModel.updateAiMaxCharsPerArticle(aiMaxCharsPerArticle.toInt())
                    },
                    onAiMaxCharsPerFeedArticleChange = { aiMaxCharsPerFeedArticle = it },
                    onAiMaxCharsPerFeedArticleCommitted = {
                        viewModel.updateAiMaxCharsPerFeedArticle(aiMaxCharsPerFeedArticle.toInt())
                    },
                    onAiMaxCharsTotalChange = { aiMaxCharsTotal = it },
                    onAiMaxCharsTotalCommitted = {
                        viewModel.updateAiMaxCharsTotal(aiMaxCharsTotal.toInt())
                    },
                    onSummaryNewsInFeedExtractiveChange = { summaryNewsInFeedExtractive = it },
                    onSummaryNewsInFeedExtractiveCommitted = {
                        viewModel.updateSummaryNewsInFeedExtractive(summaryNewsInFeedExtractive.toInt())
                    },
                    onSummaryNewsInScheduledExtractiveChange = { summaryNewsInScheduledExtractive = it },
                    onSummaryNewsInScheduledExtractiveCommitted = {
                        viewModel.updateSummaryNewsInScheduledExtractive(summaryNewsInScheduledExtractive.toInt())
                    },
                    onDeduplicationStrategyChange = viewModel::updateDeduplicationStrategy,
                    onCustomSummaryPromptEnabledChange = viewModel::updateCustomSummaryPromptEnabled,
                    onSummaryPromptChange = {
                        summaryPrompt = it
                        viewModel.updateSummaryPrompt(it)
                    },
                    onAdaptiveExtractiveOnlyBelowCharsChange = { adaptiveExtractiveOnlyBelowChars = it },
                    onAdaptiveExtractiveOnlyBelowCharsCommitted = {
                        viewModel.updateAdaptiveExtractiveOnlyBelowChars(adaptiveExtractiveOnlyBelowChars.toInt())
                    },
                    onAdaptiveExtractiveCompressAboveCharsChange = { adaptiveExtractiveCompressAboveChars = it },
                    onAdaptiveExtractiveCompressAboveCharsCommitted = {
                        viewModel.updateAdaptiveExtractiveCompressAboveChars(adaptiveExtractiveCompressAboveChars.toInt())
                    },
                    onAdaptiveExtractiveCompressionPercentChange = { adaptiveExtractiveCompressionPercent = it },
                    onAdaptiveExtractiveCompressionPercentCommitted = {
                        viewModel.updateAdaptiveExtractiveCompressionPercent(adaptiveExtractiveCompressionPercent.toInt())
                    }
                )
            }
            if (activeGroup == SettingsGroup.FEED) item {
                SettingsFeedGroupContent(
                    userPreferences = userPreferences,
                    localDeduplicationThreshold = localDeduplicationThreshold,
                    cloudDeduplicationThreshold = cloudDeduplicationThreshold,
                    minMentions = minMentions,
                    downloadState = downloadState,
                    onFeedMediaEnabledChange = viewModel::updateFeedMediaEnabled,
                    onFeedDescriptionEnabledChange = viewModel::updateFeedDescriptionEnabled,
                    onFeedSummaryUseFullTextEnabledChange = viewModel::updateFeedSummaryUseFullTextEnabled,
                    onImportanceFilterEnabledChange = viewModel::updateImportanceFilterEnabled,
                    onDeduplicationEnabledChange = viewModel::updateDeduplicationEnabled,
                    onHideSingleNewsEnabledChange = viewModel::updateHideSingleNewsEnabled,
                    onLocalDeduplicationThresholdChange = { localDeduplicationThreshold = it },
                    onLocalDeduplicationThresholdCommitted = {
                        viewModel.updateLocalDeduplicationThreshold(localDeduplicationThreshold)
                    },
                    onCloudDeduplicationThresholdChange = { cloudDeduplicationThreshold = it },
                    onCloudDeduplicationThresholdCommitted = {
                        viewModel.updateCloudDeduplicationThreshold(cloudDeduplicationThreshold)
                    },
                    onMinMentionsChange = { minMentions = it },
                    onMinMentionsCommitted = {
                        viewModel.updateMinMentions(minMentions.toInt())
                    },
                    onModelActionClick = {
                        if (downloadState is ModelDownloadState.Ready) viewModel.deleteModel()
                        else viewModel.downloadModel()
                    }
                )
            }
            if (activeGroup == SettingsGroup.SCHEDULED_SUMMARY) item {
                ScheduledSummarySettingsSection(
                    showTitle = false,
                    userPreferences = userPreferences,
                    showLastSummariesCount = showLastSummariesCount,
                    onShowLastSummariesCountChange = { showLastSummariesCount = it },
                    onShowLastSummariesCountCommitted = {
                        viewModel.updateShowLastSummariesCount(showLastSummariesCount.toInt())
                    },
                    showInfographicNewsCount = showInfographicNewsCount,
                    onShowInfographicNewsCountChange = { showInfographicNewsCount = it },
                    onShowInfographicNewsCountCommitted = {
                        viewModel.updateShowInfographicNewsCount(showInfographicNewsCount.toInt())
                    },
                    onScheduledSummaryToggle = {
                        viewModel.updateScheduledSummary(
                            it,
                            userPreferences.scheduledHour,
                            userPreferences.scheduledMinute
                        )
                    },
                    onScheduledPushToggle = { enabled ->
                        if (!enabled) {
                            viewModel.updateScheduledSummaryPushEnabled(false)
                        } else if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.updateScheduledSummaryPushEnabled(true)
                        }
                    },
                    onPickTime = { showTimePicker = true }
                )
            }
            if (activeGroup == SettingsGroup.RECOMMENDATIONS) item {
                SourcesSettingsSection(
                    showTitle = false,
                    isRecommendationsEnabled = userPreferences.isRecommendationsEnabled,
                    onRecommendationsToggle = { viewModel.updateRecommendationsEnabled(it) }
                )
            }

            if (activeGroup == SettingsGroup.MEMORY) item {
                MemorySettingsSection(
                    showTitle = false,
                    articleAutoCleanupDays = userPreferences.articleAutoCleanupDays,
                    onArticleAutoCleanupDaysChange = viewModel::updateArticleAutoCleanupDays,
                    onClearArticles = { showClearArticlesDialog = true },
                    onClearEmbeddings = { showClearEmbeddingsDialog = true },
                    onClearScheduledSummaries = { showClearScheduledSummariesDialog = true },
                    onResetSettings = { showResetSettingsDialog = true }
                )
            }
            }
        }

        if (helpDescription != null) {
            AppExplanationDialog(
                description = helpDescription.orEmpty(),
                onDismiss = { helpDescription = null },
                title = "Пояснення групи налаштувань"
            )
        }

        showConfigDialog?.let { (config, type) ->
            SettingsAiConfigDialog(
                viewModel = viewModel,
                config = config,
                type = type,
                existingConfigs = if (type == AiModelType.SUMMARY) summaryConfigs else embeddingConfigs,
                onDismiss = { showConfigDialog = null },
                onConfirm = { viewModel.addAiConfig(it); showConfigDialog = null }
            )
        }

        if (showTimePicker) {
            SettingsScheduledTimePickerDialog(
                hour = userPreferences.scheduledHour,
                minute = userPreferences.scheduledMinute,
                onDismiss = { showTimePicker = false },
                onConfirm = { h, m -> viewModel.updateScheduledSummary(true, h, m); showTimePicker = false }
            )
        }

        if (showClearArticlesDialog) {
            SettingsConfirmDeleteDialog(
                title = stringResource(R.string.settings_clear_articles),
                text = stringResource(R.string.settings_clear_articles_confirm),
                onConfirm = { viewModel.clearAllArticles() },
                onDismiss = { showClearArticlesDialog = false }
            )
        }

        if (showClearEmbeddingsDialog) {
            SettingsConfirmDeleteDialog(
                title = stringResource(R.string.settings_clear_embeddings),
                text = stringResource(R.string.settings_clear_embeddings_confirm),
                onConfirm = { viewModel.clearEmbeddings() },
                onDismiss = { showClearEmbeddingsDialog = false }
            )
        }

        if (showClearScheduledSummariesDialog) {
            SettingsConfirmDeleteDialog(
                title = stringResource(R.string.settings_clear_scheduled_summaries),
                text = stringResource(R.string.settings_clear_scheduled_summaries_confirm),
                onConfirm = { viewModel.clearScheduledSummaries() },
                onDismiss = { showClearScheduledSummariesDialog = false }
            )
        }

        if (showResetSettingsDialog) {
            SettingsConfirmDeleteDialog(
                title = stringResource(R.string.settings_reset_settings),
                text = stringResource(R.string.settings_reset_settings_confirm),
                onConfirm = { viewModel.resetSettingsToDefaults() },
                onDismiss = { showResetSettingsDialog = false }
            )
        }

        if (showEmailAuthDialog) {
            SettingsEmailAuthDialog(
                onDismiss = { showEmailAuthDialog = false },
                onLogin = { email, pass -> viewModel.signInWithEmail(email, pass, register = false) },
                onRegister = { email, pass -> viewModel.signInWithEmail(email, pass, register = true) },
                onGoogleLogin = {
                    // Force account chooser instead of silent reuse of the previous account.
                    googleSignInClient.signOut().addOnCompleteListener {
                        googleAuthLauncher.launch(googleSignInClient.signInIntent)
                    }
                }
            )
        }
    }
}

private fun settingsGroupHelpDescription(context: android.content.Context, group: SettingsGroup): String {
    return when (group) {
        SettingsGroup.ACCOUNT -> context.getString(R.string.settings_help_account)
        SettingsGroup.AI_PROCESSING -> context.getString(R.string.settings_help_ai_processing)
        SettingsGroup.API_KEYS -> context.getString(R.string.settings_help_api_keys)
        SettingsGroup.RECOMMENDATIONS -> context.getString(R.string.settings_help_recommendations)
        SettingsGroup.FEED -> context.getString(R.string.settings_help_feed)
        SettingsGroup.SCHEDULED_SUMMARY -> context.getString(R.string.settings_help_scheduled)
        SettingsGroup.GENERAL -> context.getString(R.string.settings_help_general)
        SettingsGroup.MEMORY -> context.getString(R.string.settings_help_memory)
    }
}


