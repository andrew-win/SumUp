package com.andrewwin.sumup.ui.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationIcon {
    data class Vector(val imageVector: ImageVector) : NavigationIcon()
    data class Custom(@DrawableRes val resId: Int) : NavigationIcon()
}
