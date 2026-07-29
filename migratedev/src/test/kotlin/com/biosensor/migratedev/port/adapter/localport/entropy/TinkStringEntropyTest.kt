package com.biosensor.migratedev.port.adapter.localport.entropy

import com.biosensor.migratedev.port.adapter.localport.EntropyReadResult
import com.biosensor.migratedev.port.adapter.localport.EntropyRemoveResult
import com.biosensor.migratedev.port.adapter.localport.EntropyWriteResult
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

class TinkStringEntropyTest {
    private lateinit var entropy: TinkStringEntropy
    private lateinit var aead: Aead
    private lateinit var preferences: InMemoryEntropyPreferences

    @Before
    fun setUp() {
        AeadConfig.register()
        aead = KeysetHandle.generateNew(
            KeyTemplates.get("AES256_GCM")
        ).getPrimitive(
            RegistryConfiguration.get(),
            Aead::class.java
        )
        preferences = InMemoryEntropyPreferences()
        entropy = TinkStringEntropy(
            preferences = preferences,
            aead = aead
        )
    }

    @Test
    fun `write read contains and remove form a complete CRUD cycle`() = runTest {
        assertEquals(EntropyReadResult.Missing, entropy.read("session"))
        assertFalse(entropy.contains("session"))

        assertEquals(
            EntropyWriteResult.Written,
            entropy.write("session", "token-1")
        )
        assertEquals(
            EntropyReadResult.Found("token-1"),
            entropy.read("session")
        )
        assertTrue(entropy.contains("session"))

        assertEquals(
            EntropyWriteResult.Written,
            entropy.write("session", "token-2")
        )
        assertEquals(
            EntropyReadResult.Found("token-2"),
            entropy.read("session")
        )

        assertEquals(
            EntropyRemoveResult.Removed,
            entropy.remove("session")
        )
        assertEquals(EntropyReadResult.Missing, entropy.read("session"))
        assertEquals(
            EntropyRemoveResult.Missing,
            entropy.remove("session")
        )
    }

    @Test
    fun `ciphertext cannot be moved to another key`() = runTest {
        entropy.write("session", "token")
        val ciphertext = preferences.values["entropy:session"]
        requireNotNull(ciphertext)
        preferences.values["entropy:other"] = ciphertext

        assertTrue(entropy.read("other") is EntropyReadResult.Failed)
    }
}

private class InMemoryEntropyPreferences : EntropyPreferences {
    val values = mutableMapOf<String, String>()

    override suspend fun read(key: String): String? = values[key]

    override suspend fun write(key: String, value: String) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }

    override suspend fun contains(key: String): Boolean =
        values.containsKey(key)
}
