package com.biosensor.migratedev.port.auth

import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.port.CommandPort

sealed interface AuthCommand {
    data class Login(val account: String, val password: String) : AuthCommand
    data class Register(
        val account: String,
        val password: String,
        val telephone: String,
        val avatarUrl: String? = null
    ) : AuthCommand

    data class SaveSession(val session: AuthSession) : AuthCommand
    data object ClearSession : AuthCommand
}

sealed interface AuthResult {
    data class RemoteAccepted(val session: AuthSession) : AuthResult
    data class RemoteRejected(val message: String) : AuthResult
    data object RemoteTimeout : AuthResult
    data object SessionSaved : AuthResult
    data class SessionSaveFailed(val message: String) : AuthResult
    data object SessionCleared : AuthResult
}

interface AuthPort : CommandPort<AuthCommand, AuthResult>
