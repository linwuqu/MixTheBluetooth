package com.biosensor.migratedev.port.adapter.localport

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedSessionStore(
    context: Context,
    private val gson: Gson = Gson()
) : SessionStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun read(): SessionRead {
        // 取出
        val encoded = preferences.getString(BLOB_KEY, null) ?: return SessionRead.Missing
        return try {
            // 解码
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            // 解密
            val plaintext = SessionCipher.decrypt(payload, loadOrCreateKey(), AAD)
            // 去json化
            val session = gson.fromJson(plaintext.toString(Charsets.UTF_8), AuthSession::class.java)
            if (session == null || session.token.isBlank()) corrupted() else SessionRead.Found(session)
        } catch (_: GeneralSecurityException) {
            corrupted()
        } catch (_: IllegalArgumentException) {
            corrupted()
        } catch (_: JsonParseException) {
            corrupted()
        }
    }

    override fun save(session: AuthSession): Boolean {
        return try {
            // json化
            val plaintext = gson.toJson(session).toByteArray(Charsets.UTF_8)
            // 加密
            val payload = SessionCipher.encrypt(plaintext, loadOrCreateKey(), AAD)
            // 编码成文本
            val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
            // 存入SP 这里不能改成 apply() + return true 因为就是需要立即存入
            preferences.edit().putString(BLOB_KEY, encoded).commit()
        } catch (_: GeneralSecurityException) {
            false
        } catch (_: JsonParseException) {
            false
        }
    }

    override fun clear(): Boolean {
        return preferences.edit().remove(BLOB_KEY).commit()
    }

    private fun corrupted(): SessionRead {
        preferences.edit().remove(BLOB_KEY).apply()
        return SessionRead.Corrupted
    }

    private fun loadOrCreateKey(): SecretKey {
        synchronized(KEY_LOCK) {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing
            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            return generator.generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "auth_session_secure"
        const val BLOB_KEY = "session_blob_v1"
        const val KEY_ALIAS = "migratedev.auth.session.aes256"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        val AAD = "migratedev/auth_session_secure/session_blob_v1".toByteArray(Charsets.UTF_8)
        val KEY_LOCK = Any()
    }
}

internal object SessionCipher {
    private const val VERSION: Byte = 1
    private const val IV_SIZE = 12
    private const val TAG_SIZE_BITS = 128
    private const val TAG_SIZE_BYTES = 16

    fun encrypt(plaintext: ByteArray, key: SecretKey, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        if (iv.size != IV_SIZE) {
            throw GeneralSecurityException("Unexpected AES-GCM IV size")
        }
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return byteArrayOf(VERSION) + iv + ciphertext
    }

    fun decrypt(payload: ByteArray, key: SecretKey, aad: ByteArray): ByteArray {
        if (payload.size <= 1 + IV_SIZE + TAG_SIZE_BYTES || payload[0] != VERSION) {
            throw GeneralSecurityException("Invalid encrypted session payload")
        }
        val iv = payload.copyOfRange(1, 1 + IV_SIZE)
        val ciphertext = payload.copyOfRange(1 + IV_SIZE, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }
}
