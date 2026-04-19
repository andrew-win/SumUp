package com.andrewwin.sumup.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.andrewwin.sumup.ui.theme.AppCardShape
import com.andrewwin.sumup.ui.theme.appCardBorder
import com.andrewwin.sumup.ui.theme.appCardColors

@Composable
fun SettingsSection(
    title: String,
    boxed: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    headerContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val hasHeader = title.isNotBlank() || trailing != null
    val contentComposable: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            if (hasHeader) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (title.isNotBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Spacer(modifier = Modifier)
                    }
                    trailing?.invoke()
                }
                androidx.compose.material3.HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            headerContent?.invoke()
            content()
        }
    }

    if (boxed) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppCardShape,
            colors = appCardColors(),
            border = appCardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                contentComposable()
            }
        }
    } else {
        contentComposable()
    }
}
