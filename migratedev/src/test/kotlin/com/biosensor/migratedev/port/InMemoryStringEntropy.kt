package com.biosensor.migratedev.port

import com.biosensor.migratedev.port.adapter.localport.EntropyReadResult
import com.biosensor.migratedev.port.adapter.localport.EntropyRemoveResult
import com.biosensor.migratedev.port.adapter.localport.EntropyWriteResult
import com.biosensor.migratedev.port.adapter.localport.StringEntropy

/** 测试用内存 KV,替代 Tink 加密存储。 */
internal class InMemoryStringEntropy : StringEntropy {
    val values = mutableMapOf<String, String>()

    override suspend fun read(key: String): EntropyReadResult =
        values[key]?.let(EntropyReadResult::Found) ?: EntropyReadResult.Missing

    override suspend fun write(key: String, value: String): EntropyWriteResult {
        values[key] = value
        return EntropyWriteResult.Written
    }

    override suspend fun remove(key: String): EntropyRemoveResult =
        if (values.remove(key) == null) EntropyRemoveResult.Missing else EntropyRemoveResult.Removed

    override suspend fun contains(key: String): Boolean = values.containsKey(key)
}
