package com.biosensor.migratedev.port.adapter.localport

import com.biosensor.migratedev.database.LocalDatabase
import okio.Sink
import okio.Source

sealed interface EntropyReadResult {
    data class Found(val value: String) : EntropyReadResult
    data object Missing : EntropyReadResult
    data class Failed(val message: String) : EntropyReadResult
}

sealed interface EntropyWriteResult {
    data object Written : EntropyWriteResult
    data class Failed(val message: String) : EntropyWriteResult
}

sealed interface EntropyRemoveResult {
    data object Removed : EntropyRemoveResult
    data object Missing : EntropyRemoveResult
    data class Failed(val message: String) : EntropyRemoveResult
}

interface StringEntropy {
    suspend fun read(key: String): EntropyReadResult

    suspend fun write(key: String, value: String): EntropyWriteResult

    suspend fun remove(key: String): EntropyRemoveResult

    suspend fun contains(key: String): Boolean
}

interface LocalFileClient {
    // 流式输入抽象
    fun source(space: FileSpace, relativePath: String): Source

    // 流式输出抽象
    fun sink(space: FileSpace, relativePath: String, append: Boolean = false): Sink

    // 枚举目录内容
    fun list(space: FileSpace, relativePath: String = ""): List<FileEntry>

    // 删除单个文件
    fun delete(space: FileSpace, relativePath: String): FileDeleteResult

    // 按策略批量清理文件
    fun prune(space: FileSpace, policy: RetentionPolicy): FilePruneResult
}

enum class FileSpace {
    LOGS, RECEIVED, OUTGOING, CACHE
}

data class FileEntry(
    val relativePath: String, val size: Long?, val lastModifiedAtMillis: Long?
)

sealed interface FileDeleteResult {
    data object Deleted : FileDeleteResult
    data object Missing : FileDeleteResult
    data class Failed(val message: String) : FileDeleteResult
}

data class RetentionPolicy(
    val maxFiles: Int, val maxAgeMillis: Long, val nowMillis: Long
)

sealed interface FilePruneResult {
    data class Pruned(val deletedCount: Int) : FilePruneResult
    data class Failed(val message: String) : FilePruneResult
}
