package com.andrewwin.sumup.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.andrewwin.sumup.ui.theme.AppCardShape
import com.andrewwin.sumup.ui.theme.appCardBorder

@Composable
fun AppCardSurface(
    modifier: Modifier = Modifier,
    shape: Shape = AppCardShape,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    border: BorderStroke = appCardBorder(),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        border = border,
        content = content
    )
}
