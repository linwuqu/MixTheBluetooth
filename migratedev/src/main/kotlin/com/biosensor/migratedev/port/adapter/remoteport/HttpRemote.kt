package com.biosensor.migratedev.port.adapter.remoteport

/**
 * 传输结果:统一超时与错误分类。
 * 只描述传输层发生了什么,业务语义(哪些算拒绝、哪些算验证失败)由业务适配器自己映射。
 *
 * 响应报文构成
 * HTTP/1.1 200 OK                  ← 状态行（状态码 + 原因短语）
 * Content-Type: application/json   ← 响应头
 * Content-Length: 42               ← 响应头
 *                                  ← 空行（头与体分隔）
 * {"code":0, "success":true}       ← 响应体（JSON 字符串）
 */
sealed interface HttpOutcome<out T> {
    // 200 OK
    data class Success<T>(val data: T) : HttpOutcome<T>
    // 超时捕获
    data object Timeout : HttpOutcome<Nothing>
    // 网络分割 网络层错误
    data class Network(val message: String) : HttpOutcome<Nothing>
    // 非 200
    data class Http(val code: Int, val message: String) : HttpOutcome<Nothing>
}

/**
 * 传输能力:只负责发起调用、统一超时与错误分类。
 * 端点由业务适配器通过 [api] 自行创建并表达业务协议。
 */
interface HttpRemote {
    // 包裹住所有的业务端点语义 同时不需要上层关注 retrofit 实例
    fun <T> api(apiClass: Class<T>): T

    // execute suspend 函数
    suspend fun <T> invoke(block: suspend () -> T): HttpOutcome<T>
}
