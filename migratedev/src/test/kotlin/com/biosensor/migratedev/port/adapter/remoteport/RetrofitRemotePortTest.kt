package com.biosensor.migratedev.port.adapter.remoteport

import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.port.adapter.localport.SessionStore
import com.biosensor.migratedev.port.auth.AuthCommand
import com.biosensor.migratedev.port.auth.AuthResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class RetrofitRemotePortTest {

    private val now = 10_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)

    @Test
    fun loginLoadsAccountBeforeReturningACompleteSession() = runTest {
        val api = FakeAccountApi()
        val port = RetrofitRemotePort.Auth(api, clock)

        val result = port.execute(AuthCommand.Remote.Login("13800000000", "password")).first()

        assertEquals("token-1", api.detailToken)
        assertEquals(
            AuthResult.Remote.Accepted(
                AuthSession(
                    user = User("7", "alice", "13800000000", "avatar"),
                    token = "token-1",
                    expiresAtMillis = now + SessionStore.LOCAL_TOKEN_TTL_MS
                )
            ),
            result
        )
    }

    @Test
    fun validationKeepsTheOriginalTokenAndExpiration() = runTest {
        val api = FakeAccountApi()
        val original = AuthSession(
            user = User("old", "old", "old"),
            token = "saved-token",
            expiresAtMillis = 88_000L
        )
        val port = RetrofitRemotePort.Auth(api, clock)

        val result = port.execute(AuthCommand.Remote.ValidateSession(original)).first()

        assertEquals("saved-token", api.detailToken)
        assertEquals(
            AuthResult.Remote.SessionVerified(
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
    fun registrationDoesNotCreateASession() = runTest {
        val api = FakeAccountApi()
        val port = RetrofitRemotePort.Auth(api, clock)

        val result = port.execute(
            AuthCommand.Remote.Register("alice", "password", "13800000000", "avatar")
        ).first()

        assertEquals(AuthResult.Remote.RegistrationAccepted, result)
        assertEquals(RegisterRequest("alice", "password", "13800000000", "avatar"), api.registerRequest)
    }

    @Test
    fun serverFailureDuringValidationKeepsTheLocalSession() = runTest {
        val session = AuthSession(User("1", "alice", "13800000000"), "saved-token", 88_000L)
        val port = RetrofitRemotePort.Auth(FailingDetailApi(503), clock)

        val result = port.execute(AuthCommand.Remote.ValidateSession(session)).first()

        assertEquals(AuthResult.Remote.SessionValidationTimeout, result)
    }

    @Test
    fun unauthorizedValidationRejectsTheLocalSession() = runTest {
        val session = AuthSession(User("1", "alice", "13800000000"), "saved-token", 88_000L)
        val port = RetrofitRemotePort.Auth(FailingDetailApi(401), clock)

        val result = port.execute(AuthCommand.Remote.ValidateSession(session)).first()

        assertEquals(AuthResult.Remote.SessionRejected("服务端错误：401"), result)
    }

    private class FakeAccountApi : AccountApi {
        var detailToken: String? = null
        var registerRequest: RegisterRequest? = null

        override suspend fun login(request: LoginRequest): ServerResponse<String> {
            return ServerResponse(0, true, null, "token-1")
        }

        override suspend fun register(request: RegisterRequest): ServerResponse<AccountDto> {
            registerRequest = request
            return ServerResponse(0, true, null, null)
        }

        override suspend fun detail(token: String): ServerResponse<AccountDto> {
            detailToken = token
            return ServerResponse(
                0,
                true,
                null,
                AccountDto(7, "alice", "13800000000", "avatar", "user")
            )
        }
    }

    private class FailingDetailApi(private val code: Int) : AccountApi {
        override suspend fun login(request: LoginRequest): ServerResponse<String> {
            error("unused")
        }

        override suspend fun register(request: RegisterRequest): ServerResponse<AccountDto> {
            error("unused")
        }

        override suspend fun detail(token: String): ServerResponse<AccountDto> {
            val body = "{}".toResponseBody("application/json".toMediaType())
            throw HttpException(Response.error<ServerResponse<AccountDto>>(code, body))
        }
    }
}
