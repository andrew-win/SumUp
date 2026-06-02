package com.andrewwin.sumup.ui.screen.sources

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.andrewwin.sumup.R
import com.andrewwin.sumup.domain.source.model.SourceGroup

private const val UNCATEGORIZED_GROUP_ID = 1L

@Composable
fun SourceGroup.displayName(): String {
    return if (id == UNCATEGORIZED_GROUP_ID && !isDeletable) {
        stringResource(R.string.group_uncategorized)
    } else {
        name
    }
}
