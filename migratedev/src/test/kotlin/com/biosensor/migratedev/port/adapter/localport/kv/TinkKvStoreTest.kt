package com.biosensor.migratedev.port.adapter.localport.kv

import com.biosensor.migratedev.port.adapter.localport.KvReadResult
import com.biosensor.migratedev.port.adapter.localport.KvRemoveResult
import com.biosensor.migratedev.port.adapter.localport.KvWriteResult
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TinkKvStoreTest {
    private lateinit var store: TinkKvStore
    private lateinit var storage: InMemoryKvStorage

    @Before
    fun setUp() {
        AeadConfig.register()
        val aead = KeysetHandle.generateNew(
            KeyTemplates.get("AES256_GCM")
        ).getPrimitive(
            RegistryConfiguration.get(),
            Aead::class.java
        )
        storage = InMemoryKvStorage()
        store = TinkKvStore(
            storage = storage,
            cipher = TinkAeadCipher(aead)
        )
    }

    @Test
    fun `write read contains and remove form a complete CRUD cycle`() = runTest {
        assertEquals(KvReadResult.None, store.read("session"))
        assertFalse(store.contains("session"))

        assertEquals(KvWriteResult.Done, store.write("session", "token-1"))
        assertEquals(KvReadResult.Value("token-1"), store.read("session"))
        assertTrue(store.contains("session"))

        assertEquals(KvWriteResult.Done, store.write("session", "token-2"))
        assertEquals(KvReadResult.Value("token-2"), store.read("session"))

        assertEquals(KvRemoveResult.Done, store.remove("session"))
        assertEquals(KvReadResult.None, store.read("session"))
        assertEquals(KvRemoveResult.None, store.remove("session"))
    }

    @Test
    fun `ciphertext cannot be moved to another key`() = runTest {
        store.write("session", "token")
        val ciphertext = storage.values["entropy:session"]
        requireNotNull(ciphertext)
        storage.values["entropy:other"] = ciphertext

        assertTrue(store.read("other") is KvReadResult.Failed)
    }
}

private class InMemoryKvStorage : KvStorage {
    val values = mutableMapOf<String, String>()

    override suspend fun read(key: String): String? = values[key]

    override suspend fun write(key: String, value: String) {
        values[key] = value
    }

    override suspend fun remove(key: String): Boolean = values.remove(key) != null

    override suspend fun contains(key: String): Boolean =
        values.containsKey(key)
}
