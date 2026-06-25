package com.biosensor.migratedev.promise

import kotlinx.coroutines.flow.Flow

interface AccountPromise {
    val session: Flow<AccountSession?>

    suspend fun login(input: LoginInput): AccountSession
    suspend fun register(input: RegisterInput): AccountSession
    suspend fun rename(newName: String): AccountUser
    suspend fun logout()
    suspend fun currentSession(): AccountSession?
}

data class LoginInput(
    val phone: String,
    val password: String
)

data class RegisterInput(
    val username: String,
    val phone: String,
    val password: String,
    val avatarUrl: String? = null
)

// token 给 data/driver 做鉴权，presentation 不应该直接消费它。
data class AccountSession(
    val user: AccountUser,
    val token: String,
    val expiredAtMillis: Long? = null
)

data class AccountUser(
    val id: Long,
    val username: String?,
    val phone: String?,
    val avatarUrl: String? = null,
    val role: String? = null
)
