package com.andrewwin.sumup.ui.screen.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import com.andrewwin.sumup.data.local.entities.AiProvider
import com.andrewwin.sumup.ui.components.AppExplanationDialog
import com.andrewwin.sumup.ui.components.AppHelpToggleAction
import com.andrewwin.sumup.ui.components.AppMotion
import com.andrewwin.sumup.ui.components.AppSearchField
import com.andrewwin.sumup.ui.components.AppTopBar
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import java.util.Locale

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    val summaryConfigs by viewModel.summaryConfigs.collectAsState()
    val embeddingConfigs by viewModel.embeddingConfigs.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val transferState by viewModel.transferState.collectAsState()
    val authUiState by viewModel.authUiState.collectAsState()
    val isCloudSyncEnabled by viewModel.isCloudSyncEnabled.collectAsState()
    val syncIntervalHours by viewModel.syncIntervalHours.collectAsState()
    val syncStrategy by viewModel.syncStrategy.collectAsState()
    val syncOverwritePriority by viewModel.syncOverwritePriority.collectAsState()
    val importStrategy by viewModel.importStrategy.collectAsState()
    val lastSyncAt by viewModel.lastSyncAt.collectAsState()
    val syncSelectionState by viewModel.syncSelection.collectAsState()
    val exportSelectionState by viewModel.exportSelection.collectAsState()
    val importSelectionState by viewModel.importSelection.collectAsState()
    val hasSyncPassphrase by viewModel.hasSyncPassphrase.collectAsState()
    val currentSummaryConfig = remember(summaryConfigs) { summaryConfigs.firstOrNull { it.isEnabled } }
    val currentEmbeddingConfig = remember(embeddingConfigs) { embeddingConfigs.firstOrNull { it.isEnabled } }

    var showConfigDialog by remember { mutableStateOf<Pair<AiModelConfig?, AiModelType>?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var summaryPrompt by remember(userPreferences.summaryPrompt) { mutableStateOf(userPreferences.summaryPrompt) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var selectedGroup by rememberSaveable { mutableStateOf<SettingsGroup?>(null) }

    var showClearArticlesDialog by remember { mutableStateOf(false) }
    var showClearEmbeddingsDialog by remember { mutableStateOf(false) }
    var showClearScheduledSummariesDialog by remember { mutableStateOf(false) }
    var showResetSettingsDialog by remember { mutableStateOf(false) }
    var showEmailAuthDialog by remember { mutableStateOf(false) }
    var showSyncPassphraseDialog by remember { mutableStateOf(false) }
    var deleteAiConfigConfirm by remember { mutableStateOf<AiModelConfig?>(null) }
    var isHelpMode by rememberSaveable { mutableStateOf(false) }
    var helpDescription by remember { mutableStateOf<String?>(null) }
    var wasImeVisible by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedGroup != null) {
        selectedGroup = null
    }
    BackHandler(enabled = isSearchFocused && selectedGroup == null) {
        focusManager.clearFocus(force = true)
        isSearchFocused = false
    }

    LaunchedEffect(selectedGroup) {
        if (selectedGroup != null && isHelpMode) {
            isHelpMode = false
        }
        if (selectedGroup != null) {
            focusManager.clearFocus(force = true)
            isSearchFocused = false
        }
    }

    LaunchedEffect(imeVisible, isSearchFocused, selectedGroup) {
        if (selectedGroup != null) {
            wasImeVisible = false
            return@LaunchedEffect
        }
        if (imeVisible) {
            wasImeVisible = true
        } else if (wasImeVisible && isSearchFocused) {
            focusManager.clearFocus(force = true)
            isSearchFocused = false
            wasImeVisible = false
        } else if (!isSearchFocused) {
            wasImeVisible = false
        }
    }

    val syncSelection = BackupSelection(
        includeSources = syncSelectionState.includeSources,
        includeSubscriptions = syncSelectionState.includeSubscriptions,
        includeSavedArticles = syncSelectionState.includeSavedArticles,
        includeSettingsNoApi = syncSelectionState.includeSettingsNoApi,
        includeApiKeys = syncSelectionState.includeApiKeys
    )
    val exportSelection = BackupSelection(
        includeSources = exportSelectionState.includeSources,
        includeSubscriptions = exportSelectionState.includeSubscriptions,
        includeSavedArticles = exportSelectionState.includeSavedArticles,
        includeSettingsNoApi = exportSelectionState.includeSettingsNoApi,
        includeApiKeys = exportSelectionState.includeApiKeys
    )
    val importSelection = BackupSelection(
        includeSources = importSelectionState.includeSources,
        includeSubscriptions = importSelectionState.includeSubscriptions,
        includeSavedArticles = importSelectionState.includeSavedArticles,
        includeSettingsNoApi = importSelectionState.includeSettingsNoApi,
        includeApiKeys = importSelectionState.includeApiKeys
    )

    val webClientId = remember(context) {
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
                    merge = importStrategy == SyncConflictStrategy.MERGE,
                    selection = importSelection
                )
            }
        }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportSettingsAndSources(
                uri = uri,
                selection = exportSelection
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
            Toast.makeText(
                context,
                context.getString(R.string.settings_notification_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
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

    var aiMaxCharsPerArticle by rememberSaveable(userPreferences.aiMaxCharsPerArticle) {
        mutableStateOf(userPreferences.aiMaxCharsPerArticle.toFloat())
    }
    var aiMaxCharsPerFeedArticle by rememberSaveable(userPreferences.aiMaxCharsPerFeedArticle) {
        mutableStateOf(userPreferences.aiMaxCharsPerFeedArticle.toFloat())
    }
    var aiMaxCharsTotal by rememberSaveable(userPreferences.aiMaxCharsTotal) {
        mutableStateOf(userPreferences.aiMaxCharsTotal.toFloat())
    }
    var summaryNewsInFeedExtractive by rememberSaveable(userPreferences.summaryNewsInFeedExtractive) {
        mutableStateOf(userPreferences.summaryNewsInFeedExtractive.toFloat())
    }
    var summaryNewsInScheduledExtractive by rememberSaveable(userPreferences.summaryNewsInScheduledExtractive) {
        mutableStateOf(userPreferences.summaryNewsInScheduledExtractive.toFloat())
    }
    var showLastSummariesCount by rememberSaveable(userPreferences.showLastSummariesCount) {
        mutableStateOf(userPreferences.showLastSummariesCount.toFloat())
    }
    var showInfographicNewsCount by rememberSaveable(userPreferences.showInfographicNewsCount) {
        mutableStateOf(userPreferences.showInfographicNewsCount.toFloat())
    }
    var localDeduplicationThreshold by rememberSaveable(userPreferences.localDeduplicationThreshold) {
        mutableStateOf(userPreferences.localDeduplicationThreshold)
    }
    var cloudDeduplicationThreshold by rememberSaveable(userPreferences.cloudDeduplicationThreshold) {
        mutableStateOf(userPreferences.cloudDeduplicationThreshold)
    }
    var minMentions by rememberSaveable(userPreferences.minMentions) {
        mutableStateOf((userPreferences.minMentions - 1).coerceAtLeast(1).toFloat())
    }
    var adaptiveExtractiveOnlyBelowChars by rememberSaveable(userPreferences.adaptiveExtractiveOnlyBelowChars) {
        mutableStateOf(userPreferences.adaptiveExtractiveOnlyBelowChars.toFloat())
    }
    var adaptiveExtractiveHighCompressionAboveChars by rememberSaveable(userPreferences.adaptiveExtractiveHighCompressionAboveChars) {
        mutableStateOf(userPreferences.adaptiveExtractiveHighCompressionAboveChars.toFloat())
    }
    var adaptiveExtractiveCompressionPercentMedium by rememberSaveable(userPreferences.adaptiveExtractiveCompressionPercentMedium) {
        mutableStateOf(userPreferences.adaptiveExtractiveCompressionPercentMedium.toFloat())
    }
    var adaptiveExtractiveCompressionPercentHigh by rememberSaveable(userPreferences.adaptiveExtractiveCompressionPercentHigh) {
        mutableStateOf(userPreferences.adaptiveExtractiveCompressionPercentHigh.toFloat())
    }

    val settingsGroups = remember {
        listOf(
            SettingsGroup.ACCOUNT,
            SettingsGroup.TRANSFER,
            SettingsGroup.AI_PROCESSING,
            SettingsGroup.API_KEYS,
            SettingsGroup.RECOMMENDATIONS,
            SettingsGroup.FEED,
            SettingsGroup.SCHEDULED_SUMMARY,
            SettingsGroup.GENERAL,
            SettingsGroup.MEMORY
        )
    }
    val filteredGroups = remember(searchQuery, context) {
        settingsGroups.filter { it.matchesSearch(searchQuery, context) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AnimatedVisibility(
                visible = !(selectedGroup == null && isSearchFocused),
                enter = fadeIn(animationSpec = tween(320, easing = FastOutSlowInEasing)) +
                    expandVertically(animationSpec = tween(380, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                    shrinkVertically(animationSpec = tween(360, easing = FastOutSlowInEasing))
            ) {
                AppTopBar(
                    title = { Text(stringResource(selectedGroup?.titleRes ?: R.string.nav_settings)) },
                    navigationIcon = {
                        if (selectedGroup != null) {
                            androidx.compose.material3.IconButton(onClick = { selectedGroup = null }) {
                                androidx.compose.material3.Icon(Icons.Default.ArrowBack, contentDescription = null)
                            }
                        }
                    },
                    actions = {
                        AppHelpToggleAction(
                            isHelpMode = isHelpMode,
                            onToggle = { isHelpMode = !isHelpMode }
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedGroup,
            label = "settingsGroupTransition",
            transitionSpec = {
                AppMotion.contentEnter() togetherWith AppMotion.contentExit()
            }
        ) { activeGroup ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (activeGroup == null) {
                    item {
                        AppSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = stringResource(R.string.settings_search_placeholder),
                            leadingIcon = Icons.Default.Search,
                            modifier = Modifier.fillMaxWidth(),
                            onFocusChanged = { focused ->
                                if (focused) isSearchFocused = true
                            }
                        )
                    }
                    if (filteredGroups.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.settings_search_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    } else {
                        item {
                            SettingsGroupsPanel(
                                groups = filteredGroups,
                                isHelpMode = isHelpMode,
                                onGroupClick = { selectedGroup = it },
                                onHelpRequest = { group ->
                                    helpDescription = settingsGroupHelpDescription(context, group)
                                },
                                helpDescriptionForGroup = { group ->
                                    settingsGroupHelpDescription(context, group)
                                }
                            )
                        }
                    }
                    return@LazyColumn
                }

                when (activeGroup) {
                    SettingsGroup.ACCOUNT -> item {
                        SettingsAccountGroup(
                            isHelpMode = isHelpMode,
                            authUiState = authUiState,
                            isCloudSyncEnabled = isCloudSyncEnabled,
                            syncIntervalHours = syncIntervalHours,
                            syncStrategy = syncStrategy,
                            syncOverwritePriority = syncOverwritePriority,
                            lastSyncAt = lastSyncAt,
                            syncSelection = syncSelection,
                            hasSyncPassphrase = hasSyncPassphrase,
                            transferState = transferState,
                            onHelpRequest = { helpDescription = it },
                            onSyncIntervalSelect = viewModel::updateSyncIntervalHours,
                            onSyncStrategySelect = viewModel::updateSyncStrategy,
                            onSyncOverwritePrioritySelect = viewModel::updateSyncOverwritePriority,
                            onSyncEnabledChange = { enabled ->
                                viewModel.setCloudSyncEnabled(enabled, syncSelection)
                            },
                            onSyncSelectionChange = viewModel::updateSyncSelection,
                            onManageSyncPassphrase = { showSyncPassphraseDialog = true },
                            onSignInOutClick = {
                                if (authUiState.isSignedIn) viewModel.signOut() else showEmailAuthDialog = true
                            },
                            onSyncNowClick = { viewModel.syncNow(syncSelection) }
                        )
                    }

                    SettingsGroup.TRANSFER -> item {
                        SettingsTransferGroupContent(
                            isHelpMode = isHelpMode,
                            exportSelection = exportSelection,
                            importSelection = importSelection,
                            importStrategy = importStrategy,
                            hasSyncPassphrase = hasSyncPassphrase,
                            transferState = transferState,
                            onHelpRequest = { helpDescription = it },
                            onExportSelectionChange = viewModel::updateExportSelection,
                            onImportSelectionChange = viewModel::updateImportSelection,
                            onImportStrategyChange = viewModel::updateImportStrategy,
                            onManageSyncPassphrase = { showSyncPassphraseDialog = true },
                            onImportClick = {
                                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            onExportClick = { exportLauncher.launch(it) }
                        )
                    }

                    SettingsGroup.GENERAL -> item {
                        SettingsGeneralGroupContent(
                            isHelpMode = isHelpMode,
                            userPreferences = userPreferences,
                            onHelpRequest = { helpDescription = it },
                            onAppLanguageChange = viewModel::updateAppLanguage,
                            onSummaryLanguageChange = viewModel::updateSummaryLanguage,
                            onThemeModeChange = viewModel::updateAppThemeMode
                        )
                    }

                    SettingsGroup.AI_PROCESSING -> item {
                        SettingsAiProcessingGroupContent(
                            isHelpMode = isHelpMode,
                            userPreferences = userPreferences,
                            summaryPrompt = summaryPrompt,
                            aiMaxCharsPerArticle = aiMaxCharsPerArticle,
                            aiMaxCharsPerFeedArticle = aiMaxCharsPerFeedArticle,
                            aiMaxCharsTotal = aiMaxCharsTotal,
                            summaryNewsInFeedExtractive = summaryNewsInFeedExtractive,
                            summaryNewsInScheduledExtractive = summaryNewsInScheduledExtractive,
                            adaptiveExtractiveOnlyBelowChars = adaptiveExtractiveOnlyBelowChars,
                            adaptiveExtractiveHighCompressionAboveChars = adaptiveExtractiveHighCompressionAboveChars,
                            adaptiveExtractiveCompressionPercentMedium = adaptiveExtractiveCompressionPercentMedium,
                            adaptiveExtractiveCompressionPercentHigh = adaptiveExtractiveCompressionPercentHigh,
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
                            onCustomSummaryPromptEnabledChange = viewModel::updateCustomSummaryPromptEnabled,
                            onSummaryPromptChange = {
                                summaryPrompt = it
                                viewModel.updateSummaryPrompt(it)
                            },
                            onAdaptiveExtractiveOnlyBelowCharsChange = { adaptiveExtractiveOnlyBelowChars = it },
                            onAdaptiveExtractiveOnlyBelowCharsCommitted = {
                                viewModel.updateAdaptiveExtractiveOnlyBelowChars(adaptiveExtractiveOnlyBelowChars.toInt())
                            },
                            onAdaptiveExtractiveHighCompressionAboveCharsChange = { adaptiveExtractiveHighCompressionAboveChars = it },
                            onAdaptiveExtractiveHighCompressionAboveCharsCommitted = {
                                viewModel.updateAdaptiveExtractiveHighCompressionAboveChars(adaptiveExtractiveHighCompressionAboveChars.toInt())
                            },
                            onAdaptiveExtractiveCompressionPercentMediumChange = { adaptiveExtractiveCompressionPercentMedium = it },
                            onAdaptiveExtractiveCompressionPercentMediumCommitted = {
                                viewModel.updateAdaptiveExtractiveCompressionPercentMedium(
                                    adaptiveExtractiveCompressionPercentMedium.toInt()
                                )
                            },
                            onAdaptiveExtractiveCompressionPercentHighChange = { adaptiveExtractiveCompressionPercentHigh = it },
                            onAdaptiveExtractiveCompressionPercentHighCommitted = {
                                viewModel.updateAdaptiveExtractiveCompressionPercentHigh(
                                    adaptiveExtractiveCompressionPercentHigh.toInt()
                                )
                            },
                            onHelpRequest = { helpDescription = it }
                        )
                    }

                    SettingsGroup.API_KEYS -> item {
                        SettingsApiKeysGroupContent(
                            isHelpMode = isHelpMode,
                            summaryConfigs = summaryConfigs,
                            embeddingConfigs = embeddingConfigs,
                            currentSummaryConfig = currentSummaryConfig,
                            currentEmbeddingConfig = currentEmbeddingConfig,
                            onHelpRequest = { helpDescription = it },
                            onAddSummaryConfig = { showConfigDialog = null to AiModelType.SUMMARY },
                            onEditSummaryConfig = { showConfigDialog = it to AiModelType.SUMMARY },
                            onDeleteSummaryConfig = { deleteAiConfigConfirm = it },
                            onToggleSummaryConfig = viewModel::toggleAiConfig,
                            onAddEmbeddingConfig = { showConfigDialog = null to AiModelType.EMBEDDING },
                            onEditEmbeddingConfig = { showConfigDialog = it to AiModelType.EMBEDDING },
                            onDeleteEmbeddingConfig = { deleteAiConfigConfirm = it },
                            onToggleEmbeddingConfig = viewModel::toggleAiConfig
                        )
                    }

                    SettingsGroup.FEED -> item {
                        SettingsFeedGroupContent(
                            isHelpMode = isHelpMode,
                            userPreferences = userPreferences,
                            localDeduplicationThreshold = localDeduplicationThreshold,
                            cloudDeduplicationThreshold = cloudDeduplicationThreshold,
                            minMentions = minMentions,
                            onFeedMediaEnabledChange = viewModel::updateFeedMediaEnabled,
                            onFeedDescriptionEnabledChange = viewModel::updateFeedDescriptionEnabled,
                            onFeedSummaryUseFullTextEnabledChange = viewModel::updateFeedSummaryUseFullTextEnabled,
                            onImportanceFilterEnabledChange = viewModel::updateImportanceFilterEnabled,
                            onDeduplicationEnabledChange = viewModel::updateDeduplicationEnabled,
                            onHideSingleNewsEnabledChange = viewModel::updateHideSingleNewsEnabled,
                            onDeduplicationStrategyChange = viewModel::updateDeduplicationStrategy,
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
                                viewModel.updateMinMentions(minMentions.toInt() + 1)
                            },
                            onHelpRequest = { helpDescription = it }
                        )
                    }

                    SettingsGroup.SCHEDULED_SUMMARY -> item {
                        ScheduledSummarySettingsSection(
                            showTitle = false,
                            isHelpMode = isHelpMode,
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
                            onPickTime = { showTimePicker = true },
                            onHelpRequest = { helpDescription = it }
                        )
                    }

                    SettingsGroup.RECOMMENDATIONS -> item {
                        SourcesSettingsSection(
                            showTitle = false,
                            isRecommendationsEnabled = userPreferences.isRecommendationsEnabled,
                            onRecommendationsToggle = viewModel::updateRecommendationsEnabled,
                            isHelpMode = isHelpMode,
                            onHelpRequest = { helpDescription = it }
                        )
                    }

                    SettingsGroup.MEMORY -> item {
                        MemorySettingsSection(
                            showTitle = false,
                            isHelpMode = isHelpMode,
                            articleAutoCleanupDays = userPreferences.articleAutoCleanupDays,
                            onArticleAutoCleanupDaysChange = viewModel::updateArticleAutoCleanupDays,
                            onClearArticles = { showClearArticlesDialog = true },
                            onClearEmbeddings = { showClearEmbeddingsDialog = true },
                            onClearScheduledSummaries = { showClearScheduledSummariesDialog = true },
                            onResetSettings = { showResetSettingsDialog = true },
                            onHelpRequest = { helpDescription = it }
                        )
                    }
                }
            }
        }

        AppExplanationDialog(
            visible = helpDescription != null,
            description = helpDescription.orEmpty(),
            onDismiss = { helpDescription = null },
            title = stringResource(R.string.settings_help_group_dialog_title)
        )

        showConfigDialog?.let { (config, type) ->
            SettingsAiConfigDialog(
                viewModel = viewModel,
                config = config,
                type = type,
                existingConfigs = if (type == AiModelType.SUMMARY) summaryConfigs else embeddingConfigs,
                onDismiss = { showConfigDialog = null },
                onConfirm = {
                    if (it.id == 0L) viewModel.addAiConfig(it) else viewModel.updateAiConfig(it)
                    showConfigDialog = null
                }
            )
        }

        if (showTimePicker) {
            SettingsScheduledTimePickerDialog(
                hour = userPreferences.scheduledHour,
                minute = userPreferences.scheduledMinute,
                onDismiss = { showTimePicker = false },
                onConfirm = { h, m ->
                    viewModel.updateScheduledSummary(true, h, m)
                    showTimePicker = false
                }
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
                    googleSignInClient.signOut().addOnCompleteListener {
                        googleAuthLauncher.launch(googleSignInClient.signInIntent)
                    }
                }
            )
        }

        if (showSyncPassphraseDialog) {
            SettingsSyncPassphraseDialog(
                hasExistingPassphrase = hasSyncPassphrase,
                isSignedIn = authUiState.isSignedIn,
                isMatchingExistingPassphrase = viewModel::isSyncPassphraseMatchingCurrent,
                onDismiss = { showSyncPassphraseDialog = false },
                onSave = viewModel::saveSyncPassphrase,
                onClear = viewModel::clearSyncPassphrase
            )
        }

        deleteAiConfigConfirm?.let { config ->
            SettingsConfirmDeleteDialog(
                title = stringResource(R.string.delete),
                text = stringResource(R.string.delete_api_key_confirm, config.name),
                onConfirm = {
                    viewModel.deleteAiConfig(config)
                    deleteAiConfigConfirm = null
                },
                onDismiss = { deleteAiConfigConfirm = null }
            )
        }
    }
}

private fun SettingsGroup.matchesSearch(
    query: String,
    context: android.content.Context
): Boolean {
    if (query.isBlank()) return true
    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
    val searchableText = buildString {
        append(context.getString(titleRes))
        append(' ')
        append(context.getString(descriptionRes))
        searchableTextResIds().forEach { resId ->
            append(' ')
            append(context.getString(resId))
        }
    }.lowercase(Locale.getDefault())
    return searchableText.contains(normalizedQuery)
}

private fun SettingsGroup.searchableTextResIds(): List<Int> = when (this) {
    SettingsGroup.ACCOUNT -> listOf(
        R.string.settings_account_login_title,
        R.string.settings_account_login_subtitle,
        R.string.settings_account_login_btn,
        R.string.settings_sync,
        R.string.settings_sync_strategy_title,
        R.string.settings_sync_strategy_overwrite,
        R.string.settings_sync_strategy_merge,
        R.string.settings_sync_overwrite_priority_title,
        R.string.settings_sync_overwrite_priority_local,
        R.string.settings_sync_overwrite_priority_cloud,
        R.string.settings_sync_enabled,
        R.string.settings_sync_interval_label,
        R.string.settings_sync_now,
        R.string.settings_sync_passphrase_title,
        R.string.settings_backup_sources,
        R.string.settings_backup_subscriptions,
        R.string.settings_backup_settings_no_api,
        R.string.settings_backup_api_keys
    )

    SettingsGroup.TRANSFER -> listOf(
        R.string.settings_export_import,
        R.string.settings_export_block_title,
        R.string.settings_import_block_title,
        R.string.settings_export_button,
        R.string.settings_import_button,
        R.string.settings_import_strategy_title,
        R.string.settings_sync_strategy_overwrite,
        R.string.settings_sync_strategy_merge,
        R.string.settings_backup_sources,
        R.string.settings_backup_subscriptions,
        R.string.settings_backup_settings_no_api,
        R.string.settings_backup_api_keys
    )

    SettingsGroup.GENERAL -> listOf(
        R.string.settings_language,
        R.string.settings_summary_language,
        R.string.settings_theme,
        R.string.settings_language_uk,
        R.string.settings_language_en,
        R.string.settings_theme_system,
        R.string.settings_theme_light,
        R.string.settings_theme_dark
    )

    SettingsGroup.API_KEYS -> listOf(
        R.string.settings_cloud_summary_api_keys,
        R.string.settings_cloud_vectorization_api_keys,
        R.string.settings_api_keys_empty
    )

    SettingsGroup.AI_PROCESSING -> listOf(
        R.string.settings_ai_strategy,
        R.string.settings_custom_summary_prompt,
        R.string.settings_summary_prompt,
        R.string.settings_ai_limits,
        R.string.settings_local_summary,
        R.string.settings_adaptive_summary,
        R.string.settings_ai_chars_per_article_processing,
        R.string.settings_ai_chars_per_feed_article,
        R.string.settings_ai_chars_total,
        R.string.settings_summary_news_feed_extractive,
        R.string.settings_summary_news_scheduled_extractive,
        R.string.settings_adaptive_extractive_only_below_chars,
        R.string.settings_adaptive_extractive_high_compression_above_chars,
        R.string.settings_adaptive_extractive_compression_percent_medium,
        R.string.settings_adaptive_extractive_compression_percent_high
    )

    SettingsGroup.FEED -> listOf(
        R.string.settings_feed,
        R.string.settings_feed_media,
        R.string.settings_feed_description,
        R.string.settings_feed_summary_use_full_text,
        R.string.settings_importance_filter,
        R.string.settings_enable_importance_filter,
        R.string.settings_deduplication_strategy,
        R.string.settings_enable_deduplication,
        R.string.settings_hide_single_news,
        R.string.settings_local_deduplication_threshold,
        R.string.settings_cloud_deduplication_threshold,
        R.string.settings_min_mentions
    )

    SettingsGroup.SCHEDULED_SUMMARY -> listOf(
        R.string.settings_scheduled_summary,
        R.string.settings_show_last_summaries_count,
        R.string.settings_show_infographic_news_count,
        R.string.settings_time_label,
        R.string.settings_scheduled_push_notifications
    )

    SettingsGroup.RECOMMENDATIONS -> listOf(
        R.string.settings_show_recommendations
    )

    SettingsGroup.MEMORY -> listOf(
        R.string.settings_article_auto_cleanup_interval,
        R.string.settings_clear_articles,
        R.string.settings_clear_embeddings,
        R.string.settings_clear_scheduled_summaries,
        R.string.settings_reset_settings
    )
}

private fun settingsGroupHelpDescription(context: android.content.Context, group: SettingsGroup): String {
    return when (group) {
        SettingsGroup.ACCOUNT -> context.getString(R.string.settings_help_account)
        SettingsGroup.TRANSFER -> context.getString(R.string.settings_help_account)
        SettingsGroup.AI_PROCESSING -> context.getString(R.string.settings_help_ai_processing)
        SettingsGroup.API_KEYS -> context.getString(R.string.settings_help_api_keys)
        SettingsGroup.RECOMMENDATIONS -> context.getString(R.string.settings_help_recommendations)
        SettingsGroup.FEED -> context.getString(R.string.settings_help_feed)
        SettingsGroup.SCHEDULED_SUMMARY -> context.getString(R.string.settings_help_scheduled)
        SettingsGroup.GENERAL -> context.getString(R.string.settings_help_general)
        SettingsGroup.MEMORY -> context.getString(R.string.settings_help_memory)
    }
}
