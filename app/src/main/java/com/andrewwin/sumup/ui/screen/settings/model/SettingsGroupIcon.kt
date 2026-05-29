package com.andrewwin.sumup.ui.screen.settings.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector

sealed class SettingsGroupIcon {
    data class Vector(val imageVector: ImageVector) : SettingsGroupIcon()
    data class Drawable(@DrawableRes val resId: Int) : SettingsGroupIcon()
}
