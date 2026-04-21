package com.andrewwin.sumup.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.andrewwin.sumup.R
import com.andrewwin.sumup.data.local.entities.SourceGroup

@Composable
fun SourceGroup.displayName(): String {
    return if (id == 1L && !isDeletable) {
        stringResource(R.string.group_uncategorized)
    } else {
        name
    }
}
