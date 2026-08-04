package com.biosensor.migratedev.port.auth

import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.port.adapter.localport.EntropyReadResult
import com.biosensor.migratedev.port.adapter.localport.EntropyRemoveResult
import com.biosensor.migratedev.port.adapter.localport.EntropyWriteResult
import com.biosensor.migratedev.port.adapter.localport.StringEntropy
import com.biosensor.migratedev.port.adapter.remoteport.HttpOutcome
import com.biosensor.migratedev.port.adapter.remoteport.HttpRemote
import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import timber.log.Timber

// ===== 业务协议:auth 的端点与 DTO(业务自持) =====

interface AccountApi {
    @POST("/api/account/v1/login")
    suspend fun login(@Body request: LoginRequest): ServerResponse<String>

    @POST("/api/account/v1/register")
    suspend fun register(@Body request: RegisterRequest): ServerResponse<AccountDto>

    @GET("/api/account/v1/detail")
    suspend fun detail(@Header("token") token: String): ServerResponse<AccountDto>
}

data class LoginRequest(val phone: String, val password: String)

data class RegisterRequest(
    val username: String, val password: String, val phone: String, val avatarUrl: String?
)

data class AccountDto(
    val id: Long = 0,
    val username: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null,
    val role: String? = null
)

data class ServerResponse<T>(
    val code: Int = -1, val success: Boolean = false, val msg: String? = null, val data: T? = null
) {
    fun isOk(): Boolean = success || code == 0 || code == 200
}

// ===== 业务适配器:能力 → 业务词汇 =====

/**
 * auth 业务协议的全部内容:端点、会话 codec(键名 + gson + TTL + 过期判断)、
 * 传输失败到业务词汇的映射、Timber 日志收口(本地/远端命令各一行)。
 * 契约 [AuthPort] 不变,行为与重构前等价(含 ValidateSession 的超时/401/403 特殊语义)。
 */
class AuthPortAdapter(
    private val kv: StringEntropy,
    private val http: HttpRemote,
    private val clock: Clock = Clock.systemUTC(),
    private val gson: Gson = Gson()
) : AuthPort {

    private val api: AccountApi by lazy { http.api(AccountApi::class.java) }

    override fun execute(command: AuthCommand): Flow<AuthResult> = when (command) {
        is AuthCommand.Local -> executeLocal(command)
        is AuthCommand.Remote -> executeRemote(command)
    }

    // ── 本地:KV + 会话 codec ──────────────────────────────

    private fun executeLocal(command: AuthCommand.Local): Flow<AuthResult> = flow {
        emit(runLocal(command))
    }.flowOn(Dispatchers.IO)   // Tink 加解密为 CPU 密集

    private suspend fun runLocal(command: AuthCommand.Local): AuthResult.Local {
        val result = when (command) {
            AuthCommand.Local.ReadSession -> when (val read = kv.read(SESSION_KEY)) {
                EntropyReadResult.Missing -> AuthResult.Local.SessionMissing
                is EntropyReadResult.Failed -> corrupted()
                is EntropyReadResult.Found -> parse(read.value)
            }

            is AuthCommand.Local.SaveSession -> if (save(command.session)) {
                AuthResult.Local.SessionSaved
            } else {
                AuthResult.Local.SessionSaveFailed("本地会话写入失败")
            }

            AuthCommand.Local.ClearSession -> when (kv.remove(SESSION_KEY)) {
                EntropyRemoveResult.Removed, EntropyRemoveResult.Missing -> AuthResult.Local.SessionCleared

                is EntropyRemoveResult.Failed -> AuthResult.Local.SessionClearFailed("本地会话清理失败")
            }
        }
        Timber.tag(AUTH_TAG)
            .i("本地命令=%s 结果=%s", command::class.simpleName, result::class.simpleName)
        return result
    }

    private suspend fun parse(raw: String): AuthResult.Local {
        val session = try {
            gson.fromJson(raw, AuthSession::class.java)
        } catch (_: JsonParseException) {
            return corrupted()
        } catch (_: IllegalArgumentException) {
            return corrupted()
        }
        if (session == null || session.token.isBlank()) return corrupted()
        val expiresAt = session.expiresAtMillis
        return if (expiresAt != null && clock.millis() >= expiresAt) {
            AuthResult.Local.SessionExpired
        } else {
            AuthResult.Local.SessionFound(session)
        }
    }

    private suspend fun corrupted(): AuthResult.Local {
        kv.remove(SESSION_KEY)
        return AuthResult.Local.SessionReadFailed("本地会话无法解密")
    }

    private suspend fun save(session: AuthSession): Boolean {
        if (session.token.isBlank()) return false
        return try {
            kv.write(SESSION_KEY, gson.toJson(session)) == EntropyWriteResult.Written
        } catch (_: JsonParseException) {
            false
        }
    }

    // ── 远端:端点语义 + 传输失败映射 ──────────────────────

    private fun executeRemote(command: AuthCommand.Remote): Flow<AuthResult> = flow {
        emit(runRemote(command))
    }

    private suspend fun runRemote(command: AuthCommand.Remote): AuthResult.Remote {
        val result = when (command) {
            is AuthCommand.Remote.Login -> login(command.phone, command.password)
            is AuthCommand.Remote.Register -> register(command)
            is AuthCommand.Remote.ValidateSession -> validate(command.session)
        }
        Timber.tag(AUTH_TAG)
            .i("远端命令=%s 结果=%s", command::class.simpleName, result::class.simpleName)
        return result
    }

    private suspend fun login(phone: String, password: String): AuthResult.Remote {
        val login = when (val outcome = http.invoke { api.login(LoginRequest(phone, password)) }) {
            is HttpOutcome.Success -> outcome.data
            else -> return outcome.toRejected()
        }
        if (!login.isOk()) return AuthResult.Remote.Rejected(login.msg ?: "登录失败")
        val token = login.data?.takeIf { it.isNotBlank() }
            ?: return AuthResult.Remote.Rejected("登录响应没有 token")
        val detail = when (val outcome = http.invoke { api.detail(token) }) {
            is HttpOutcome.Success -> outcome.data
            else -> return outcome.toRejected()
        }
        val account = detail.data
        return if (!detail.isOk() || account == null) {
            AuthResult.Remote.Rejected(detail.msg ?: "用户详情验证失败")
        } else {
            AuthResult.Remote.Accepted(
                AuthSession(account.toDomainUser(), token, clock.millis() + LOCAL_TOKEN_TTL_MS)
            )
        }
    }

    private suspend fun register(command: AuthCommand.Remote.Register): AuthResult.Remote {
        val result = when (val outcome = http.invoke {
            api.register(
                RegisterRequest(
                    username = command.nickname,
                    password = command.password,
                    phone = command.phone,
                    avatarUrl = command.avatarUrl
                )
            )
        }) {
            is HttpOutcome.Success -> outcome.data
            else -> return outcome.toRejected()
        }
        return if (result.isOk()) {
            AuthResult.Remote.RegistrationAccepted
        } else {
            AuthResult.Remote.Rejected(result.msg ?: "注册失败")
        }
    }

    private suspend fun validate(session: AuthSession): AuthResult.Remote {
        val detail = when (val outcome = http.invoke { api.detail(session.token) }) {
            is HttpOutcome.Success -> outcome.data
            else -> return outcome.toSessionFailure()
        }
        val account = detail.data
        if (!detail.isOk() || account == null) {
            return AuthResult.Remote.SessionRejected(detail.msg ?: "token 已失效")
        }
        return AuthResult.Remote.SessionVerified(
            AuthSession(account.toDomainUser(), session.token, session.expiresAtMillis)
        )
    }

    // 传输失败 → 业务词汇(登录/注册语义)
    private fun HttpOutcome<*>.toRejected(): AuthResult.Remote = when (this) {
        HttpOutcome.Timeout -> AuthResult.Remote.Timeout
        is HttpOutcome.Network -> AuthResult.Remote.Rejected(message)
        is HttpOutcome.Http -> AuthResult.Remote.Rejected(message)
        is HttpOutcome.Success -> error("不可达")
    }

    // 传输失败 → 业务词汇(会话验证语义:超时/网络/非 401 都视为验证失败)
    private fun HttpOutcome<*>.toSessionFailure(): AuthResult.Remote = when (this) {
        HttpOutcome.Timeout -> AuthResult.Remote.SessionValidationTimeout
        is HttpOutcome.Network -> AuthResult.Remote.SessionValidationTimeout
        is HttpOutcome.Http -> if (code == 401 || code == 403) {
            AuthResult.Remote.SessionRejected(message)
        } else {
            AuthResult.Remote.SessionValidationTimeout
        }

        is HttpOutcome.Success -> error("不可达")
    }

    internal companion object {
        internal const val SESSION_KEY = "auth.active_session"
        private const val LOCAL_TOKEN_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val AUTH_TAG = "Auth.Port"
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
