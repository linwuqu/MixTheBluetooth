package com.biosensor.migratedev.port

import com.biosensor.migratedev.port.adapter.localport.KvReadResult
import com.biosensor.migratedev.port.adapter.localport.KvRemoveResult
import com.biosensor.migratedev.port.adapter.localport.KvStore
import com.biosensor.migratedev.port.adapter.localport.KvWriteResult

/** 测试用内存 KV,替代 Tink 加密存储。 */
internal class InMemoryKvStore : KvStore {
    val values = mutableMapOf<String, String>()

    override suspend fun read(key: String): KvReadResult =
        values[key]?.let { KvReadResult.Value(it) } ?: KvReadResult.None

    override suspend fun write(key: String, value: String): KvWriteResult {
        values[key] = value
        return KvWriteResult.Done
    }

    override suspend fun remove(key: String): KvRemoveResult =
        if (values.remove(key) == null) KvRemoveResult.None else KvRemoveResult.Done

    override suspend fun contains(key: String): Boolean = values.containsKey(key)
}
