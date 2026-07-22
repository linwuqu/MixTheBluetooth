package com.biosensor.migratedev.orchestrator

import kotlinx.coroutines.flow.Flow

/**
 * 执行状态机声明的副作用，并把执行结果转换成重新进入状态机的事件流。
 *
 * `execute(effect) -> Flow<event>`
 */
fun interface EffectExecutor<Effect, Event> {
    fun execute(effect: Effect): Flow<Event>
}
