package com.andrewwin.sumup.ui.screen.settings.model

sealed interface TransferState {
    data object Idle : TransferState
    data object Working : TransferState
    data class Success(val message: String) : TransferState
    data class Error(val message: String) : TransferState
}
