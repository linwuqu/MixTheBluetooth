package com.biosensor.migratedev.port.auth

import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.port.CommandPort

sealed interface AuthCommand {
    sealed interface Local : AuthCommand {
        data object ReadSession : Local
        data class SaveSession(val session: AuthSession) : Local
        data object ClearSession : Local
    }

    sealed interface Remote : AuthCommand {
        data class Login(val phone: String, val password: String) : Remote
        data class Register(
            val phone: String,
            val password: String,
            val nickname: String,
            val avatarUrl: String? = null
        ) : Remote
        data class ValidateSession(val session: AuthSession) : Remote
    }
}

sealed interface AuthResult {
    sealed interface Local : AuthResult {
        data class SessionFound(val session: AuthSession) : Local
        data object SessionMissing : Local
        data object SessionExpired : Local
        data class SessionReadFailed(val message: String) : Local
        data object SessionSaved : Local
        data class SessionSaveFailed(val message: String) : Local
        data class SessionClearFailed(val message: String) : Local
        data object SessionCleared : Local
    }

    sealed interface Remote : AuthResult {
        data class SessionVerified(val session: AuthSession) : Remote
        data class SessionRejected(val message: String) : Remote
        data object SessionValidationTimeout : Remote
        data class Accepted(val session: AuthSession) : Remote
        data class Rejected(val message: String) : Remote
        data object Timeout : Remote
        data object RegistrationAccepted : Remote
    }
}

interface AuthPort : CommandPort<AuthCommand, AuthResult>
