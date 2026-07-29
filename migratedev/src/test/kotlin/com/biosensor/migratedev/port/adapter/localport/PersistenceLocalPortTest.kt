package com.biosensor.migratedev.port.adapter.localport

import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.port.auth.AuthCommand
import com.biosensor.migratedev.port.auth.AuthResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistenceLocalPortTest {

    private val now = 1_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
    private val user = User("1", "alice", "13800000000")

    @Test
    fun existingSessionIsReturned() = runTest {
        val session = AuthSession(user, "token", now + 1)
        val port = PersistenceLocalPort(FakeSessionStore(SessionRead.Found(session)), clock)

        val result = port.execute(AuthCommand.Local.ReadSession).first()

        assertEquals(AuthResult.Local.SessionFound(session), result)
    }

    @Test
    fun expiredSessionIsReportedWithoutClearingStorage() = runTest {
        val store = FakeSessionStore(SessionRead.Found(AuthSession(user, "token", now)))
        val port = PersistenceLocalPort(store, clock)

        val result = port.execute(AuthCommand.Local.ReadSession).first()

        assertEquals(AuthResult.Local.SessionExpired, result)
        assertEquals(0, store.clearCalls)
    }

    @Test
    fun corruptedSessionIsClearedAndReported() = runTest {
        val store = FakeSessionStore(SessionRead.Corrupted)
        val port = PersistenceLocalPort(store, clock)

        val result = port.execute(AuthCommand.Local.ReadSession).first()

        assertEquals(AuthResult.Local.SessionReadFailed("本地会话无法解密"), result)
        assertEquals(1, store.clearCalls)
    }

    @Test
    fun saveAndClearReturnCheckedResults() = runTest {
        val session = AuthSession(user, "token")
        val store = FakeSessionStore(SessionRead.Missing)
        val port = PersistenceLocalPort(store, clock)

        assertEquals(
            AuthResult.Local.SessionSaved,
            port.execute(AuthCommand.Local.SaveSession(session)).first()
        )
        assertEquals(
            AuthResult.Local.SessionCleared,
            port.execute(AuthCommand.Local.ClearSession).first()
        )
        assertTrue(store.saved === session)
    }

    private class FakeSessionStore(
        private val readResult: SessionRead,
        private val saveResult: Boolean = true,
        private val clearResult: Boolean = true
    ) : SessionStore {
        var saved: AuthSession? = null
        var clearCalls = 0

        override suspend fun read(): SessionRead = readResult

        override suspend fun save(session: AuthSession): Boolean {
            saved = session
            return saveResult
        }

        override suspend fun clear(): Boolean {
            clearCalls++
            return clearResult
        }
    }
}
