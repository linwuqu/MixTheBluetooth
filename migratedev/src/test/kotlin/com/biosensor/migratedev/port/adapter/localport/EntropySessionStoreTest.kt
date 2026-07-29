package com.biosensor.migratedev.port.adapter.localport

import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.decisioncore.auth.User
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntropySessionStoreTest {
    @Test
    fun `session round trips through generic string entropy`() = runTest {
        val entropy = InMemoryStringEntropy()
        val store = EntropySessionStore(entropy)
        val session = AuthSession(
            user = User("7", "tester", "13800000000"),
            token = "token",
            expiresAtMillis = 1234L
        )

        assertEquals(SessionRead.Missing, store.read())
        assertTrue(store.save(session))
        assertEquals(SessionRead.Found(session), store.read())
        assertTrue(store.clear())
        assertEquals(SessionRead.Missing, store.read())
    }

    @Test
    fun `invalid session json is reported as corrupted`() = runTest {
        val entropy = InMemoryStringEntropy().apply {
            values[EntropySessionStore.SESSION_KEY] = "not-json"
        }
        val store = EntropySessionStore(entropy)

        assertEquals(SessionRead.Corrupted, store.read())
        assertFalse(store.save(AuthSession(
            user = User("7", "tester", "13800000000"),
            token = "",
            expiresAtMillis = null
        )))
    }
}

private class InMemoryStringEntropy : StringEntropy {
    val values = mutableMapOf<String, String>()

    override suspend fun read(key: String): EntropyReadResult =
        values[key]?.let(EntropyReadResult::Found)
            ?: EntropyReadResult.Missing

    override suspend fun write(
        key: String,
        value: String
    ): EntropyWriteResult {
        values[key] = value
        return EntropyWriteResult.Written
    }

    override suspend fun remove(key: String): EntropyRemoveResult =
        if (values.remove(key) == null) {
            EntropyRemoveResult.Missing
        } else {
            EntropyRemoveResult.Removed
        }

    override suspend fun contains(key: String): Boolean =
        values.containsKey(key)
}
