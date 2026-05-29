package com.andrewwin.sumup.ui.screen.settings.model

data class AuthUiState(
    val isSignedIn: Boolean = false,
    val displayName: String = "",
    val email: String = ""
)
