package com.biosensor.migratedev.port.auth

import com.biosensor.migratedev.BuildConfig
import com.biosensor.migratedev.decisioncore.auth.AuthEffect
import com.biosensor.migratedev.decisioncore.auth.AuthEvent
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

// ===== 业务适配器:指令直达 + 分层上报 =====

/**
 * auth 业务协议的全部内容:端点、会话 codec(键名 + gson + TTL + 过期判断)、
 * 传输失败到领域事件的映射、Timber 日志收口(本地/远端命令各一行)。
 *
 * 下行:直接执行 [AuthEffect](指令直达,无 Command 传话);
 * 上行:底层结果(EntropyReadResult / HttpOutcome)在此翻译成 [AuthEvent](分层上报)。
 */
class AuthPortAdapter(
    private val kv: StringEntropy,
    private val http: HttpRemote,
    private val clock: Clock = Clock.systemUTC(),
    private val gson: Gson = Gson(),
    private val debugOfflineMode: Boolean = BuildConfig.DEBUG
) : AuthPort {

    private val api: AccountApi by lazy { http.api(AccountApi::class.java) }

    override fun execute(effect: AuthEffect): Flow<AuthEvent> = when (effect) {
        // 本地:KV + 会话 codec
        is AuthEffect.ReadSession, is AuthEffect.SaveSession, is AuthEffect.ClearSession -> executeLocal(
            effect
        )

        // 远端:端点语义
        is AuthEffect.LoginRemote, is AuthEffect.RegisterRemote, is AuthEffect.ValidateSession -> executeRemote(
            effect
        )
    }

    // ── 本地:KV + 会话 codec ──────────────────────────────

    private fun executeLocal(effect: AuthEffect): Flow<AuthEvent> = flow {
        emit(runLocal(effect))
    }.flowOn(Dispatchers.IO)   // Tink 加解密为 CPU 密集

    private suspend fun runLocal(effect: AuthEffect): AuthEvent {
        val result = when (effect) {
            is AuthEffect.ReadSession -> when (val read = kv.read(SESSION_KEY)) {
                EntropyReadResult.Missing -> DebugOfflineSession.maybeInject(   // 调试专用,删除时连带移除
                    AuthEvent.SessionMissing, enabled = debugOfflineMode
                )

                is EntropyReadResult.Failed -> corrupted()
                is EntropyReadResult.Found -> parse(read.value)
            }

            is AuthEffect.SaveSession -> if (save(effect.session)) {
                AuthEvent.SessionSaved
            } else {
                AuthEvent.SessionSaveFailed("本地会话写入失败")
            }

            is AuthEffect.ClearSession -> when (kv.remove(SESSION_KEY)) {
                EntropyRemoveResult.Removed, EntropyRemoveResult.Missing -> AuthEvent.SessionCleared

                is EntropyRemoveResult.Failed -> AuthEvent.SessionClearFailed("本地会话清理失败")
            }

            else -> error("不可达")
        }
        Timber.tag(AUTH_TAG)
            .i("本地命令=%s 结果=%s", effect::class.simpleName, result::class.simpleName)
        return result
    }

    private suspend fun parse(raw: String): AuthEvent {
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
            AuthEvent.SessionExpired
        } else {
            AuthEvent.SessionFound(session)
        }
    }

    private suspend fun corrupted(): AuthEvent {
        kv.remove(SESSION_KEY)
        return AuthEvent.SessionReadFailed("本地会话无法解密")
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

    private fun executeRemote(effect: AuthEffect): Flow<AuthEvent> = flow {
        emit(runRemote(effect))
    }

    private suspend fun runRemote(effect: AuthEffect): AuthEvent {
        val result = when (effect) {
            is AuthEffect.LoginRemote -> login(effect.phone, effect.password)
            is AuthEffect.RegisterRemote -> register(effect)
            is AuthEffect.ValidateSession -> validate(effect.session)
            else -> error("不可达")
        }
        Timber.tag(AUTH_TAG)
            .i("远端命令=%s 结果=%s", effect::class.simpleName, result::class.simpleName)
        return result
    }

    private suspend fun login(phone: String, password: String): AuthEvent {
        // outcome 只确定是否拿到了 Http 响应
        val login = when (val outcome = http.invoke { api.login(LoginRequest(phone, password)) }) {
            is HttpOutcome.Success -> outcome.data
            else -> return outcome.toRejected()
        }
        // isOk 判断是否业务成功
        if (!login.isOk()) return AuthEvent.RemoteRejected(login.msg ?: "登录失败")
        val token = login.data?.takeIf { it.isNotBlank() }
            ?: return AuthEvent.RemoteRejected("登录响应没有 token")
        val detail = when (val outcome = http.invoke { api.detail(token) }) {
            is HttpOutcome.Success -> outcome.data
            else -> return outcome.toRejected()
        }
        val account = detail.data
        return if (!detail.isOk() || account == null) {
            AuthEvent.RemoteRejected(detail.msg ?: "用户详情验证失败")
        } else {
            AuthEvent.RemoteAccepted(
                AuthSession(account.toDomainUser(), token, clock.millis() + LOCAL_TOKEN_TTL_MS)
            )
        }
    }

    private suspend fun register(effect: AuthEffect.RegisterRemote): AuthEvent {
        val result = when (val outcome = http.invoke {
            api.register(
                RegisterRequest(
                    username = effect.nickname,
                    password = effect.password,
                    phone = effect.phone,
                    avatarUrl = effect.avatarUrl
                )
            )
        }) {
            is HttpOutcome.Success -> outcome.data
            else -> return outcome.toRejected()
        }
        return if (result.isOk()) {
            AuthEvent.RegistrationAccepted
        } else {
            AuthEvent.RemoteRejected(result.msg ?: "注册失败")
        }
    }

    private suspend fun validate(session: AuthSession): AuthEvent {
        val detail = when (val outcome = http.invoke { api.detail(session.token) }) {
            is HttpOutcome.Success -> outcome.data
            // 调试专用:远端不可达时信任本地会话,删除时连带移除
            else -> return DebugOfflineSession.maybeTrust(
                outcome.toSessionFailure(), local = session, enabled = debugOfflineMode
            )
        }
        val account = detail.data
        if (!detail.isOk() || account == null) {
            return AuthEvent.SessionRejected(detail.msg ?: "token 已失效")
        }
        return AuthEvent.SessionVerified(
            AuthSession(account.toDomainUser(), session.token, session.expiresAtMillis)
        )
    }

    // 传输失败 → 领域事件(登录/注册语义)
    private fun HttpOutcome<*>.toRejected(): AuthEvent = when (this) {
        HttpOutcome.Timeout -> AuthEvent.RemoteTimeout
        is HttpOutcome.Network -> AuthEvent.RemoteRejected(message)
        is HttpOutcome.Http -> AuthEvent.RemoteRejected(message)
        is HttpOutcome.Success -> error("不可达")
    }

    // 传输失败 → 领域事件(会话验证语义:超时/网络/非 401 都视为验证失败)
    private fun HttpOutcome<*>.toSessionFailure(): AuthEvent = when (this) {
        HttpOutcome.Timeout -> AuthEvent.SessionValidationTimeout
        is HttpOutcome.Network -> AuthEvent.SessionValidationTimeout
        is HttpOutcome.Http -> if (code == 401 || code == 403) {
            AuthEvent.SessionRejected(message)
        } else {
            AuthEvent.SessionValidationTimeout
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

// ===== 调试专用:离线会话 =====
// 服务器不可达时注入假会话直接进入扫描页,便于纯蓝牙功能调试。
// 移除方式:删除本段(注解 + 对象)与两处"调试专用"调用点,零残留。

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class DebugOnly

/** 调试专用:仅 DEBUG 构建生效,release 构建行为与现状完全一致。 */
@DebugOnly
private object DebugOfflineSession {
    private const val TAG = "Auth.Port.Debug"
    private const val TTL_MILLIS = 7L * 24 * 60 * 60 * 1000

    /** 本地无会话时:启用注入离线会话,否则保持原结果。 */
    fun maybeInject(sessionMissing: AuthEvent, enabled: Boolean): AuthEvent =
        if (enabled) {
            Timber.tag(TAG).w("调试模式:本地无会话,注入离线会话")
            AuthEvent.SessionFound(offlineSession())
        } else {
            sessionMissing
        }

    /** 远端验证传输失败时:启用则信任本地会话,否则保持失败结果。 */
    fun maybeTrust(failure: AuthEvent, local: AuthSession, enabled: Boolean): AuthEvent =
        if (enabled) {
            Timber.tag(TAG).w("调试模式:远端验证不可达,信任本地会话")
            AuthEvent.SessionVerified(local)
        } else {
            failure
        }

    private fun offlineSession(): AuthSession = AuthSession(
        user = User(id = "offline-dev", userName = "离线开发", telephone = "00000000000"),
        token = "offline-token",
        expiresAtMillis = Clock.systemUTC().millis() + TTL_MILLIS
    )
}
