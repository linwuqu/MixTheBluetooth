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
    data class SubmitLogin(val phone: String, val password: String) : AuthIntent
    data class SubmitRegister(
        val phone: String,
        val password: String,
        val nickname: String,
        val avatarUrl: String? = null
    ) : AuthIntent

    data object Logout : AuthIntent
    data object Reset : AuthIntent
}

sealed interface AuthLifecycleEvent {
    data object AppStarted : AuthLifecycleEvent
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

class AuthTranslation(
    private val orchestrator: WorkflowOrchestrator<AuthState, AuthEvent, AuthEffect>,
    scope: CoroutineScope
) : Translation<AuthIntent, AuthUiState> {

    /** 将状态机的热流 (StateFlow<AuthState>) 映射为 UI 专用热流 (StateFlow<AuthUiState>)。
    1. orchestrator.state 本身是热流，始终持有最新 AuthState。
    2. .map { it.toUiState() } 生成一个冷流，仅在收集时按需转换。
    3. .stateIn(scope, Eagerly, 初始值) 立刻启动收集，使冷流变热，
    并确保任意时刻订阅 uiState 都能立即拿到当前 UI 状态（初始值就是当前状态机的值转换后的结果）。*/
    override val uiState: StateFlow<AuthUiState> = orchestrator.state.map { it.toUiState() }
        .stateIn(scope, SharingStarted.Eagerly, orchestrator.state.value.toUiState())

    override fun submit(intent: AuthIntent) {
        orchestrator.dispatch(intent.toEvent())
    }

    fun onLifecycle(event: AuthLifecycleEvent) {
        val authEvent = when (event) {
            AuthLifecycleEvent.AppStarted -> AuthEvent.AppStarted
        }
        orchestrator.dispatch(authEvent)
    }

    private fun AuthIntent.toEvent(): AuthEvent {
        return when (this) {
            is AuthIntent.SubmitLogin -> AuthEvent.SubmitLogin(phone = phone, password = password)
            is AuthIntent.SubmitRegister -> AuthEvent.SubmitRegister(
                phone = phone,
                password = password,
                nickname = nickname,
                avatarUrl = avatarUrl
            )

            AuthIntent.Logout -> AuthEvent.Logout
            AuthIntent.Reset -> AuthEvent.Reset
        }
    }

    private fun AuthState.toUiState(): AuthUiState {
        return when (this) {
            AuthState.Idle -> AuthUiState.Idle
            AuthState.RestoringSession -> AuthUiState.RestoringSession
            AuthState.Loading -> AuthUiState.Loading
            is AuthState.SavingSession -> AuthUiState.SavingSession
            is AuthState.Registered -> AuthUiState.Registered(message)
            // 故意隐藏了 session.token 这些 UI 不该知道的敏感信息
            is AuthState.Authenticated -> AuthUiState.Authenticated(session.user)
            is AuthState.Error -> AuthUiState.Error(message)
        }
    }
}
