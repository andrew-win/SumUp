package com.andrewwin.sumup.data.local.entities

import com.andrewwin.sumup.R

enum class SourceType {
    TELEGRAM, RSS, YOUTUBE;

    val labelRes: Int
        get() = when (this) {
            TELEGRAM -> R.string.source_type_telegram
            RSS -> R.string.source_type_rss
            YOUTUBE -> R.string.source_type_youtube
        }
}






