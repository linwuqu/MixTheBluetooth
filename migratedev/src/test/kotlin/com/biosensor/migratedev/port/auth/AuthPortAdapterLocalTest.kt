package com.biosensor.migratedev.port.auth

import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.port.InMemoryStringEntropy
import com.biosensor.migratedev.port.adapter.localport.EntropyReadResult
import com.biosensor.migratedev.port.adapter.localport.EntropyRemoveResult
import com.biosensor.migratedev.port.adapter.localport.EntropyWriteResult
import com.biosensor.migratedev.port.adapter.localport.StringEntropy
import com.biosensor.migratedev.port.adapter.remoteport.HttpOutcome
import com.biosensor.migratedev.port.adapter.remoteport.HttpRemote
import com.google.gson.Gson
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthPortAdapterLocalTest {

    private val now = 1_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
    private val user = User("1", "alice", "13800000000")
    private val gson = Gson()

    private fun adapter(entropy: StringEntropy) =
        AuthPortAdapter(kv = entropy, http = NoopHttpRemote, clock = clock)

    private fun seededEntropy(raw: String): InMemoryStringEntropy =
        InMemoryStringEntropy().apply { values[AuthPortAdapter.SESSION_KEY] = raw }

    @Test
    fun existingSessionIsReturned() = runTest {
        val session = AuthSession(user, "token", now + 1)

        val result = adapter(seededEntropy(gson.toJson(session)))
            .execute(AuthCommand.Local.ReadSession).first()

        assertEquals(AuthResult.Local.SessionFound(session), result)
    }

    @Test
    fun expiredSessionIsReportedWithoutClearingStorage() = runTest {
        val entropy = seededEntropy(gson.toJson(AuthSession(user, "token", now)))

        val result = adapter(entropy).execute(AuthCommand.Local.ReadSession).first()

        assertEquals(AuthResult.Local.SessionExpired, result)
        assertEquals(EntropyReadResult.Found(gson.toJson(AuthSession(user, "token", now))), entropy.read(AuthPortAdapter.SESSION_KEY))
    }

    @Test
    fun corruptedSessionIsClearedAndReported() = runTest {
        val entropy = seededEntropy("not-json")

        val result = adapter(entropy).execute(AuthCommand.Local.ReadSession).first()

        assertEquals(AuthResult.Local.SessionReadFailed("本地会话无法解密"), result)
        assertEquals(EntropyReadResult.Missing, entropy.read(AuthPortAdapter.SESSION_KEY))
    }

    @Test
    fun blankTokenJsonIsTreatedAsCorrupted() = runTest {
        val entropy = seededEntropy(gson.toJson(AuthSession(user, "", null)))

        val result = adapter(entropy).execute(AuthCommand.Local.ReadSession).first()

        assertEquals(AuthResult.Local.SessionReadFailed("本地会话无法解密"), result)
    }

    @Test
    fun sessionRoundTripsThroughKv() = runTest {
        val port = adapter(InMemoryStringEntropy())
        val session = AuthSession(User("7", "tester", "13800000000"), "token", 1234L)

        assertEquals(
            AuthResult.Local.SessionMissing,
            port.execute(AuthCommand.Local.ReadSession).first()
        )
        assertEquals(
            AuthResult.Local.SessionSaved,
            port.execute(AuthCommand.Local.SaveSession(session)).first()
        )
        assertEquals(
            AuthResult.Local.SessionFound(session),
            port.execute(AuthCommand.Local.ReadSession).first()
        )
        assertEquals(
            AuthResult.Local.SessionCleared,
            port.execute(AuthCommand.Local.ClearSession).first()
        )
        assertEquals(
            AuthResult.Local.SessionMissing,
            port.execute(AuthCommand.Local.ReadSession).first()
        )
    }

    @Test
    fun blankTokenSaveIsRejected() = runTest {
        val port = adapter(InMemoryStringEntropy())

        val result = port.execute(AuthCommand.Local.SaveSession(AuthSession(user, "", null))).first()

        assertEquals(AuthResult.Local.SessionSaveFailed("本地会话写入失败"), result)
    }

    @Test
    fun clearFailureIsReported() = runTest {
        val result = adapter(FailingRemoveEntropy())
            .execute(AuthCommand.Local.ClearSession).first()

        assertEquals(AuthResult.Local.SessionClearFailed("本地会话清理失败"), result)
    }

    private class FailingRemoveEntropy : StringEntropy {
        override suspend fun read(key: String): EntropyReadResult = EntropyReadResult.Missing
        override suspend fun write(key: String, value: String): EntropyWriteResult = EntropyWriteResult.Written
        override suspend fun remove(key: String): EntropyRemoveResult = EntropyRemoveResult.Failed("模拟失败")
        override suspend fun contains(key: String): Boolean = false
    }

    private object NoopHttpRemote : HttpRemote {
        override fun <T> api(apiClass: Class<T>): T = error("本地测试不会使用远端")
        override suspend fun <T> invoke(block: suspend () -> T): HttpOutcome<T> = error("本地测试不会使用远端")
    }
}
