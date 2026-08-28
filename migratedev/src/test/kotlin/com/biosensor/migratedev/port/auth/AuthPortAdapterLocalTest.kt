package com.biosensor.migratedev.port.auth

import com.biosensor.migratedev.decisioncore.auth.AuthEffect
import com.biosensor.migratedev.decisioncore.auth.AuthEvent
import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.port.InMemoryKvStore
import com.biosensor.migratedev.port.adapter.localport.KvReadResult
import com.biosensor.migratedev.port.adapter.localport.KvRemoveResult
import com.biosensor.migratedev.port.adapter.localport.KvStore
import com.biosensor.migratedev.port.adapter.localport.KvWriteResult
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

    private fun adapter(kv: KvStore) =
        AuthPortAdapter(
            kv = kv,
            http = NoopHttpRemote,
            clock = clock,
            debugOfflineMode = false   // 测试不启用调试离线会话
        )

    private fun seededKv(raw: String): InMemoryKvStore =
        InMemoryKvStore().apply { values[AuthPortAdapter.SESSION_KEY] = raw }

    @Test
    fun existingSessionIsReturned() = runTest {
        val session = AuthSession(user, "token", now + 1)

        val result = adapter(seededKv(gson.toJson(session)))
            .execute(AuthEffect.ReadSession).first()

        assertEquals(AuthEvent.SessionFound(session), result)
    }

    @Test
    fun expiredSessionIsReportedWithoutClearingStorage() = runTest {
        val kv = seededKv(gson.toJson(AuthSession(user, "token", now)))

        val result = adapter(kv).execute(AuthEffect.ReadSession).first()

        assertEquals(AuthEvent.SessionExpired, result)
        assertEquals(
            KvReadResult.Value(gson.toJson(AuthSession(user, "token", now))),
            kv.read(AuthPortAdapter.SESSION_KEY)
        )
    }

    @Test
    fun corruptedSessionIsClearedAndReported() = runTest {
        val kv = seededKv("not-json")

        val result = adapter(kv).execute(AuthEffect.ReadSession).first()

        assertEquals(AuthEvent.SessionReadFailed("本地会话无法解密"), result)
        assertEquals(KvReadResult.None, kv.read(AuthPortAdapter.SESSION_KEY))
    }

    @Test
    fun blankTokenJsonIsTreatedAsCorrupted() = runTest {
        val kv = seededKv(gson.toJson(AuthSession(user, "", null)))

        val result = adapter(kv).execute(AuthEffect.ReadSession).first()

        assertEquals(AuthEvent.SessionReadFailed("本地会话无法解密"), result)
    }

    @Test
    fun sessionRoundTripsThroughKv() = runTest {
        val port = adapter(InMemoryKvStore())
        val session = AuthSession(User("7", "tester", "13800000000"), "token", 1234L)

        assertEquals(
            AuthEvent.SessionMissing,
            port.execute(AuthEffect.ReadSession).first()
        )
        assertEquals(
            AuthEvent.SessionSaved,
            port.execute(AuthEffect.SaveSession(session)).first()
        )
        assertEquals(
            AuthEvent.SessionFound(session),
            port.execute(AuthEffect.ReadSession).first()
        )
        assertEquals(
            AuthEvent.SessionCleared,
            port.execute(AuthEffect.ClearSession).first()
        )
        assertEquals(
            AuthEvent.SessionMissing,
            port.execute(AuthEffect.ReadSession).first()
        )
    }

    @Test
    fun blankTokenSaveIsRejected() = runTest {
        val port = adapter(InMemoryKvStore())

        val result = port.execute(AuthEffect.SaveSession(AuthSession(user, "", null))).first()

        assertEquals(AuthEvent.SessionSaveFailed("本地会话写入失败"), result)
    }

    @Test
    fun clearFailureIsReported() = runTest {
        val result = adapter(FailingRemoveKv())
            .execute(AuthEffect.ClearSession).first()

        assertEquals(AuthEvent.SessionClearFailed("本地会话清理失败"), result)
    }

    private class FailingRemoveKv : KvStore {
        override suspend fun read(key: String): KvReadResult = KvReadResult.None
        override suspend fun write(key: String, value: String): KvWriteResult = KvWriteResult.Done
        override suspend fun remove(key: String): KvRemoveResult = KvRemoveResult.Failed("模拟失败")
        override suspend fun contains(key: String): Boolean = false
    }

    private object NoopHttpRemote : HttpRemote {
        override fun <T> api(apiClass: Class<T>): T = error("本地测试不会使用远端")
        override suspend fun <T> invoke(block: suspend () -> T): HttpOutcome<T> = error("本地测试不会使用远端")
    }
}
