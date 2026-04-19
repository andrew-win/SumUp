package com.andrewwin.sumup.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val AppCardShape: Shape = RoundedCornerShape(18.dp)

@Composable
fun appCardBorder(
    color: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
): BorderStroke = BorderStroke(1.dp, color)

@Composable
fun appCardColors(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
): CardColors = CardDefaults.cardColors(containerColor = containerColor)
