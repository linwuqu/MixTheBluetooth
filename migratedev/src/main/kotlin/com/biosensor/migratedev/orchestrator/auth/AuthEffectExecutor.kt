package com.biosensor.migratedev.orchestrator.auth

import com.biosensor.migratedev.decisioncore.auth.AuthEffect
import com.biosensor.migratedev.decisioncore.auth.AuthEvent
import com.biosensor.migratedev.port.auth.AuthCommand
import com.biosensor.migratedev.port.auth.AuthPort
import com.biosensor.migratedev.port.auth.AuthResult
import com.biosensor.migratedev.orchestrator.EffectExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthEffectExecutor(
    private val port: AuthPort
) : EffectExecutor<AuthEffect, AuthEvent> {

    override fun execute(effect: AuthEffect): Flow<AuthEvent> {
        // 使用扩展函数 effect.toCommand 和 result.toEvent 将 execute 的整体过程进行概括描述
        // effect → command 执行 → 流式结果 map 为事件
        return port.execute(effect.toCommand()).map { it.toEvent() }
    }

    private fun AuthEffect.toCommand(): AuthCommand {
        return when (this) {
            AuthEffect.ReadSession -> AuthCommand.Local.ReadSession
            is AuthEffect.ValidateSession -> AuthCommand.Remote.ValidateSession(session)
            is AuthEffect.LoginRemote -> AuthCommand.Remote.Login(account, password)
            is AuthEffect.RegisterRemote -> AuthCommand.Remote.Register(
                account,
                password,
                telephone,
                avatarUrl
            )

            is AuthEffect.SaveSession -> AuthCommand.Local.SaveSession(session)
            AuthEffect.ClearSession -> AuthCommand.Local.ClearSession
        }
    }

    private fun AuthResult.toEvent(): AuthEvent {
        return when (this) {
            is AuthResult.Local.SessionFound -> AuthEvent.SessionFound(session)
            AuthResult.Local.SessionMissing -> AuthEvent.SessionMissing
            AuthResult.Local.SessionExpired -> AuthEvent.SessionExpired
            is AuthResult.Local.SessionReadFailed -> AuthEvent.SessionReadFailed(message)
            AuthResult.Local.SessionSaved -> AuthEvent.SessionSaved
            is AuthResult.Local.SessionSaveFailed -> AuthEvent.SessionSaveFailed(message)
            is AuthResult.Local.SessionClearFailed -> AuthEvent.SessionClearFailed(message)
            AuthResult.Local.SessionCleared -> AuthEvent.SessionCleared
            is AuthResult.Remote.SessionVerified -> AuthEvent.SessionVerified(session)
            is AuthResult.Remote.SessionRejected -> AuthEvent.SessionRejected(message)
            AuthResult.Remote.SessionValidationTimeout -> AuthEvent.SessionValidationTimeout
            is AuthResult.Remote.Accepted -> AuthEvent.RemoteAccepted(session)
            is AuthResult.Remote.Rejected -> AuthEvent.RemoteRejected(message)
            AuthResult.Remote.Timeout -> AuthEvent.RemoteTimeout
            AuthResult.Remote.RegistrationAccepted -> AuthEvent.RegistrationAccepted
        }
    }
}
