package com.biosensor.migratedev.translation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.biosensor.migratedev.decisioncore.auth.AuthDecisionCore
import com.biosensor.migratedev.decisioncore.auth.AuthEvent
import com.biosensor.migratedev.decisioncore.auth.AuthState
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.orchestrator.WorkflowOrchestrator
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

sealed interface AuthOutput {
    data class Authenticated(val userId: String) : AuthOutput
    data object SessionCleared : AuthOutput
}

/**
 * 对于子编排器的逻辑理解主要把握住两个继承
 * : Translation<AuthIntent, AuthUiState>
 * 1. override val uiState -> private fun AuthState.toUiState()
 * 2. override fun submit(intent: AuthIntent) 这里就是子编排器的完整业务逻辑
 *
 * : ViewModel()
 * 1. override fun onCleared()
 * 2. 传入 orchestrator 的参数从外部 scope: CoroutineScope 变成 viewModelScope
 * 3. onTransition = ::reportRootOutput 将切面进行上报 即 init 时 object: ViewModelProvider.Factory 传入的 ::report
 * 4. 建立一个伴随 object.factory 使用时理解成 static 成员函数即可
 */
class AuthTranslation private constructor(
    port: AuthPort, private val report: (AuthOutput) -> Unit
) : ViewModel(), Translation<AuthIntent, AuthUiState> {
    private val orchestrator = WorkflowOrchestrator(
        initialState = AuthState.Idle,
        decisionCore = AuthDecisionCore,
        effectExecutor = port::execute,
        scope = viewModelScope,
        logTag = "Auth.Workflow",
        onTransition = ::reportRootOutput
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

    private fun reportRootOutput(
        previous: AuthState, event: AuthEvent, current: AuthState
    ) {
        if (current is AuthState.Authenticated && previous !is AuthState.Authenticated) {
            report(AuthOutput.Authenticated(current.session.user.id))
        }
        if (event == AuthEvent.SessionCleared) {
            report(AuthOutput.SessionCleared)
        }
    }

    companion object {
        fun factory(
            port: AuthPort, report: (AuthOutput) -> Unit = {}
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>
            ): T {
                require(
                    modelClass.isAssignableFrom(
                        AuthTranslation::class.java
                    )
                )
                return AuthTranslation(port, report) as T
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
