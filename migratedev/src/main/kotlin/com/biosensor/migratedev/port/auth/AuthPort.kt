package com.biosensor.migratedev.port.auth

import com.biosensor.migratedev.decisioncore.auth.AuthEffect
import com.biosensor.migratedev.decisioncore.auth.AuthEvent
import kotlinx.coroutines.flow.Flow

/**
 * auth 业务端口:直接执行领域副作用([AuthEffect]),翻译成领域事件([AuthEvent])上报。
 * 不再有 Command/Result 中间词汇。
 */
interface AuthPort {
    fun execute(effect: AuthEffect): Flow<AuthEvent>
}
