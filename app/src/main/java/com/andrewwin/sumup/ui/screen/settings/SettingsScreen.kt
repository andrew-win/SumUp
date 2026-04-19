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
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
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
            TopAppBar(
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
                        FilledIconButton(
                            onClick = { isHelpMode = !isHelpMode },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            if (isHelpMode) {
                                Icon(Icons.Default.Close, contentDescription = "Вимкнути підказки")
                            } else {
                                Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "Увімкнути підказки")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
                        Text(
                            text = stringResource(R.string.settings_section_account),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                        )
                        SettingsGroupsPanel(
                            groups = listOf(SettingsGroup.ACCOUNT),
                            isHelpMode = isHelpMode,
                            onGroupClick = { selectedGroup = it },
                            onHelpRequest = { group ->
                                helpDescription = settingsGroupHelpDescription(group)
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.settings_section_content_ai),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                        )
                        SettingsGroupsPanel(
                            groups = listOf(SettingsGroup.AI_PROCESSING, SettingsGroup.API_KEYS, SettingsGroup.RECOMMENDATIONS),
                            isHelpMode = isHelpMode,
                            onGroupClick = { selectedGroup = it },
                            onHelpRequest = { group ->
                                helpDescription = settingsGroupHelpDescription(group)
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.settings_section_interface),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                        )
                        SettingsGroupsPanel(
                            groups = listOf(
                                SettingsGroup.FEED,
                                SettingsGroup.SCHEDULED_SUMMARY,
                                SettingsGroup.GENERAL,
                                SettingsGroup.MEMORY
                            ),
                            isHelpMode = isHelpMode,
                            onGroupClick = { selectedGroup = it },
                            onHelpRequest = { group ->
                                helpDescription = settingsGroupHelpDescription(group)
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
                SettingsSection(title = stringResource(R.string.settings_language), boxed = true) {
                    val languages = listOf(
                        AppLanguage.UK to R.string.settings_language_uk,
                        AppLanguage.EN to R.string.settings_language_en
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        languages.forEachIndexed { index, (lang, labelRes) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = languages.size),
                                onClick = { viewModel.updateAppLanguage(lang) },
                                selected = userPreferences.appLanguage == lang
                            ) {
                                Text(text = stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (activeGroup == SettingsGroup.GENERAL) item {
                SettingsSection(title = stringResource(R.string.settings_summary_language), boxed = true) {
                    val summaryLanguages = listOf(
                        SummaryLanguage.ORIGINAL to R.string.settings_summary_language_original,
                        SummaryLanguage.UK to R.string.settings_summary_language_uk,
                        SummaryLanguage.EN to R.string.settings_summary_language_en
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        summaryLanguages.forEachIndexed { index, (lang, labelRes) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = summaryLanguages.size),
                                onClick = { viewModel.updateSummaryLanguage(lang) },
                                selected = userPreferences.summaryLanguage == lang
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            if (activeGroup == SettingsGroup.GENERAL) item {
                SettingsSection(title = stringResource(R.string.settings_theme), boxed = true) {
                    val themeModes = listOf(
                        AppThemeMode.SYSTEM to R.string.settings_theme_system,
                        AppThemeMode.LIGHT to R.string.settings_theme_light,
                        AppThemeMode.DARK to R.string.settings_theme_dark
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        themeModes.forEachIndexed { index, (mode, labelRes) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = themeModes.size),
                                onClick = { viewModel.updateAppThemeMode(mode) },
                                selected = userPreferences.appThemeMode == mode
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            if (activeGroup == SettingsGroup.AI_PROCESSING) item {
                SettingsSection(title = stringResource(R.string.settings_ai_strategy), boxed = true) {
                    val strategies = listOf(
                        AiStrategy.LOCAL to R.string.ai_strategy_local,
                        AiStrategy.CLOUD to R.string.ai_strategy_cloud,
                        AiStrategy.ADAPTIVE to R.string.ai_strategy_adaptive
                    )
                    
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        strategies.forEachIndexed { index, (strategy, labelRes) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = strategies.size),
                                onClick = { viewModel.updateAiStrategy(strategy) },
                                selected = userPreferences.aiStrategy == strategy
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            if (activeGroup == SettingsGroup.API_KEYS) item {
                SettingsSection(
                    title = stringResource(R.string.settings_cloud_summary_api_keys),
                    boxed = true,
                    trailing = {
                        IconButton(
                            onClick = { showConfigDialog = null to AiModelType.SUMMARY },
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (summaryConfigs.isEmpty()) {
                            Text(
                                stringResource(R.string.settings_api_keys_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            summaryConfigs.forEach { config ->
                                AiKeyItem(
                                    config = config,
                                    onEdit = { showConfigDialog = config to AiModelType.SUMMARY },
                                    onDelete = { viewModel.deleteAiConfig(config) },
                                    onToggle = { viewModel.toggleAiConfig(config, it) }
                                )
                            }
                        }
                    }
                }
            }

            if (activeGroup == SettingsGroup.API_KEYS) item {
                SettingsSection(
                    title = stringResource(R.string.settings_cloud_vectorization_api_keys),
                    boxed = true,
                    trailing = {
                        IconButton(
                            onClick = { showConfigDialog = null to AiModelType.EMBEDDING },
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (embeddingConfigs.isEmpty()) {
                            Text(
                                stringResource(R.string.settings_api_keys_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            embeddingConfigs.forEach { config ->
                                AiKeyItem(
                                    config = config,
                                    onEdit = { showConfigDialog = config to AiModelType.EMBEDDING },
                                    onDelete = { viewModel.deleteAiConfig(config) },
                                    onToggle = { viewModel.toggleAiConfig(config, it) }
                                )
                            }
                        }
                    }
                }
            }
            if (activeGroup == SettingsGroup.AI_PROCESSING) item {
                SettingsSection(title = stringResource(R.string.settings_ai_limits), boxed = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text(
                                stringResource(R.string.settings_ai_chars_per_article_processing, aiMaxCharsPerArticle.toInt()),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = aiMaxCharsPerArticle,
                                onValueChange = { aiMaxCharsPerArticle = it },
                                onValueChangeFinished = {
                                    viewModel.updateAiMaxCharsPerArticle(aiMaxCharsPerArticle.toInt())
                                },
                                valueRange = 200f..3000f,
                                steps = 28
                            )
                        }
                        Column {
                            Text(
                                stringResource(R.string.settings_ai_chars_per_feed_article, aiMaxCharsPerFeedArticle.toInt()),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = aiMaxCharsPerFeedArticle,
                                onValueChange = { aiMaxCharsPerFeedArticle = it },
                                onValueChangeFinished = {
                                    viewModel.updateAiMaxCharsPerFeedArticle(aiMaxCharsPerFeedArticle.toInt())
                                },
                                valueRange = 200f..3000f,
                                steps = 28
                            )
                        }
                        Column {
                            Text(
                                stringResource(R.string.settings_ai_chars_total, aiMaxCharsTotal.toInt()),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = aiMaxCharsTotal,
                                onValueChange = { aiMaxCharsTotal = it },
                                onValueChangeFinished = {
                                    viewModel.updateAiMaxCharsTotal(aiMaxCharsTotal.toInt())
                                },
                                valueRange = 2000f..20000f,
                                steps = 35
                            )
                        }
                    }
                }
            }
            if (activeGroup == SettingsGroup.AI_PROCESSING) item {
                SettingsSection(title = stringResource(R.string.settings_local_summary), boxed = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text(
                                stringResource(R.string.settings_summary_news_feed_extractive, summaryNewsInFeedExtractive.toInt()),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = summaryNewsInFeedExtractive,
                                onValueChange = { summaryNewsInFeedExtractive = it },
                                onValueChangeFinished = {
                                    viewModel.updateSummaryNewsInFeedExtractive(summaryNewsInFeedExtractive.toInt())
                                },
                                valueRange = 1f..20f,
                                steps = 18
                            )
                        }
                        Column {
                            Text(
                                stringResource(R.string.settings_summary_news_scheduled_extractive, summaryNewsInScheduledExtractive.toInt()),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = summaryNewsInScheduledExtractive,
                                onValueChange = { summaryNewsInScheduledExtractive = it },
                                onValueChangeFinished = {
                                    viewModel.updateSummaryNewsInScheduledExtractive(summaryNewsInScheduledExtractive.toInt())
                                },
                                valueRange = 1f..20f,
                                steps = 18
                            )
                        }
                    }
                }
            }
            if (activeGroup == SettingsGroup.AI_PROCESSING) item {
                SettingsSection(title = stringResource(R.string.settings_deduplication_strategy), boxed = true) {
                    val strategies = listOf(
                        DeduplicationStrategy.LOCAL to R.string.ai_strategy_local,
                        DeduplicationStrategy.CLOUD to R.string.ai_strategy_cloud,
                        DeduplicationStrategy.ADAPTIVE to R.string.ai_strategy_adaptive
                    )

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        strategies.forEachIndexed { index, (strategy, labelRes) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = strategies.size),
                                onClick = { viewModel.updateDeduplicationStrategy(strategy) },
                                selected = userPreferences.deduplicationStrategy == strategy
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            if (activeGroup == SettingsGroup.AI_PROCESSING) item {
                SettingsSection(title = stringResource(R.string.settings_custom_summary_prompt), boxed = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_custom_summary_prompt),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = userPreferences.isCustomSummaryPromptEnabled,
                                onCheckedChange = { viewModel.updateCustomSummaryPromptEnabled(it) },
                                modifier = Modifier.scale(SETTINGS_SWITCH_SCALE)
                            )
                        }

                        if (userPreferences.isCustomSummaryPromptEnabled) {
                            OutlinedTextField(
                                value = summaryPrompt,
                                onValueChange = {
                                    summaryPrompt = it
                                    viewModel.updateSummaryPrompt(it)
                                },
                                label = { Text(stringResource(R.string.settings_summary_prompt)) },
                                placeholder = { Text(stringResource(R.string.settings_summary_prompt_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large
                            )
                        }
                    }
                }
            }
            if (selectedGroup == SettingsGroup.AI_PROCESSING) item {
                SettingsSection(title = stringResource(R.string.settings_adaptive_summary), boxed = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text(
                                stringResource(
                                    R.string.settings_adaptive_extractive_only_below_chars,
                                    adaptiveExtractiveOnlyBelowChars.toInt()
                                ),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = adaptiveExtractiveOnlyBelowChars,
                                onValueChange = { adaptiveExtractiveOnlyBelowChars = it },
                                onValueChangeFinished = {
                                    viewModel.updateAdaptiveExtractiveOnlyBelowChars(adaptiveExtractiveOnlyBelowChars.toInt())
                                },
                                valueRange = 500f..5000f,
                                steps = 45
                            )
                        }
                        Column {
                            Text(
                                stringResource(
                                    R.string.settings_adaptive_extractive_compress_above_chars,
                                    adaptiveExtractiveCompressAboveChars.toInt()
                                ),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = adaptiveExtractiveCompressAboveChars,
                                onValueChange = { adaptiveExtractiveCompressAboveChars = it },
                                onValueChangeFinished = {
                                    viewModel.updateAdaptiveExtractiveCompressAboveChars(adaptiveExtractiveCompressAboveChars.toInt())
                                },
                                valueRange = 1000f..10000f,
                                steps = 90
                            )
                        }
                        Column {
                            Text(
                                stringResource(
                                    R.string.settings_adaptive_extractive_compression_percent,
                                    adaptiveExtractiveCompressionPercent.toInt()
                                ),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = adaptiveExtractiveCompressionPercent,
                                onValueChange = { adaptiveExtractiveCompressionPercent = it },
                                onValueChangeFinished = {
                                    viewModel.updateAdaptiveExtractiveCompressionPercent(adaptiveExtractiveCompressionPercent.toInt())
                                },
                                valueRange = 10f..90f,
                                steps = 79
                            )
                        }
                    }
                }
            }
            if (activeGroup == SettingsGroup.FEED) item {
                SettingsSection(title = stringResource(R.string.settings_feed), boxed = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_feed_media),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = userPreferences.isFeedMediaEnabled,
                                onCheckedChange = { viewModel.updateFeedMediaEnabled(it) },
                                modifier = Modifier.scale(SETTINGS_SWITCH_SCALE)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_feed_description),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = userPreferences.isFeedDescriptionEnabled,
                                onCheckedChange = { viewModel.updateFeedDescriptionEnabled(it) },
                                modifier = Modifier.scale(SETTINGS_SWITCH_SCALE)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_feed_summary_use_full_text),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = userPreferences.isFeedSummaryUseFullTextEnabled,
                                onCheckedChange = { viewModel.updateFeedSummaryUseFullTextEnabled(it) },
                                modifier = Modifier.scale(SETTINGS_SWITCH_SCALE)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_enable_importance_filter),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = userPreferences.isImportanceFilterEnabled,
                                onCheckedChange = { viewModel.updateImportanceFilterEnabled(it) },
                                modifier = Modifier.scale(SETTINGS_SWITCH_SCALE)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_enable_deduplication),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = userPreferences.isDeduplicationEnabled,
                                onCheckedChange = { viewModel.updateDeduplicationEnabled(it) },
                                modifier = Modifier.scale(SETTINGS_SWITCH_SCALE)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_hide_single_news),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = userPreferences.isHideSingleNewsEnabled,
                                onCheckedChange = { viewModel.updateHideSingleNewsEnabled(it) },
                                modifier = Modifier.scale(SETTINGS_SWITCH_SCALE)
                            )
                        }
                        
                        Column {
                            Text(
                                stringResource(R.string.settings_local_deduplication_threshold, String.format(Locale.US, "%.2f", localDeduplicationThreshold)),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = localDeduplicationThreshold,
                                onValueChange = { localDeduplicationThreshold = it },
                                onValueChangeFinished = {
                                    viewModel.updateLocalDeduplicationThreshold(localDeduplicationThreshold)
                                },
                                valueRange = 0.3f..0.99f,
                                steps = 69,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    activeTickColor = MaterialTheme.colorScheme.primaryContainer,
                                    inactiveTickColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }

                        Column {
                            Text(
                                stringResource(R.string.settings_cloud_deduplication_threshold, String.format(Locale.US, "%.2f", cloudDeduplicationThreshold)),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = cloudDeduplicationThreshold,
                                onValueChange = { cloudDeduplicationThreshold = it },
                                onValueChangeFinished = {
                                    viewModel.updateCloudDeduplicationThreshold(cloudDeduplicationThreshold)
                                },
                                valueRange = 0.3f..0.99f,
                                steps = 69,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    activeTickColor = MaterialTheme.colorScheme.primaryContainer,
                                    inactiveTickColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }
                        
                        Column {
                            Text(
                                stringResource(R.string.settings_min_mentions, minMentions.toInt()),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = minMentions,
                                onValueChange = { minMentions = it },
                                onValueChangeFinished = {
                                    viewModel.updateMinMentions(minMentions.toInt())
                                },
                                valueRange = 1f..10f,
                                steps = 8,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    activeTickColor = MaterialTheme.colorScheme.primaryContainer,
                                    inactiveTickColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        val isModelReady = downloadState is ModelDownloadState.Ready
                        val statusText = when (val s = downloadState) {
                            is ModelDownloadState.Idle -> stringResource(R.string.model_status_idle)
                            is ModelDownloadState.Downloading -> stringResource(R.string.model_status_downloading, s.progress)
                            is ModelDownloadState.Loading -> stringResource(R.string.model_status_loading)
                            is ModelDownloadState.Ready -> stringResource(R.string.model_status_ready)
                            is ModelDownloadState.Error -> stringResource(R.string.model_status_error, s.message)
                        }
                        
                        Text(
                            stringResource(R.string.settings_model_status, statusText),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        Button(
                            onClick = { if (isModelReady) viewModel.deleteModel() else viewModel.downloadModel() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = if (isModelReady) 
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            else ButtonDefaults.buttonColors()
                        ) {
                            Text(
                                text = stringResource(if (isModelReady) R.string.settings_delete_model else R.string.settings_download_model),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
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
            AlertDialog(
                onDismissRequest = { helpDescription = null },
                title = { Text("Пояснення групи налаштувань") },
                text = { Text(helpDescription.orEmpty()) },
                confirmButton = {
                    TextButton(onClick = { helpDescription = null }) {
                        Text("OK")
                    }
                }
            )
        }

        showConfigDialog?.let { (config, type) ->
            AiConfigDialog(
                viewModel = viewModel,
                config = config,
                type = type,
                existingConfigs = if (type == AiModelType.SUMMARY) summaryConfigs else embeddingConfigs,
                onDismiss = { showConfigDialog = null },
                onConfirm = { viewModel.addAiConfig(it); showConfigDialog = null }
            )
        }

        if (showTimePicker) {
            ScheduledTimePickerDialog(
                hour = userPreferences.scheduledHour,
                minute = userPreferences.scheduledMinute,
                onDismiss = { showTimePicker = false },
                onConfirm = { h, m -> viewModel.updateScheduledSummary(true, h, m); showTimePicker = false }
            )
        }

        if (showClearArticlesDialog) {
            ConfirmDeleteDialog(
                title = stringResource(R.string.settings_clear_articles),
                text = stringResource(R.string.settings_clear_articles_confirm),
                onConfirm = { viewModel.clearAllArticles() },
                onDismiss = { showClearArticlesDialog = false }
            )
        }

        if (showClearEmbeddingsDialog) {
            ConfirmDeleteDialog(
                title = stringResource(R.string.settings_clear_embeddings),
                text = stringResource(R.string.settings_clear_embeddings_confirm),
                onConfirm = { viewModel.clearEmbeddings() },
                onDismiss = { showClearEmbeddingsDialog = false }
            )
        }

        if (showClearScheduledSummariesDialog) {
            ConfirmDeleteDialog(
                title = stringResource(R.string.settings_clear_scheduled_summaries),
                text = stringResource(R.string.settings_clear_scheduled_summaries_confirm),
                onConfirm = { viewModel.clearScheduledSummaries() },
                onDismiss = { showClearScheduledSummariesDialog = false }
            )
        }

        if (showResetSettingsDialog) {
            ConfirmDeleteDialog(
                title = stringResource(R.string.settings_reset_settings),
                text = stringResource(R.string.settings_reset_settings_confirm),
                onConfirm = { viewModel.resetSettingsToDefaults() },
                onDismiss = { showResetSettingsDialog = false }
            )
        }

        if (showEmailAuthDialog) {
            EmailAuthDialog(
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

private fun settingsGroupHelpDescription(group: SettingsGroup): String {
    return when (group) {
        SettingsGroup.ACCOUNT ->
            "Акаунт: вхід/вихід, хмарна синхронізація та перенос даних. " +
                "Тут зберігаються параметри резервного копіювання, імпорту/експорту і ручного sync, " +
                "щоб мати однакові налаштування та джерела на кількох пристроях."

        SettingsGroup.AI_PROCESSING ->
            "ШІ обробка: логіка формування зведень (стратегія, ліміти, дедуплікація, довжина і фільтри). " +
                "Ця група визначає якість/швидкість обробки і те, скільки контенту ШІ бере в аналіз."

        SettingsGroup.API_KEYS ->
            "API ключі: підключення провайдерів ШІ та вибір моделей для summary/embedding задач. " +
                "Тут додаються ключі, керується їх активність і перевіряється доступність моделей."

        SettingsGroup.RECOMMENDATIONS ->
            "Рекомендації: налаштування тематичних підписок і пов'язаних сценаріїв персоналізації контенту. " +
                "Допомагає швидко включати релевантні теми без ручного пошуку кожного джерела."

        SettingsGroup.FEED ->
            "Стрічка: параметри відображення новин у feed (медіа, описи, кількість елементів і поведінка карток). " +
                "Впливає на те, наскільки компактно або детально виглядає щоденний перегляд новин."

        SettingsGroup.SCHEDULED_SUMMARY ->
            "Заплановані зведення: час автогенерації, push-сповіщення та параметри регулярного запуску. " +
                "Дозволяє отримувати зведення автоматично в заданий час без ручного запуску."

        SettingsGroup.GENERAL ->
            "Загальні: мова застосунку, мова зведень, тема та інші базові глобальні параметри. " +
                "Це загальна поведінка інтерфейсу, яка застосовується до всіх екранів."

        SettingsGroup.MEMORY ->
            "Памʼять: сервісні дії з локальними даними (очищення, скидання, технічне обслуговування сховища). " +
                "Використовуйте обережно, бо деякі операції можуть видаляти кеш або накопичену історію."
    }
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}


@Composable
fun BackupOptionRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(SETTINGS_SWITCH_SCALE)
        )
    }
}

@Composable
fun BackupCheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun EmailAuthDialog(
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onGoogleLogin: () -> Unit
) {
    val emailRequiredMessage = stringResource(R.string.settings_validation_email_required)
    val emailInvalidMessage = stringResource(R.string.settings_validation_email_invalid)
    val passwordShortMessage = stringResource(R.string.settings_validation_password_short)
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_login_register),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        validationError = null
                    },
                    label = { Text(stringResource(R.string.settings_email)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        validationError = null
                    },
                    label = { Text(stringResource(R.string.settings_password)) },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val normalizedEmail = email.trim()
                        when {
                            normalizedEmail.isBlank() -> validationError = emailRequiredMessage
                            !android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches() ->
                                validationError = emailInvalidMessage
                            password.length < 6 -> validationError = passwordShortMessage
                            else -> {
                                onLogin(normalizedEmail, password)
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text(stringResource(R.string.settings_login))
                }

                OutlinedButton(
                    onClick = {
                        onGoogleLogin()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_google_logo),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_login_google))
                }

                OutlinedButton(
                    onClick = {
                        val normalizedEmail = email.trim()
                        when {
                            normalizedEmail.isBlank() -> validationError = emailRequiredMessage
                            !android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches() ->
                                validationError = emailInvalidMessage
                            password.length < 6 -> validationError = passwordShortMessage
                            else -> {
                                onRegister(normalizedEmail, password)
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text(stringResource(R.string.settings_register))
                }

                validationError?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun AiKeyItem(
    config: AiModelConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(config.provider.iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = config.name, 
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = config.modelName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Switch(
            checked = config.isEnabled, 
            onCheckedChange = onToggle, 
            modifier = Modifier.scale(0.75f)
        )
        IconButton(
            onClick = onEdit, 
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        IconButton(
            onClick = onDelete, 
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigDialog(
    viewModel: SettingsViewModel,
    config: AiModelConfig? = null,
    type: AiModelType,
    existingConfigs: List<AiModelConfig>,
    onDismiss: () -> Unit,
    onConfirm: (AiModelConfig) -> Unit
) {
    var name by remember(config?.id) { mutableStateOf(config?.name ?: "") }
    var apiKey by remember(config?.id) { mutableStateOf(config?.apiKey ?: "") }
    var provider by remember(config?.id) { mutableStateOf(config?.provider ?: AiProvider.GEMINI) }
    var modelName by remember(config?.id) { mutableStateOf(config?.modelName ?: "") }
    val providerLabel = stringResource(provider.labelRes)
    
    val availableModels by viewModel.availableModels.collectAsState()
    val isLoadingModels by viewModel.isLoadingModels.collectAsState()
    
    var expandedProvider by remember { mutableStateOf(false) }
    var expandedModel by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(if (config == null) R.string.settings_add_api_key else R.string.settings_edit_api_key),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.dialog_config_name)) },
                        placeholder = { Text(stringResource(R.string.dialog_config_name_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.large
                    )

                    ExposedDropdownMenuBox(
                        expanded = expandedProvider,
                        onExpandedChange = { expandedProvider = !expandedProvider }
                    ) {
                        OutlinedTextField(
                            value = stringResource(provider.labelRes),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.dialog_provider)) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(provider.iconRes),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvider) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        )
                        ExposedDropdownMenu(
                            expanded = expandedProvider,
                            onDismissRequest = { expandedProvider = false }
                        ) {
                            AiProvider.entries.forEach { entry ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(entry.labelRes)) },
                                    onClick = {
                                        provider = entry
                                        modelName = ""
                                        expandedProvider = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(entry.iconRes),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(stringResource(R.string.dialog_api_key)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.large
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExposedDropdownMenuBox(
                            expanded = expandedModel,
                            onExpandedChange = { if (availableModels.isNotEmpty()) expandedModel = !expandedModel },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = modelName,
                                onValueChange = { modelName = it },
                                label = { Text(stringResource(R.string.dialog_model)) },
                                placeholder = {
                                    Text(
                                        if (isLoadingModels) stringResource(R.string.dialog_model_loading)
                                        else stringResource(R.string.dialog_model_select)
                                    )
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModel) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryEditable, true)
                                    .fillMaxWidth(),
                                shape = MaterialTheme.shapes.large
                            )
                            if (availableModels.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = expandedModel,
                                    onDismissRequest = { expandedModel = false }
                                ) {
                                    availableModels.forEach { model ->
                                        DropdownMenuItem(
                                            text = { Text(model) },
                                            onClick = {
                                                modelName = model
                                                expandedModel = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        if (isLoadingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = { viewModel.loadModels(provider, apiKey, type) }) {
                                Text(stringResource(R.string.dialog_load))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            val trimmedApiKey = apiKey.trim()
                            val trimmedModelName = modelName.trim()
                            val resolvedName = name.trim().ifBlank {
                                buildAutoAiConfigName(
                                    providerLabel = providerLabel,
                                    provider = provider,
                                    existingConfigs = existingConfigs,
                                    editingConfigId = config?.id
                                )
                            }
                            if (trimmedApiKey.isNotBlank() && trimmedModelName.isNotBlank()) {
                                onConfirm(
                                    config?.copy(
                                        name = resolvedName,
                                        provider = provider,
                                        apiKey = trimmedApiKey,
                                        modelName = trimmedModelName
                                    ) ?: AiModelConfig(
                                        name = resolvedName,
                                        provider = provider,
                                        apiKey = trimmedApiKey,
                                        modelName = trimmedModelName,
                                        type = type
                                    )
                                )
                            }
                        },
                        enabled = apiKey.isNotBlank() && modelName.isNotBlank() && !isLoadingModels,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(stringResource(if (config == null) R.string.add else R.string.save))
                    }
                }
            }

        }
    }
}

private fun buildAutoAiConfigName(
    providerLabel: String,
    provider: AiProvider,
    existingConfigs: List<AiModelConfig>,
    editingConfigId: Long?
): String {
    val usedNumbers = existingConfigs
        .asSequence()
        .filter { it.id != editingConfigId && it.provider == provider }
        .mapNotNull { config ->
            val normalizedName = config.name.trim()
            val prefix = "$providerLabel "
            if (normalizedName.startsWith(prefix)) {
                normalizedName.removePrefix(prefix).toIntOrNull()
            } else {
                null
            }
        }
        .toSet()

    val nextNumber = generateSequence(1) { it + 1 }
        .first { it !in usedNumbers }

    return "$providerLabel $nextNumber"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTimePickerDialog(
    hour: Int, 
    minute: Int, 
    onDismiss: () -> Unit, 
    onConfirm: (Int, Int) -> Unit
) {
    val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute)
    
    AlertDialog(
        onDismissRequest = onDismiss, 
        confirmButton = { 
            TextButton(onClick = { onConfirm(timeState.hour, timeState.minute) }) { 
                Text(stringResource(R.string.ok)) 
            } 
        }, 
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text(stringResource(R.string.cancel)) 
            } 
        }, 
        text = { 
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = timeState)
            }
        }
    )
}







