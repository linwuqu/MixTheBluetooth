package com.biosensor.migratedev.port.adapter.localport.entropy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.biosensor.migratedev.port.adapter.localport.EntropyReadResult
import com.biosensor.migratedev.port.adapter.localport.EntropyRemoveResult
import com.biosensor.migratedev.port.adapter.localport.EntropyWriteResult
import com.biosensor.migratedev.port.adapter.localport.StringEntropy
import com.google.crypto.tink.Aead
import kotlinx.coroutines.flow.first
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

class TinkStringEntropy internal constructor(
    private val preferences: EntropyPreferences, private val aead: Aead
) : StringEntropy {
    constructor(
        dataStore: DataStore<Preferences>, aead: Aead
    ) : this(
        preferences = DataStoreEntropyPreferences(dataStore), aead = aead
    )

    override suspend fun read(key: String): EntropyReadResult {
        return try {
            val encoded = preferences.read(preferenceKey(key)) ?: return EntropyReadResult.Missing
            val ciphertext = encoded.decodeBase64()?.toByteArray()
                ?: return EntropyReadResult.Failed("加密字符串格式无效")
            val plaintext = aead.decrypt(ciphertext, associatedData(key))
            EntropyReadResult.Found(plaintext.toString(Charsets.UTF_8))
        } catch (failure: Exception) {
            EntropyReadResult.Failed(
                failure.message ?: "加密字符串读取失败"
            )
        }
    }

    override suspend fun write(
        key: String, value: String
    ): EntropyWriteResult {
        return try {
            val ciphertext = aead.encrypt(
                value.toByteArray(Charsets.UTF_8), associatedData(key)
            )
            preferences.write(
                preferenceKey(key), ciphertext.toByteString().base64()
            )
            EntropyWriteResult.Written
        } catch (failure: Exception) {
            EntropyWriteResult.Failed(
                failure.message ?: "加密字符串写入失败"
            )
        }
    }

    override suspend fun remove(key: String): EntropyRemoveResult {
        return try {
            val preferenceKey = preferenceKey(key)
            val existed = preferences.contains(preferenceKey)
            if (!existed) {
                EntropyRemoveResult.Missing
            } else {
                preferences.remove(preferenceKey)
                EntropyRemoveResult.Removed
            }
        } catch (failure: Exception) {
            EntropyRemoveResult.Failed(
                failure.message ?: "加密字符串删除失败"
            )
        }
    }

    override suspend fun contains(key: String): Boolean {
        return try {
            preferences.contains(preferenceKey(key))
        } catch (_: Exception) {
            false
        }
    }

    private fun preferenceKey(key: String) = "$KEY_PREFIX$key"

    private fun associatedData(key: String): ByteArray =
        "$AAD_PREFIX$key".toByteArray(Charsets.UTF_8)

    private companion object {
        const val KEY_PREFIX = "entropy:"
        const val AAD_PREFIX = "migratedev/string-entropy/"
    }
}

internal interface EntropyPreferences {
    suspend fun read(key: String): String?

    suspend fun write(key: String, value: String)

    suspend fun remove(key: String)

    suspend fun contains(key: String): Boolean
}

private class DataStoreEntropyPreferences(
    private val dataStore: DataStore<Preferences>
) : EntropyPreferences {
    override suspend fun read(key: String): String? =
        dataStore.data.first()[stringPreferencesKey(key)]

    override suspend fun write(key: String, value: String) {
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey(key)] = value
        }
    }

    override suspend fun remove(key: String) {
        dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(key))
        }
    }

    override suspend fun contains(key: String): Boolean =
        dataStore.data.first().contains(stringPreferencesKey(key))
}
