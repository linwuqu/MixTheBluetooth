package com.biosensor.migratedev.port.auth

import com.biosensor.migratedev.port.adapter.localport.KvReadResult
import com.biosensor.migratedev.port.adapter.localport.KvRemoveResult
import com.biosensor.migratedev.port.adapter.localport.KvStore
import com.biosensor.migratedev.port.adapter.localport.KvWriteResult
import com.biosensor.migratedev.BuildConfig
import com.biosensor.migratedev.decisioncore.auth.AuthEffect
import com.biosensor.migratedev.decisioncore.auth.AuthEvent
import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.port.adapter.remoteport.ApiError
import com.biosensor.migratedev.port.adapter.remoteport.HttpOutcome
import com.biosensor.migratedev.port.adapter.remoteport.HttpRemote
import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import timber.log.Timber
import java.time.Clock

interface AccountApi {
    @POST("/api/account/v1/login")
    suspend fun login(@Body request: LoginRequest): ServerResponse<String>

    @POST("/api/account/v1/register")
    suspend fun register(@Body request: RegisterRequest): ServerResponse<AccountDto>

    @GET("/api/account/v1/detail")
    suspend fun detail(@Header("token") token: String): ServerResponse<AccountDto>
}



class AuthPortAdapter(
    private val kv: KvStore,
    private val http: HttpRemote,
    private val clock: Clock = Clock.systemUTC(),
    private val gson: Gson = Gson(),
    private val debugOfflineMode: Boolean = BuildConfig.DEBUG
) : AuthPort {

    private val api: AccountApi by lazy { http.api(AccountApi::class.java) }

    override fun execute(effect: AuthEffect): Flow<AuthEvent> = when (effect) {
        is AuthEffect.ReadSession, is AuthEffect.SaveSession, is AuthEffect.ClearSession -> executeLocal(
            effect
        )
        is AuthEffect.LoginRemote, is AuthEffect.RegisterRemote, is AuthEffect.ValidateSession -> executeRemote(
            effect
        )
    }

    private fun executeLocal(effect: AuthEffect): Flow<AuthEvent> = flow {
        emit(runLocal(effect))
    }.flowOn(Dispatchers.IO)

    private suspend fun runLocal(effect: AuthEffect): AuthEvent {
        val result = when (effect) {
            is AuthEffect.ReadSession -> when (val read = kv.read(SESSION_KEY)) {
                KvReadResult.None -> DebugOfflineSession.maybeInject(   // 调试专用,删除时连带移除
                    AuthEvent.SessionMissing, enabled = debugOfflineMode
                )

                is KvReadResult.Failed -> corrupted()
                is KvReadResult.Value -> parse(read.value)
            }

            is AuthEffect.SaveSession -> if (save(effect.session)) {
                AuthEvent.SessionSaved
            } else {
                AuthEvent.SessionSaveFailed("本地会话写入失败")
            }

            is AuthEffect.ClearSession -> when (kv.remove(SESSION_KEY)) {
                KvRemoveResult.Done, KvRemoveResult.None -> AuthEvent.SessionCleared
                is KvRemoveResult.Failed -> AuthEvent.SessionClearFailed("本地会话清理失败")
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
            kv.write(SESSION_KEY, gson.toJson(session)) == KvWriteResult.Done
        } catch (_: JsonParseException) {
            false
        }
    }

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
        val login = when (val outcome = http.invoke { api.login(LoginRequest(phone, password)) }) {
            is HttpOutcome.Completed -> outcome.data
            else -> return outcome.toRejected()
        }
        if (!login.isOk()) return AuthEvent.RemoteRejected(login.msg ?: "登录失败")
        val token = login.data?.takeIf { it.isNotBlank() }
            ?: return AuthEvent.RemoteRejected("登录响应没有 token")
        val detail = when (val outcome = http.invoke { api.detail(token) }) {
            is HttpOutcome.Completed -> outcome.data
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
            is HttpOutcome.Completed -> outcome.data
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
            is HttpOutcome.Completed -> outcome.data
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

    private fun HttpOutcome<*>.toRejected(): AuthEvent = when (this) {
        is HttpOutcome.Failure -> when (val error = this.error) {
            ApiError.TimeoutError -> AuthEvent.RemoteTimeout
            is ApiError.UnreachableError -> AuthEvent.RemoteRejected(error.msg)
            is ApiError.HttpError -> AuthEvent.RemoteRejected(error.msg)
            is ApiError.UnKnowError -> AuthEvent.RemoteRejected(error.msg)
        }

        is HttpOutcome.Completed -> error("不可达")
    }

    // 传输失败 → 领域事件(会话验证语义:超时/网络/非 401 都视为验证失败)
    private fun HttpOutcome<*>.toSessionFailure(): AuthEvent = when (this) {
        is HttpOutcome.Failure -> when (val error = this.error) {
            ApiError.TimeoutError -> AuthEvent.SessionValidationTimeout
            is ApiError.UnreachableError -> AuthEvent.SessionValidationTimeout
            is ApiError.HttpError -> if (error.code == 401 || error.code == 403) {
                AuthEvent.SessionRejected(error.msg)
            } else {
                AuthEvent.SessionValidationTimeout
            }

            is ApiError.UnKnowError -> AuthEvent.SessionValidationTimeout
        }

        is HttpOutcome.Completed -> error("不可达")
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
