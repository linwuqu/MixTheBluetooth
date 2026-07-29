package com.biosensor.migratedev.port.adapter.localport

import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.google.gson.Gson
import com.google.gson.JsonParseException

class EntropySessionStore(
    private val entropy: StringEntropy, private val gson: Gson = Gson()
) : SessionStore {
    override suspend fun read(): SessionRead {
        return when (val result = entropy.read(SESSION_KEY)) {
            EntropyReadResult.Missing -> SessionRead.Missing
            is EntropyReadResult.Failed -> SessionRead.Corrupted
            is EntropyReadResult.Found -> parse(result.value)
        }
    }

    override suspend fun save(session: AuthSession): Boolean {
        if (session.token.isBlank()) {
            return false
        }
        return try {
            entropy.write(
                SESSION_KEY, gson.toJson(session)
            ) == EntropyWriteResult.Written
        } catch (_: JsonParseException) {
            false
        }
    }

    override suspend fun clear(): Boolean {
        return when (entropy.remove(SESSION_KEY)) {
            EntropyRemoveResult.Removed, EntropyRemoveResult.Missing -> true

            is EntropyRemoveResult.Failed -> false
        }
    }

    private fun parse(json: String): SessionRead {
        return try {
            val session = gson.fromJson(json, AuthSession::class.java)
            if (session == null || session.token.isBlank()) {
                SessionRead.Corrupted
            } else {
                SessionRead.Found(session)
            }
        } catch (_: JsonParseException) {
            SessionRead.Corrupted
        } catch (_: IllegalArgumentException) {
            SessionRead.Corrupted
        }
    }

    companion object {
        const val SESSION_KEY = "auth.active_session"
    }
}
