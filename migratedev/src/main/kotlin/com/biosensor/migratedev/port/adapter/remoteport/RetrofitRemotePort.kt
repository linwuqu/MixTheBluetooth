package com.biosensor.migratedev.port.adapter.remoteport

import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.port.adapter.localport.SessionStore
import com.biosensor.migratedev.port.auth.AuthCommand
import com.biosensor.migratedev.port.auth.AuthResult
import java.io.IOException
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AccountApi {
    @POST("/api/account/v1/login")
    suspend fun login(@Body request: LoginRequest): ServerResponse<String>

    @POST("/api/account/v1/register")
    suspend fun register(@Body request: RegisterRequest): ServerResponse<AccountDto>

    @GET("/api/account/v1/detail")
    suspend fun detail(@Header("token") token: String): ServerResponse<AccountDto>
}

data class LoginRequest(
    val phone: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val password: String,
    val phone: String,
    val avatarUrl: String?
)

data class AccountDto(
    val id: Long = 0,
    val username: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null,
    val role: String? = null
)

data class ServerResponse<T>(
    val code: Int = -1,
    val success: Boolean = false,
    val msg: String? = null,
    val data: T? = null
) {
    fun isOk(): Boolean = success || code == 0 || code == 200
}

object RetrofitRemotePort {
    fun createAccountApi(baseUrl: String): AccountApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AccountApi::class.java)
    }

    class Auth(
        private val api: AccountApi,
        private val clock: Clock
    ) {
        fun execute(command: AuthCommand.Remote): Flow<AuthResult> = flow {
            try {
                val result = withTimeout(30_000L) { executeRemote(command) }
                emit(result)
            } catch (_: TimeoutCancellationException) {
                emit(command.timeoutResult())
            } catch (failure: IOException) {
                emit(command.networkFailure(failure))
            } catch (failure: HttpException) {
                emit(command.httpFailure(failure))
            }
        }

        private suspend fun executeRemote(command: AuthCommand.Remote): AuthResult.Remote {
            return when (command) {
                is AuthCommand.Remote.Login -> login(command)
                is AuthCommand.Remote.Register -> register(command)
                is AuthCommand.Remote.ValidateSession -> validateSession(command)
            }
        }

        private suspend fun login(command: AuthCommand.Remote.Login): AuthResult.Remote {
            val login = api.login(LoginRequest(command.phone, command.password))
            if (!login.isOk()) {
                return AuthResult.Remote.Rejected(login.msg ?: "登录失败")
            }
            val token = login.data?.takeIf { it.isNotBlank() }
                ?: return AuthResult.Remote.Rejected("登录响应没有 token")
            val detail = api.detail(token)
            val account = detail.data
            if (!detail.isOk() || account == null) {
                return AuthResult.Remote.Rejected(detail.msg ?: "用户详情验证失败")
            }
            return AuthResult.Remote.Accepted(
                AuthSession(
                    user = account.toDomainUser(),
                    token = token,
                    expiresAtMillis = clock.millis() + SessionStore.LOCAL_TOKEN_TTL_MS
                )
            )
        }

        private suspend fun register(command: AuthCommand.Remote.Register): AuthResult.Remote {
            val result = api.register(
                RegisterRequest(
                    username = command.nickname,
                    password = command.password,
                    phone = command.phone,
                    avatarUrl = command.avatarUrl
                )
            )
            return if (result.isOk()) {
                AuthResult.Remote.RegistrationAccepted
            } else {
                AuthResult.Remote.Rejected(result.msg ?: "注册失败")
            }
        }

        private suspend fun validateSession(
            command: AuthCommand.Remote.ValidateSession
        ): AuthResult.Remote {
            val detail = api.detail(command.session.token)
            val account = detail.data
            if (!detail.isOk() || account == null) {
                return AuthResult.Remote.SessionRejected(detail.msg ?: "token 已失效")
            }
            return AuthResult.Remote.SessionVerified(
                AuthSession(
                    user = account.toDomainUser(),
                    token = command.session.token,
                    expiresAtMillis = command.session.expiresAtMillis
                )
            )
        }

        private fun AuthCommand.Remote.timeoutResult(): AuthResult.Remote {
            return if (this is AuthCommand.Remote.ValidateSession) {
                AuthResult.Remote.SessionValidationTimeout
            } else {
                AuthResult.Remote.Timeout
            }
        }

        private fun AuthCommand.Remote.networkFailure(failure: IOException): AuthResult.Remote {
            return if (this is AuthCommand.Remote.ValidateSession) {
                AuthResult.Remote.SessionValidationTimeout
            } else {
                AuthResult.Remote.Rejected("网络错误：${failure.message ?: "unknown"}")
            }
        }

        private fun AuthCommand.Remote.httpFailure(failure: HttpException): AuthResult.Remote {
            val message = "服务端错误：${failure.code()}"
            return if (this is AuthCommand.Remote.ValidateSession) {
                if (failure.code() == 401 || failure.code() == 403) {
                    AuthResult.Remote.SessionRejected(message)
                } else {
                    AuthResult.Remote.SessionValidationTimeout
                }
            } else {
                AuthResult.Remote.Rejected(message)
            }
        }
    }
}

private fun AccountDto.toDomainUser(): User {
    return User(
        id = id.toString(),
        userName = username.orEmpty(),
        telephone = phone.orEmpty(),
        avatarUrl = avatarUrl
    )
}
