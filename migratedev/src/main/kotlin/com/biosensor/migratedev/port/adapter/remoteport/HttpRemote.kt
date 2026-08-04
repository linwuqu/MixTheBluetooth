package com.biosensor.migratedev.port.adapter.remoteport

/**
 * 传输结果:统一超时与错误分类。
 * 只描述传输层发生了什么,业务语义(哪些算拒绝、哪些算验证失败)由业务适配器自己映射。
 */
sealed interface HttpOutcome<out T> {
    data class Success<T>(val data: T) : HttpOutcome<T>
    data object Timeout : HttpOutcome<Nothing>
    data class Network(val message: String) : HttpOutcome<Nothing>
    data class Http(val code: Int, val message: String) : HttpOutcome<Nothing>
}

/**
 * 传输能力:只负责发起调用、统一超时与错误分类。
 * 端点由业务适配器通过 [api] 自行创建并表达业务协议。
 */
interface HttpRemote {
    fun <T> api(apiClass: Class<T>): T

    suspend fun <T> invoke(block: suspend () -> T): HttpOutcome<T>
}
