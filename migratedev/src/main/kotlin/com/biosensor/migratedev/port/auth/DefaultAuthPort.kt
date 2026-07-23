package com.biosensor.migratedev.port.auth

import com.biosensor.migratedev.port.adapter.localport.PersistenceLocalPort
import com.biosensor.migratedev.port.adapter.remoteport.RetrofitRemotePort
import kotlinx.coroutines.flow.Flow

class DefaultAuthPort(
    private val persistence: PersistenceLocalPort,
    private val remote: RetrofitRemotePort.Auth
) : AuthPort {
    override fun execute(command: AuthCommand): Flow<AuthResult> {
        return when (command) {
            is AuthCommand.Local -> persistence.execute(command)
            is AuthCommand.Remote -> remote.execute(command)
        }
    }
}
