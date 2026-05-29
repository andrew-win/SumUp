package com.andrewwin.sumup.ui.screen.feed.model

import androidx.annotation.StringRes
import com.andrewwin.sumup.R

enum class SavedFilter(@StringRes val labelRes: Int, val savedOnly: Boolean) {
    ALL(R.string.filter_saved_all, false),
    SAVED(R.string.filter_saved_only, true)
}
