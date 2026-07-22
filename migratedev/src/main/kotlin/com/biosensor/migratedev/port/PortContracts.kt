package com.biosensor.migratedev.port

import kotlinx.coroutines.flow.Flow

/**
 * 执行外部命令，并以流的形式返回一个或多个结果。
 *
 * `execute(command) -> Flow<result>`
 */
interface CommandPort<Command, Result> {
    fun execute(command: Command): Flow<Result>
}
