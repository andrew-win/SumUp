package com.andrewwin.sumup.ui.screen.instruction

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andrewwin.sumup.R
import com.andrewwin.sumup.domain.settings.model.AppThemeMode
import com.andrewwin.sumup.ui.components.AppAnimatedDialog
import com.andrewwin.sumup.ui.components.AppTopBar
import com.andrewwin.sumup.ui.theme.SumUpTheme
import com.andrewwin.sumup.ui.theme.appBorderColor
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

@Composable
fun InstructionScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pages = rememberInstructionPages()
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    var language by rememberSaveable { mutableStateOf(InstructionLanguage.UK) }
    var themeMode by rememberSaveable { mutableStateOf(AppThemeMode.SYSTEM) }
    var expandedImageRes by remember { mutableStateOf<Int?>(null) }
    val currentPage = pages[pageIndex.coerceIn(pages.indices)]

    BackHandler {
        onFinish()
    }

    SumUpTheme(themeMode = themeMode) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = { Text(instructionString(R.string.instruction_title, language)) },
                actions = {
                    IconButton(
                        onClick = {
                            language = when (language) {
                                InstructionLanguage.UK -> InstructionLanguage.EN
                                InstructionLanguage.EN -> InstructionLanguage.UK
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = instructionString(R.string.instruction_toggle_language, language)
                        )
                    }
                    IconButton(
                        onClick = {
                            themeMode = when (themeMode) {
                                AppThemeMode.SYSTEM -> AppThemeMode.LIGHT
                                AppThemeMode.LIGHT -> AppThemeMode.DARK
                                AppThemeMode.DARK -> AppThemeMode.SYSTEM
                            }
                        }
                    ) {
                        val isDark = when (themeMode) {
                            AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                            AppThemeMode.LIGHT -> false
                            AppThemeMode.DARK -> true
                        }
                        Icon(
                            imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = instructionString(R.string.instruction_toggle_theme, language)
                        )
                    }
                }
            )
        },
        bottomBar = {
            InstructionBottomBar(
                pageIndex = pageIndex,
                pageCount = pages.size,
                language = language,
                onSkip = onFinish,
                onBack = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                onNext = {
                    if (pageIndex == pages.lastIndex) {
                        onFinish()
                    } else {
                        pageIndex++
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Text(
                    text = instructionString(currentPage.titleRes, language),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }
            currentPage.blocks.forEach { block ->
                when (block) {
                    is InstructionBlock.Text -> item {
                        Text(
                            text = instructionString(block.textRes, language),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                        )
                    }
                    is InstructionBlock.Image -> item {
                        InstructionImagePlaceholder(
                            drawableRes = block.drawableRes,
                            language = language,
                            onOpen = { expandedImageRes = block.drawableRes }
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
        InstructionImageDialog(
            drawableRes = expandedImageRes,
            onDismiss = { expandedImageRes = null }
        )
    }
}

@Composable
private fun InstructionBottomBar(
    pageIndex: Int,
    pageCount: Int,
    language: InstructionLanguage,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, appBorderColor())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = instructionString(R.string.instruction_page_counter, language, pageIndex + 1, pageCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pageIndex == 0) {
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(instructionString(R.string.instruction_skip, language))
                    }
                    Button(
                        onClick = onNext,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(instructionString(R.string.instruction_start, language))
                    }
                } else {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(instructionString(R.string.back, language))
                    }
                    Button(
                        onClick = onNext,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            instructionString(
                                if (pageIndex == pageCount - 1) {
                                    R.string.instruction_finish
                                } else {
                                    R.string.next
                                },
                                language
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionImagePlaceholder(
    @DrawableRes drawableRes: Int,
    language: InstructionLanguage,
    onOpen: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .height(360.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, appBorderColor())
    ) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = instructionString(R.string.instruction_image_placeholder, language),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .padding(8.dp)
        )
    }
}

@Composable
private fun InstructionImageDialog(
    @DrawableRes drawableRes: Int?,
    onDismiss: () -> Unit
) {
    AppAnimatedDialog(
        visible = drawableRes != null,
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val imageRes = drawableRes ?: return@AppAnimatedDialog
        var scale by remember(imageRes) { mutableStateOf(1f) }
        var offset by remember(imageRes) { mutableStateOf(Offset.Zero) }
        var viewportSize by remember(imageRes) { mutableStateOf(Size.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(8.dp)
                .clipToBounds()
                .onSizeChanged { viewportSize = Size(it.width.toFloat(), it.height.toFloat()) }
        ) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(imageRes, scale, viewportSize) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                if (viewportSize == Size.Zero) return@detectTapGestures

                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    val nextScale = 2f
                                    scale = nextScale
                                    offset = clampInstructionImageOffset(
                                        rawOffset = Offset(
                                            x = (viewportSize.width / 2f - tapOffset.x) * (nextScale - 1f),
                                            y = (viewportSize.height / 2f - tapOffset.y) * (nextScale - 1f)
                                        ),
                                        scale = nextScale,
                                        viewportSize = viewportSize
                                    )
                                }
                            }
                        )
                    }
                    .pointerInput(imageRes) {
                        detectTransformGestures { _: Offset, pan: Offset, zoom: Float, _: Float ->
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            if (nextScale <= 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = nextScale
                                offset = clampInstructionImageOffset(
                                    rawOffset = offset + pan,
                                    scale = nextScale,
                                    viewportSize = viewportSize
                                )
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    }
}

private fun clampInstructionImageOffset(
    rawOffset: Offset,
    scale: Float,
    viewportSize: Size
): Offset {
    if (scale <= 1f || viewportSize == Size.Zero) return Offset.Zero

    val maxX = ((viewportSize.width * scale) - viewportSize.width) / 2f
    val maxY = ((viewportSize.height * scale) - viewportSize.height) / 2f

    return Offset(
        x = rawOffset.x.coerceIn(-maxX, maxX),
        y = rawOffset.y.coerceIn(-maxY, maxY)
    )
}

@Composable
private fun rememberInstructionPages(): List<InstructionPage> {
    return remember {
        listOf(
            InstructionPage(
                titleRes = R.string.instruction_welcome_title,
                blocks = listOf(
                    InstructionBlock.Text(R.string.instruction_welcome_body)
                )
            ),
            InstructionPage(
                titleRes = R.string.instruction_summary_title,
                blocks = listOf(
                    InstructionBlock.Text(R.string.instruction_summary_scheduled_body),
                    InstructionBlock.Image(R.drawable.onboarding_summary_scheduled),
                    InstructionBlock.Text(R.string.instruction_summary_enable_body),
                    InstructionBlock.Image(R.drawable.onboarding_summary_settings),
                    InstructionBlock.Text(R.string.instruction_summary_history_export_body),
                    InstructionBlock.Image(R.drawable.onboarding_summary_history_export),
                    InstructionBlock.Text(R.string.instruction_summary_stats_body),
                    InstructionBlock.Image(R.drawable.onboarding_summary_stats)
                )
            ),
            InstructionPage(
                titleRes = R.string.instruction_feed_title,
                blocks = listOf(
                    InstructionBlock.Text(R.string.instruction_feed_intro_body),
                    InstructionBlock.Image(R.drawable.onboarding_feed_example),
                    InstructionBlock.Text(R.string.instruction_feed_settings_body),
                    InstructionBlock.Image(R.drawable.onboarding_feed_settings),
                    InstructionBlock.Text(R.string.instruction_feed_article_summary_body),
                    InstructionBlock.Image(R.drawable.onboarding_article_summary),
                    InstructionBlock.Text(R.string.instruction_feed_full_summary_body),
                    InstructionBlock.Image(R.drawable.onboarding_feed_summary)
                )
            ),
            InstructionPage(
                titleRes = R.string.instruction_sources_title,
                blocks = listOf(
                    InstructionBlock.Text(R.string.instruction_sources_intro_body),
                    InstructionBlock.Image(R.drawable.onboarding_sources_custom),
                    InstructionBlock.Text(R.string.instruction_sources_add_body),
                    InstructionBlock.Image(R.drawable.onboarding_source_add),
                    InstructionBlock.Text(R.string.instruction_sources_subscriptions_body),
                    InstructionBlock.Image(R.drawable.onboarding_subscriptions)
                )
            ),
            InstructionPage(
                titleRes = R.string.instruction_settings_title,
                blocks = listOf(
                    InstructionBlock.Text(R.string.instruction_settings_intro_body),
                    InstructionBlock.Image(R.drawable.onboarding_settings_example),
                    InstructionBlock.Text(R.string.instruction_settings_help_mode_body),
                    InstructionBlock.Image(R.drawable.onboarding_settings_help_mode),
                    InstructionBlock.Text(R.string.instruction_settings_sections_body)
                )
            )
        )
    }
}

private data class InstructionPage(
    @StringRes val titleRes: Int,
    val blocks: List<InstructionBlock>
)

private sealed interface InstructionBlock {
    data class Text(@StringRes val textRes: Int) : InstructionBlock
    data class Image(@DrawableRes val drawableRes: Int) : InstructionBlock
}

private enum class InstructionLanguage(
    val locale: Locale
) {
    UK(Locale.forLanguageTag("uk")),
    EN(Locale.ENGLISH)
}

@Composable
private fun instructionString(
    @StringRes id: Int,
    language: InstructionLanguage,
    vararg formatArgs: Any
): String {
    val context = LocalContext.current
    return remember(id, language, formatArgs.contentHashCode()) {
        val configuration = android.content.res.Configuration(context.resources.configuration)
        configuration.setLocale(language.locale)
        val localizedContext = context.createConfigurationContext(configuration)
        localizedContext.getString(id, *formatArgs)
    }
}
