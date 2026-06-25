package com.biosensor.migratedev.business.account.presentation

import com.biosensor.migratedev.promise.AccountSession
import com.biosensor.migratedev.promise.LoginInput
import com.biosensor.migratedev.promise.RegisterInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AccountViewModelContract {
    val state: StateFlow<AccountUiState>
    val effects: Flow<AccountUiEffect>

    fun onIntent(intent: AccountIntent)
}

data class AccountUiState(
    val session: AccountSession? = null,
    val busy: Boolean = false,
    val errorMessage: String? = null
)

sealed interface AccountIntent {
    data class Login(val input: LoginInput) : AccountIntent
    data class Register(val input: RegisterInput) : AccountIntent
    data class Rename(val newName: String) : AccountIntent
    data object Logout : AccountIntent
    data object RefreshSession : AccountIntent
}

sealed interface AccountUiEffect {
    data class ShowMessage(val message: String) : AccountUiEffect
    data object NavigateToLogin : AccountUiEffect
    data object NavigateToHome : AccountUiEffect
}
