package com.andrewwin.sumup.ui.screen.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.AiStrategy

@Composable
internal fun FeedAiSummaryBottomActions(
    isFeedAiActive: Boolean,
    isAiLoading: Boolean,
    isFeedEmpty: Boolean,
    aiStrategy: AiStrategy,
    userQuestion: String,
    onQuestionChange: (String) -> Unit,
    onAsk: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (aiStrategy != AiStrategy.LOCAL) {
                OutlinedTextField(
                    value = userQuestion,
                    onValueChange = onQuestionChange,
                    label = null,
                    placeholder = {
                        Text(text = context.getString(R.string.ai_question_input_placeholder))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    trailingIcon = {
                        IconButton(
                            onClick = onAsk,
                            enabled = userQuestion.isNotBlank() && !isAiLoading && !isFeedEmpty
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        }
                    }
                )
            }

            Button(
                onClick = onRegenerate,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isFeedEmpty,
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ask_ai),
                    contentDescription = null
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (isFeedAiActive) {
                        context.getString(R.string.ai_summarize_feed)
                    } else {
                        context.getString(R.string.ai_summarize_article)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}
