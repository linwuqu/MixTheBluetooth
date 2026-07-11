package com.biosensor.migratedev.business.account.data

import com.biosensor.migratedev.promise.AccountSession
import com.biosensor.migratedev.promise.AccountUser
import com.biosensor.migratedev.promise.LoginInput
import com.biosensor.migratedev.promise.NetworkEndpoint
import com.biosensor.migratedev.promise.RegisterInput

interface AccountRepository {
    suspend fun login(input: LoginInput): AccountSession
    suspend fun register(input: RegisterInput): AccountSession
    suspend fun rename(newName: String): AccountUser
    suspend fun logout()
    suspend fun currentSession(): AccountSession?
}

interface AccountRemoteDataSource {
    fun login(input: LoginInput): NetworkEndpoint<AccountSession>
    fun register(input: RegisterInput): NetworkEndpoint<AccountSession>
    fun rename(newName: String): NetworkEndpoint<AccountUser>
}

interface AccountSessionStore {
    suspend fun read(): AccountSession?
    suspend fun write(session: AccountSession?)
    suspend fun clear()
}
