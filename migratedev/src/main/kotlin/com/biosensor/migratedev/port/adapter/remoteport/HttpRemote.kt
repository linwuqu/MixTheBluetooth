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

sealed interface ApiError {
    data object TimeoutError : ApiError

    // 网络分割等都算不可达异常
    data class UnreachableError(val msg: String) : ApiError

    // 到达服务器但是非200
    data class HttpError(val code: Int, val msg: String) : ApiError
    data class UnKnowError(val msg: String) : ApiError
}

sealed interface HttpOutcome<out T> {
    // 防止混淆业务成功和通信成功
    data class Completed<T>(val data: T) : HttpOutcome<T>
    data class Failure(val error: ApiError) : HttpOutcome<Nothing>
}

interface HttpRemote {
    // 封装 client 按照 apiClass 动态代理
    fun <T> api(apiClass: Class<T>): T

    suspend fun <T> invoke(block: suspend () -> T): HttpOutcome<T>
}
