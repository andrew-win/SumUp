package com.andrewwin.sumup.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.andrewwin.sumup.ui.components.AppHelpOverlayTarget

@Composable
internal fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isHelpMode: Boolean = false,
    helpDescription: String? = null,
    onHelpRequest: ((String) -> Unit)? = null,
    contentDescription: String = label
) {
    SettingsHelpTarget(
        isHelpMode = isHelpMode,
        helpDescription = helpDescription,
        onHelpRequest = onHelpRequest,
        contentDescription = contentDescription
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier
                    .scale(SETTINGS_SWITCH_SCALE)
                    .semantics {
                        this.contentDescription = contentDescription
                    }
            )
        }
    }
}

@Composable
internal fun SettingsIntSliderItem(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    isHelpMode: Boolean = false,
    helpDescription: String? = null,
    onHelpRequest: ((String) -> Unit)? = null,
    contentDescription: String = label
) {
    SettingsHelpTarget(
        isHelpMode = isHelpMode,
        helpDescription = helpDescription,
        onHelpRequest = onHelpRequest,
        contentDescription = contentDescription
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.semantics {
                    this.contentDescription = contentDescription
                }
            )
        }
    }
}

@Composable
internal fun SettingsFloatSliderItem(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    isHelpMode: Boolean = false,
    helpDescription: String? = null,
    onHelpRequest: ((String) -> Unit)? = null,
    contentDescription: String = label
) {
    SettingsHelpTarget(
        isHelpMode = isHelpMode,
        helpDescription = helpDescription,
        onHelpRequest = onHelpRequest,
        contentDescription = contentDescription
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.semantics {
                    this.contentDescription = contentDescription
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    activeTickColor = MaterialTheme.colorScheme.primaryContainer,
                    inactiveTickColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }
    }
}

@Composable
internal fun SettingsHelpTarget(
    isHelpMode: Boolean,
    helpDescription: String?,
    onHelpRequest: ((String) -> Unit)?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val semanticsModifier = if (contentDescription.isNullOrBlank()) {
        modifier
    } else {
        modifier.semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
        }
    }
    val wrappedContent: @Composable () -> Unit = {
        Box(modifier = semanticsModifier) {
            content()
        }
    }
    if (!helpDescription.isNullOrBlank() && onHelpRequest != null) {
        AppHelpOverlayTarget(
            isEnabled = isHelpMode,
            description = helpDescription,
            onShowDescription = onHelpRequest
        ) {
            wrappedContent()
        }
    } else {
        wrappedContent()
    }
}
