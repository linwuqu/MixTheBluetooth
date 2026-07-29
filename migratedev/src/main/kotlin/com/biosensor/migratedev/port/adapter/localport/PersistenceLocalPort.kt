package com.biosensor.migratedev.port.adapter.localport

import com.biosensor.migratedev.port.auth.AuthCommand
import com.biosensor.migratedev.port.auth.AuthResult
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class PersistenceLocalPort(
    private val sessionStore: SessionStore, private val clock: Clock
) {
    fun execute(command: AuthCommand.Local): Flow<AuthResult> = flow {
        emit(executeLocal(command))
    }.flowOn(Dispatchers.IO)

    private suspend fun executeLocal(
        command: AuthCommand.Local
    ): AuthResult.Local {
        return when (command) {
            AuthCommand.Local.ReadSession -> when (val read = sessionStore.read()) {
                SessionRead.Missing -> AuthResult.Local.SessionMissing
                SessionRead.Corrupted -> {
                    sessionStore.clear()
                    AuthResult.Local.SessionReadFailed("本地会话无法解密")
                }

                is SessionRead.Found -> {
                    val expiresAt = read.session.expiresAtMillis
                    if (expiresAt != null && clock.millis() >= expiresAt) {
                        AuthResult.Local.SessionExpired
                    } else {
                        AuthResult.Local.SessionFound(read.session)
                    }
                }
            }

            is AuthCommand.Local.SaveSession -> {
                if (sessionStore.save(command.session)) {
                    AuthResult.Local.SessionSaved
                } else {
                    AuthResult.Local.SessionSaveFailed("本地会话写入失败")
                }
            }

            AuthCommand.Local.ClearSession -> {
                if (sessionStore.clear()) {
                    AuthResult.Local.SessionCleared
                } else {
                    AuthResult.Local.SessionClearFailed("本地会话清理失败")
                }
            }
        }
    }
}
