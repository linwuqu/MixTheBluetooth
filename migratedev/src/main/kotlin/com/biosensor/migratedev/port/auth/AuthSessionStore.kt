package com.biosensor.migratedev.port.auth

import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.port.adapter.localport.KvReadResult
import com.biosensor.migratedev.port.adapter.localport.KvStore
import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.time.Clock

/**
 * 本地会话的存放约定:键名与解析只有这一处。
 *
 * 登录适配器要整份会话,上传适配器只要 token——两处读的是同一个键。
 * 键名和解析抄第二遍迟早走样,所以单独放这里。
 */
internal object AuthSessionStore {
    internal const val KEY = "auth.active_session"

    /** 解不开(不是合法 JSON、或 token 为空)返回 null。 */
    fun decode(raw: String, gson: Gson): AuthSession? = try {
        gson.fromJson(raw, AuthSession::class.java)?.takeIf { it.token.isNotBlank() }
    } catch (_: JsonParseException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /** 取当前会话:没有、解不开、已过期都返回 null。 */
    suspend fun read(kv: KvStore, gson: Gson, clock: Clock): AuthSession? {
        val raw = (kv.read(KEY) as? KvReadResult.Value)?.value ?: return null
        val session = decode(raw, gson) ?: return null
        val expiresAt = session.expiresAtMillis
        return session.takeIf { expiresAt == null || clock.millis() < expiresAt }
    }

    /** 取当前 token。上传一类"只需凭据"的场景用它。 */
    suspend fun token(kv: KvStore, gson: Gson, clock: Clock): String? =
        read(kv, gson, clock)?.token
}
