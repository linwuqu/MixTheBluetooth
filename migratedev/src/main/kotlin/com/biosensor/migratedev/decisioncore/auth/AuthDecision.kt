package com.biosensor.migratedev.decisioncore.auth

import com.biosensor.migratedev.decisioncore.DecisionCore
import com.biosensor.migratedev.decisioncore.Transition

data class User(
    val id: String,
    val userName: String,
    val telephone: String,
    val avatarUrl: String? = null
)


data class AuthSession(
    val user: User,
    val token: String,
    val expiresAtMillis: Long? = null
)


sealed interface AuthState {
    data object Idle : AuthState
    data object RestoringSession : AuthState
    data object Loading : AuthState
    data class SavingSession(val session: AuthSession) : AuthState
    data class Registered(val message: String) : AuthState
    data class Authenticated(val session: AuthSession) : AuthState
    data class Error(val message: String) : AuthState
}

sealed interface AuthEvent {
    data object AppStarted : AuthEvent
    data class SubmitLogin(val account: String, val password: String) : AuthEvent
    data class SubmitRegister(
        val account: String,
        val password: String,
        val telephone: String,
        val avatarUrl: String? = null
    ) : AuthEvent

    data class RemoteAccepted(val session: AuthSession) : AuthEvent
    data class RemoteRejected(val message: String) : AuthEvent
    data object RemoteTimeout : AuthEvent
    data object SessionSaved : AuthEvent
    data class SessionSaveFailed(val message: String) : AuthEvent
    data class SessionFound(val session: AuthSession) : AuthEvent
    data object SessionMissing : AuthEvent
    data object SessionExpired : AuthEvent
    data class SessionReadFailed(val message: String) : AuthEvent
    data class SessionVerified(val session: AuthSession) : AuthEvent
    data class SessionRejected(val message: String) : AuthEvent
    data object SessionValidationTimeout : AuthEvent
    data object RegistrationAccepted : AuthEvent
    data class SessionClearFailed(val message: String) : AuthEvent
    data object SessionCleared : AuthEvent
    data object Logout : AuthEvent
    data object Reset : AuthEvent
}

sealed interface AuthEffect {
    data object ReadSession : AuthEffect
    data class ValidateSession(val session: AuthSession) : AuthEffect
    data class LoginRemote(val account: String, val password: String) : AuthEffect
    data class RegisterRemote(
        val account: String,
        val password: String,
        val telephone: String,
        val avatarUrl: String? = null
    ) : AuthEffect

    data class SaveSession(val session: AuthSession) : AuthEffect
    data object ClearSession : AuthEffect
}

// object 是全局单例
object AuthDecisionCore : DecisionCore<AuthState, AuthEvent, AuthEffect> {
    override fun reduce(
        currentState: AuthState,
        event: AuthEvent
    ): Transition<AuthState, AuthEffect> {
        return when (event) {
            AuthEvent.AppStarted -> when (currentState) {
                AuthState.Idle, is AuthState.Error -> Transition(
                    newState = AuthState.RestoringSession,
                    effects = listOf(AuthEffect.ReadSession)
                )

                else -> Transition(newState = currentState)
            }

            is AuthEvent.SessionFound -> when (currentState) {
                AuthState.RestoringSession -> Transition(
                    newState = AuthState.Loading,
                    effects = listOf(AuthEffect.ValidateSession(event.session))
                )

                else -> Transition(newState = currentState)
            }

            AuthEvent.SessionMissing -> when (currentState) {
                AuthState.RestoringSession -> Transition(AuthState.Idle)
                else -> Transition(newState = currentState)
            }

            AuthEvent.SessionExpired -> when (currentState) {
                AuthState.RestoringSession -> Transition(
                    newState = AuthState.Idle,
                    effects = listOf(AuthEffect.ClearSession)
                )

                else -> Transition(newState = currentState)
            }

            is AuthEvent.SessionReadFailed -> when (currentState) {
                AuthState.RestoringSession -> Transition(AuthState.Error(event.message))
                else -> Transition(newState = currentState)
            }

            is AuthEvent.SessionVerified -> when (currentState) {
                AuthState.Loading -> Transition(AuthState.Authenticated(event.session))
                else -> Transition(newState = currentState)
            }

            is AuthEvent.SessionRejected -> when (currentState) {
                AuthState.Loading -> Transition(
                    newState = AuthState.Idle,
                    effects = listOf(AuthEffect.ClearSession)
                )

                else -> Transition(newState = currentState)
            }

            AuthEvent.SessionValidationTimeout -> when (currentState) {
                AuthState.Loading -> Transition(AuthState.Error("会话验证超时，请重试"))
                else -> Transition(newState = currentState)
            }

            AuthEvent.RegistrationAccepted -> when (currentState) {
                AuthState.Loading -> Transition(AuthState.Registered("注册成功，请使用新账号登录"))
                else -> Transition(newState = currentState)
            }

            is AuthEvent.SubmitLogin -> when (currentState) {
                AuthState.Idle, is AuthState.Registered, is AuthState.Error -> Transition(
                    newState = AuthState.Loading,
                    effects = listOf(AuthEffect.LoginRemote(event.account, event.password))
                )
                // 非 Idle/Error 状态下忽略 SubmitLogin，保持现状且无副作用
                else -> Transition(newState = currentState)
            }

            is AuthEvent.SubmitRegister -> when (currentState) {
                AuthState.Idle, is AuthState.Error -> Transition(
                    newState = AuthState.Loading,
                    effects = listOf(
                        AuthEffect.RegisterRemote(
                            event.account,
                            event.password,
                            event.telephone,
                            event.avatarUrl
                        )
                    )
                )

                else -> Transition(newState = currentState)
            }

            is AuthEvent.RemoteAccepted -> when (currentState) {
                AuthState.Loading -> Transition(
                    newState = AuthState.SavingSession(event.session),
                    effects = listOf(AuthEffect.SaveSession(event.session))
                )

                else -> Transition(newState = currentState)
            }

            is AuthEvent.RemoteRejected -> when (currentState) {
                AuthState.Loading -> Transition(newState = AuthState.Error(event.message))
                else -> Transition(newState = currentState)
            }

            AuthEvent.RemoteTimeout -> when (currentState) {
                AuthState.Loading -> Transition(newState = AuthState.Error("远端请求超时"))
                else -> Transition(newState = currentState)
            }

            AuthEvent.SessionSaved -> when (currentState) {
                is AuthState.SavingSession -> Transition(
                    newState = AuthState.Authenticated(currentState.session)
                )

                else -> Transition(newState = currentState)
            }

            is AuthEvent.SessionSaveFailed -> when (currentState) {
                is AuthState.SavingSession -> Transition(
                    newState = AuthState.Error(event.message)
                )

                else -> Transition(newState = currentState)
            }

            is AuthEvent.SessionClearFailed -> Transition(AuthState.Error(event.message))

            AuthEvent.Logout -> Transition(
                newState = AuthState.Idle,
                effects = listOf(AuthEffect.ClearSession)
            )

            AuthEvent.SessionCleared -> Transition(newState = AuthState.Idle)
            AuthEvent.Reset -> when (currentState) {
                is AuthState.Error, is AuthState.Registered -> Transition(newState = AuthState.Idle)
                else -> Transition(newState = currentState)
            }
        }
    }
}
