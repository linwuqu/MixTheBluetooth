package com.biosensor.migratedev.translation.auth

import com.biosensor.migratedev.decisioncore.auth.AuthEvent
import com.biosensor.migratedev.decisioncore.auth.AuthEffect
import com.biosensor.migratedev.decisioncore.auth.AuthState
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.orchestrator.WorkflowOrchestrator
import com.biosensor.migratedev.translation.Translation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface AuthIntent {
    data class SubmitLogin(val account: String, val password: String) : AuthIntent
    data class SubmitRegister(
        val account: String,
        val password: String,
        val telephone: String,
        val avatarUrl: String? = null
    ) : AuthIntent

    data object Logout : AuthIntent
    data object Reset : AuthIntent
}

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data object SavingSession : AuthUiState
    data class Authenticated(val user: User) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthTranslation(
    private val orchestrator: WorkflowOrchestrator<AuthState, AuthEvent, AuthEffect>,
    scope: CoroutineScope
) : Translation<AuthIntent, AuthUiState> {

    override val uiState: StateFlow<AuthUiState> = orchestrator.state
        .map { it.toUiState() }
        .stateIn(scope, SharingStarted.Eagerly, orchestrator.state.value.toUiState())

    override fun submit(intent: AuthIntent) {
        orchestrator.dispatch(intent.toEvent())
    }

    private fun AuthIntent.toEvent(): AuthEvent {
        return when (this) {
            is AuthIntent.SubmitLogin -> AuthEvent.SubmitLogin(account, password)
            is AuthIntent.SubmitRegister -> AuthEvent.SubmitRegister(
                account,
                password,
                telephone,
                avatarUrl
            )

            AuthIntent.Logout -> AuthEvent.Logout
            AuthIntent.Reset -> AuthEvent.Reset
        }
    }

    private fun AuthState.toUiState(): AuthUiState {
        return when (this) {
            AuthState.Idle -> AuthUiState.Idle
            AuthState.Loading -> AuthUiState.Loading
            is AuthState.SavingSession -> AuthUiState.SavingSession
            is AuthState.Authenticated -> AuthUiState.Authenticated(session.user)
            is AuthState.Error -> AuthUiState.Error(message)
        }
    }
}
