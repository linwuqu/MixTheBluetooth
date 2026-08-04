package com.biosensor.migratedev.port

import kotlinx.coroutines.flow.Flow

/**
 * 执行领域副作用,并把执行结果翻译成领域事件流返回。
 *
 * `execute(effect) -> Flow<event>`
 *
 * 下行指令直达:effect 就是 port 的命令,不再有 Command 传话层;
 * 上行分层上报:底层结果(能力层词汇)在实现内部翻译成领域事件,止步于 port 边界。
 */
fun interface CommandPort<Effect, Event> {
    fun execute(effect: Effect): Flow<Event>
}
