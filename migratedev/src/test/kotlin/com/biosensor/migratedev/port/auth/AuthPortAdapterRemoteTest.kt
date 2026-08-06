package com.biosensor.migratedev.port.auth

import com.biosensor.migratedev.decisioncore.auth.AuthEffect
import com.biosensor.migratedev.decisioncore.auth.AuthEvent
import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.port.InMemoryStringEntropy
import com.biosensor.migratedev.port.adapter.remoteport.HttpOutcome
import com.biosensor.migratedev.port.adapter.remoteport.HttpRemote
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthPortAdapterRemoteTest {

    private val now = 10_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
    private val ttl = 7L * 24 * 60 * 60 * 1000

    private fun adapter(http: FakeHttpRemote) =
        AuthPortAdapter(
            kv = InMemoryStringEntropy(),
            http = http,
            clock = clock,
            debugOfflineMode = false   // 测试不启用调试离线会话
        )

    @Test
    fun loginLoadsAccountBeforeReturningACompleteSession() = runTest {
        val api = FakeAccountApi()

        val result = adapter(FakeHttpRemote(api))
            .execute(AuthEffect.LoginRemote("13800000000", "password")).first()

        assertEquals("token-1", api.detailToken)
        assertEquals(
            AuthEvent.RemoteAccepted(
                AuthSession(
                    user = User("7", "alice", "13800000000", "avatar"),
                    token = "token-1",
                    expiresAtMillis = now + ttl
                )
            ),
            result
        )
    }

    @Test
    fun loginRejectedByServer() = runTest {
        val api = FakeAccountApi().apply {
            loginResponse = ServerResponse(1001, false, "手机号或密码错误", null)
        }

        val result = adapter(FakeHttpRemote(api))
            .execute(AuthEffect.LoginRemote("13800000000", "password")).first()

        assertEquals(AuthEvent.RemoteRejected("手机号或密码错误"), result)
    }

    @Test
    fun loginMissingTokenIsRejected() = runTest {
        val api = FakeAccountApi().apply { loginResponse = ServerResponse(0, true, null, null) }

        val result = adapter(FakeHttpRemote(api))
            .execute(AuthEffect.LoginRemote("13800000000", "password")).first()

        assertEquals(AuthEvent.RemoteRejected("登录响应没有 token"), result)
    }

    @Test
    fun loginTransportTimeoutIsReported() = runTest {
        val http = FakeHttpRemote(FakeAccountApi()).apply { enqueue(HttpOutcome.Timeout) }

        val result = adapter(http)
            .execute(AuthEffect.LoginRemote("13800000000", "password")).first()

        assertEquals(AuthEvent.RemoteTimeout, result)
    }

    @Test
    fun loginHttpFailureIsRejected() = runTest {
        val http = FakeHttpRemote(FakeAccountApi())
            .apply { enqueue(HttpOutcome.Http(500, "服务端错误：500")) }

        val result = adapter(http)
            .execute(AuthEffect.LoginRemote("13800000000", "password")).first()

        assertEquals(AuthEvent.RemoteRejected("服务端错误：500"), result)
    }

    @Test
    fun validationKeepsTheOriginalTokenAndExpiration() = runTest {
        val api = FakeAccountApi()
        val original = AuthSession(User("old", "old", "old"), "saved-token", 88_000L)

        val result = adapter(FakeHttpRemote(api))
            .execute(AuthEffect.ValidateSession(original)).first()

        assertEquals("saved-token", api.detailToken)
        assertEquals(
            AuthEvent.SessionVerified(
                AuthSession(
                    user = User("7", "alice", "13800000000", "avatar"),
                    token = "saved-token",
                    expiresAtMillis = 88_000L
                )
            ),
            result
        )
    }

    @Test
    fun serverFailureDuringValidationKeepsTheLocalSession() = runTest {
        val http = FakeHttpRemote(FakeAccountApi())
            .apply { enqueue(HttpOutcome.Http(503, "服务端错误：503")) }
        val session = AuthSession(User("1", "alice", "13800000000"), "saved-token", 88_000L)

        val result = adapter(http).execute(AuthEffect.ValidateSession(session)).first()

        assertEquals(AuthEvent.SessionValidationTimeout, result)
    }

    @Test
    fun unauthorizedValidationRejectsTheLocalSession() = runTest {
        val http = FakeHttpRemote(FakeAccountApi())
            .apply { enqueue(HttpOutcome.Http(401, "服务端错误：401")) }
        val session = AuthSession(User("1", "alice", "13800000000"), "saved-token", 88_000L)

        val result = adapter(http).execute(AuthEffect.ValidateSession(session)).first()

        assertEquals(AuthEvent.SessionRejected("服务端错误：401"), result)
    }

    @Test
    fun validationTimeoutIsMappedToValidationFailure() = runTest {
        val http = FakeHttpRemote(FakeAccountApi()).apply { enqueue(HttpOutcome.Timeout) }
        val session = AuthSession(User("1", "alice", "13800000000"), "saved-token", 88_000L)

        val result = adapter(http).execute(AuthEffect.ValidateSession(session)).first()

        assertEquals(AuthEvent.SessionValidationTimeout, result)
    }

    @Test
    fun registrationDoesNotCreateASession() = runTest {
        val api = FakeAccountApi()

        val result = adapter(FakeHttpRemote(api)).execute(
            AuthEffect.RegisterRemote("13800000000", "password", "alice", "avatar")
        ).first()

        assertEquals(AuthEvent.RegistrationAccepted, result)
        assertEquals(
            RegisterRequest("alice", "password", "13800000000", "avatar"),
            api.registerRequest
        )
    }

    @Test
    fun registrationRejectedByServer() = runTest {
        val api = FakeAccountApi().apply {
            registerResponse = ServerResponse(1002, false, "手机号已注册", null)
        }

        val result = adapter(FakeHttpRemote(api)).execute(
            AuthEffect.RegisterRemote("13800000000", "password", "alice", "avatar")
        ).first()

        assertEquals(AuthEvent.RemoteRejected("手机号已注册"), result)
    }

    private class FakeAccountApi : AccountApi {
        var detailToken: String? = null
        var registerRequest: RegisterRequest? = null
        var loginResponse: ServerResponse<String> = ServerResponse(0, true, null, "token-1")
        var registerResponse: ServerResponse<AccountDto> = ServerResponse(0, true, null, null)
        var detailResponse: ServerResponse<AccountDto> = ServerResponse(
            0, true, null, AccountDto(7, "alice", "13800000000", "avatar", "user")
        )

        override suspend fun login(request: LoginRequest): ServerResponse<String> = loginResponse

        override suspend fun register(request: RegisterRequest): ServerResponse<AccountDto> {
            registerRequest = request
            return registerResponse
        }

        override suspend fun detail(token: String): ServerResponse<AccountDto> {
            detailToken = token
            return detailResponse
        }
    }

    /**
     * 两种模式:
     * 1. 预置了 HttpOutcome(enqueue)时直接返回,不执行 block —— 用于传输失败场景;
     * 2. 未预置时执行 block 并把结果包成 Success —— 用于业务编排场景,可捕获真实请求。
     */
    private class FakeHttpRemote(private val apiImpl: AccountApi) : HttpRemote {
        private val canned = ArrayDeque<HttpOutcome<*>>()

        fun enqueue(outcome: HttpOutcome<*>) {
            canned.add(outcome)
        }

        override fun <T> api(apiClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return apiImpl as T
        }

        override suspend fun <T> invoke(block: suspend () -> T): HttpOutcome<T> {
            val next = canned.removeFirstOrNull()
            @Suppress("UNCHECKED_CAST")
            return if (next != null) {
                next as HttpOutcome<T>
            } else {
                HttpOutcome.Success(block())
            }
        }
    }
}
