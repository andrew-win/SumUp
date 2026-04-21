package com.andrewwin.sumup.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

object AppMotion {
    const val ScreenDurationMs = 180
    const val ScreenPopDurationMs = 160
    const val ContentDurationMs = 190
    const val ModalDurationMs = 180

    fun screenEnter(): EnterTransition =
        fadeIn(animationSpec = tween(ScreenDurationMs, easing = FastOutSlowInEasing)) +
            slideInHorizontally(
                animationSpec = tween(ScreenDurationMs, easing = FastOutSlowInEasing),
                initialOffsetX = { fullWidth -> fullWidth / 28 }
            )

    fun screenExit(): ExitTransition =
        fadeOut(animationSpec = tween(ScreenDurationMs / 2, easing = FastOutSlowInEasing)) +
            slideOutHorizontally(
                animationSpec = tween(ScreenDurationMs, easing = FastOutSlowInEasing),
                targetOffsetX = { fullWidth -> -fullWidth / 32 }
            )

    fun screenPopEnter(): EnterTransition =
        fadeIn(animationSpec = tween(ScreenPopDurationMs, easing = FastOutSlowInEasing)) +
            slideInHorizontally(
                animationSpec = tween(ScreenPopDurationMs, easing = FastOutSlowInEasing),
                initialOffsetX = { fullWidth -> -fullWidth / 28 }
            )

    fun screenPopExit(): ExitTransition =
        fadeOut(animationSpec = tween(ScreenPopDurationMs / 2, easing = FastOutSlowInEasing)) +
            slideOutHorizontally(
                animationSpec = tween(ScreenPopDurationMs, easing = FastOutSlowInEasing),
                targetOffsetX = { fullWidth -> fullWidth / 32 }
            )

    fun contentEnter(): EnterTransition =
        fadeIn(animationSpec = tween(ContentDurationMs, easing = FastOutSlowInEasing)) +
            slideInVertically(
                animationSpec = tween(ContentDurationMs, easing = FastOutSlowInEasing),
                initialOffsetY = { fullHeight -> fullHeight / 24 }
            )

    fun contentExit(): ExitTransition =
        fadeOut(animationSpec = tween(ContentDurationMs / 2, easing = FastOutSlowInEasing)) +
            slideOutVertically(
                animationSpec = tween(ContentDurationMs, easing = FastOutSlowInEasing),
                targetOffsetY = { fullHeight -> -fullHeight / 28 }
            )

    fun modalEnter(): EnterTransition =
        fadeIn(animationSpec = tween(ModalDurationMs, easing = FastOutSlowInEasing)) +
            scaleIn(
                animationSpec = tween(ModalDurationMs, easing = FastOutSlowInEasing),
                initialScale = 0.975f
            )

    fun modalExit(): ExitTransition =
        fadeOut(animationSpec = tween(ModalDurationMs / 2, easing = FastOutSlowInEasing)) +
            scaleOut(
                animationSpec = tween(ModalDurationMs, easing = FastOutSlowInEasing),
                targetScale = 0.985f
            )
}

@Composable
fun <T> AppAnimatedSwap(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String = "AppAnimatedSwap",
    content: @Composable AnimatedContentScope.(T) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            AppMotion.contentEnter().togetherWith(
                AppMotion.contentExit()
            ).using(
                SizeTransform(clip = false)
            )
        },
        label = label,
        content = content
    )
}

@Composable
fun AppAnimatedDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    contentAlignment: Alignment = Alignment.Center,
    enter: EnterTransition = AppMotion.modalEnter(),
    exit: ExitTransition = AppMotion.modalExit(),
    content: @Composable BoxScope.() -> Unit
) {
    if (visible) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = properties
        ) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = contentAlignment
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = enter,
                    exit = exit
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = contentAlignment,
                        content = content
                    )
                }
            }
        }
    }
}
