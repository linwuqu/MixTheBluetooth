package com.biosensor.migratedev.promise

import kotlinx.coroutines.flow.Flow

interface CommonPromise {
    val logger: Logger
    val clock: Clock
    val codec: TextCodec
    val files: FileStore
    val keyValue: KeyValueStore
    val bluetooth: BluetoothPort
    val network: NetworkPort
    val qrScanner: QrScanner
}

interface Logger {
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String, cause: Throwable? = null)
    fun error(tag: String, message: String, cause: Throwable? = null)
}

interface Clock {
    fun nowMillis(): Long
    fun formatNow(pattern: String = "yyyy-MM-dd HH:mm:ss"): String
}

interface TextCodec {
    fun decode(bytes: ByteArray, encoding: String): String
    fun encode(text: String, encoding: String): ByteArray
}

interface FileStore {
    suspend fun writeText(name: String, content: String): StoredFile
    suspend fun readText(file: StoredFile): String
    suspend fun delete(file: StoredFile): Boolean
}

interface KeyValueStore {
    suspend fun getString(key: String): String?
    suspend fun putString(key: String, value: String?)
    suspend fun remove(key: String)
}

interface BluetoothPort {
    val events: Flow<BluetoothEvent>

    suspend fun connect(device: BluetoothDevice)
    suspend fun disconnect(deviceId: String)
    suspend fun send(packet: BluetoothPacket)
}

interface NetworkPort {
    // 端点定义放在 business/data；common network 只负责执行端点。
    suspend fun <T : Any> execute(endpoint: NetworkEndpoint<T>): T
}

interface QrScanner {
    suspend fun scan(): QrScanResult
}

data class StoredFile(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val mimeType: String? = null
)

data class BluetoothDevice(
    val id: String,
    val name: String? = null
)

data class BluetoothPacket(
    val deviceId: String,
    val bytes: ByteArray,
    val timestampMillis: Long
)

sealed interface BluetoothEvent {
    val deviceId: String
    val timestampMillis: Long

    data class BytesReceived(
        override val deviceId: String,
        override val timestampMillis: Long,
        val bytes: ByteArray
    ) : BluetoothEvent

    data class Connected(
        override val deviceId: String,
        override val timestampMillis: Long
    ) : BluetoothEvent

    data class Disconnected(
        override val deviceId: String,
        override val timestampMillis: Long
    ) : BluetoothEvent
}

fun interface NetworkEndpoint<T : Any> {
    suspend fun request(): T
}

data class QrScanResult(
    val rawText: String,
    val format: String? = null
)
