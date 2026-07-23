package com.biosensor.migratedev.port.adapter.localport

import com.biosensor.migratedev.decisioncore.auth.AuthSession

sealed interface SessionRead {
    data class Found(val session: AuthSession) : SessionRead
    data object Missing : SessionRead
    data object Corrupted : SessionRead
}

interface SessionStore {
    companion object {
        const val LOCAL_TOKEN_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }

    fun read(): SessionRead

    fun save(session: AuthSession): Boolean

    fun clear(): Boolean
}
