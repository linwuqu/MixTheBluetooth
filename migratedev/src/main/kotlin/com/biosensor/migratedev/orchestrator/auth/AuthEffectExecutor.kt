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
            is AuthEffect.LoginRemote -> AuthCommand.Login(account, password)
            is AuthEffect.RegisterRemote -> AuthCommand.Register(
                account,
                password,
                telephone,
                avatarUrl
            )

            is AuthEffect.SaveSession -> AuthCommand.SaveSession(session)
            AuthEffect.ClearSession -> AuthCommand.ClearSession
        }
    }

    private fun AuthResult.toEvent(): AuthEvent {
        return when (this) {
            is AuthResult.RemoteAccepted -> AuthEvent.RemoteAccepted(session)
            is AuthResult.RemoteRejected -> AuthEvent.RemoteRejected(message)
            AuthResult.RemoteTimeout -> AuthEvent.RemoteTimeout
            AuthResult.SessionSaved -> AuthEvent.SessionSaved
            is AuthResult.SessionSaveFailed -> AuthEvent.SessionSaveFailed(message)
            AuthResult.SessionCleared -> AuthEvent.SessionCleared
        }
    }
}
