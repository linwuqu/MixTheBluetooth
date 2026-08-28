package com.biosensor.migratedev.port.auth

import com.biosensor.migratedev.decisioncore.auth.AuthEffect
import com.biosensor.migratedev.decisioncore.auth.AuthEvent
import kotlinx.coroutines.flow.Flow

data class LoginRequest(val phone: String, val password: String)

data class RegisterRequest(
    val username: String, val password: String, val phone: String, val avatarUrl: String?
)

data class AccountDto(
    val id: Long = 0,
    val username: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null,
    val role: String? = null
)

data class ServerResponse<T>(
    val code: Int = -1, val success: Boolean = false, val msg: String? = null, val data: T? = null
) {
    fun isOk(): Boolean = success || code == 0 || code == 200
}

interface AuthPort {
    fun execute(effect: AuthEffect): Flow<AuthEvent>
}
