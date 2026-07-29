package com.biosensor.migratedev.translation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.biosensor.migratedev.decisioncore.auth.AuthDecisionCore
import com.biosensor.migratedev.decisioncore.auth.AuthEvent
import com.biosensor.migratedev.decisioncore.auth.AuthState
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.orchestrator.WorkflowOrchestrator
import com.biosensor.migratedev.orchestrator.auth.AuthEffectExecutor
import com.biosensor.migratedev.port.auth.AuthPort
import com.biosensor.migratedev.translation.Translation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface AuthIntent {
    data class SubmitLogin(
        val phone: String, val password: String
    ) : AuthIntent

    data class SubmitRegister(
        val phone: String, val password: String, val nickname: String, val avatarUrl: String? = null
    ) : AuthIntent

    data object RetrySession : AuthIntent
    data object Logout : AuthIntent
    data object Reset : AuthIntent
}

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object RestoringSession : AuthUiState
    data object Loading : AuthUiState
    data object SavingSession : AuthUiState
    data class Registered(val message: String) : AuthUiState
    data class Authenticated(val user: User) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthTranslation private constructor(
    port: AuthPort
) : ViewModel(), Translation<AuthIntent, AuthUiState> {
    private val orchestrator = WorkflowOrchestrator(
        initialState = AuthState.Idle,
        decisionCore = AuthDecisionCore,
        effectExecutor = AuthEffectExecutor(port),
        scope = viewModelScope
    )

    override val uiState: StateFlow<AuthUiState> =
        orchestrator.state.map(AuthState::toUiState).stateIn(
            viewModelScope, SharingStarted.Eagerly, AuthState.Idle.toUiState()
        )

    init {
        orchestrator.dispatch(AuthEvent.AuthCreated)
    }

    override fun submit(intent: AuthIntent) {
        orchestrator.dispatch(
            when (intent) {
                is AuthIntent.SubmitLogin -> AuthEvent.SubmitLogin(
                    phone = intent.phone, password = intent.password
                )

                is AuthIntent.SubmitRegister -> AuthEvent.SubmitRegister(
                    phone = intent.phone,
                    password = intent.password,
                    nickname = intent.nickname,
                    avatarUrl = intent.avatarUrl
                )

                AuthIntent.RetrySession -> AuthEvent.AuthCreated

                AuthIntent.Logout -> AuthEvent.Logout
                AuthIntent.Reset -> AuthEvent.Reset
            }
        )
    }

    override fun onCleared() {
        orchestrator.close()
    }

    companion object {
        fun factory(port: AuthPort): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>
                ): T {
                    require(
                        modelClass.isAssignableFrom(
                            AuthTranslation::class.java
                        )
                    )
                    return AuthTranslation(port) as T
                }
            }
    }
}

private fun AuthState.toUiState(): AuthUiState = when (this) {
    AuthState.Idle -> AuthUiState.Idle
    AuthState.RestoringSession -> AuthUiState.RestoringSession
    AuthState.Loading -> AuthUiState.Loading
    is AuthState.SavingSession -> AuthUiState.SavingSession
    is AuthState.Registered -> AuthUiState.Registered(message)
    is AuthState.Authenticated -> AuthUiState.Authenticated(session.user)

    is AuthState.Error -> AuthUiState.Error(message)
}
