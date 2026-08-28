package com.biosensor.migratedev.port.adapter.localport

import com.biosensor.migratedev.database.DeviceBindingQueries
import okio.Sink
import okio.Source

data class LocalPort(
    val kv: KvStore,
    val sql: SqlStore,
    val files: FileStore,
)

interface KvStore {
    suspend fun read(key: String): KvReadResult
    suspend fun write(key: String, value: String): KvWriteResult
    suspend fun remove(key: String): KvRemoveResult
    suspend fun contains(key: String): Boolean
}

sealed interface KvReadResult {
    data class Value(val value: String) : KvReadResult
    data object None : KvReadResult
    data class Failed(val message: String) : KvReadResult
}

sealed interface KvWriteResult {
    data object Done : KvWriteResult
    data class Failed(val msg: String) : KvWriteResult
}

sealed interface KvRemoveResult {
    data object Done : KvRemoveResult
    data object None : KvRemoveResult
    data class Failed(val msg: String) : KvRemoveResult
}

interface SqlStore {
    val deviceBinding: DeviceBindingQueries
}

interface FileStore {
    fun read(space: FileSpace, relativePath: String): Source
    fun write(space: FileSpace, relativePath: String, append: Boolean = false): Sink
    fun list(space: FileSpace, relativePath: String = ""): List<FileEntry>
    fun delete(space: FileSpace, relativePath: String): FileDeleteResult
}

enum class FileSpace {
    LOGS, RECEIVED, OUTGOING, CACHE,
}

data class FileEntry(
    val relativePath: String, val size: Long?, val lastModifiedAtMillis: Long?
)

sealed interface FileDeleteResult {
    data object Done : FileDeleteResult
    data object None : FileDeleteResult
    data class Failed(val msg: String) : FileDeleteResult
}

