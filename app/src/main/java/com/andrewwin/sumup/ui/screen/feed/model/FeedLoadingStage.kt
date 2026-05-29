package com.andrewwin.sumup.ui.screen.feed.model

import androidx.annotation.StringRes
import com.andrewwin.sumup.R

enum class FeedLoadingStage(@StringRes val messageRes: Int) {
    LOADING_FROM_DATABASE(R.string.feed_loading_from_database),
    PARSING_NEWS(R.string.feed_loading_news),
    DEDUPLICATING_NEWS(R.string.feed_deduplicating),
    BUILDING_UPDATED_FEED(R.string.feed_building_updated_feed)
}
