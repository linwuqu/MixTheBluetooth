package com.biosensor.migratedev.port.adapter.localport.kv

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.biosensor.migratedev.port.adapter.localport.KvReadResult
import com.biosensor.migratedev.port.adapter.localport.KvRemoveResult
import com.biosensor.migratedev.port.adapter.localport.KvStore
import com.biosensor.migratedev.port.adapter.localport.KvWriteResult
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/** 键值实现:两步组合——存储([KvStorage]) + 加密([KvCipher])。 */
internal class TinkKvStore(
    private val storage: KvStorage,
    private val cipher: KvCipher,
) : KvStore {

    companion object {
        fun create(context: Context, scope: CoroutineScope): KvStore {
            val application = context.applicationContext
            val dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { application.preferencesDataStoreFile(ENTROPY_DATASTORE_NAME) },
            )
            return TinkKvStore(
                storage = DataStoreKvStorage(dataStore),
                cipher = TinkAeadCipher(createAead(application)),
            )
        }

        private fun createAead(context: Context): Aead {
            AeadConfig.register()
            val keyset = AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREFERENCES_NAME)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build().keysetHandle
            return keyset.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }

        private const val ENTROPY_DATASTORE_NAME = "string_entropy.preferences_pb"
        private const val KEYSET_NAME = "string_entropy_keyset"
        private const val KEYSET_PREFERENCES_NAME = "string_entropy_keyset_preferences"
        private const val MASTER_KEY_URI = "android-keystore://migratedev.string_entropy.master"
        private const val KEY_PREFIX = "entropy:"
    }

    override suspend fun read(key: String): KvReadResult = try {
        val stored = storage.read(preferenceKey(key)) ?: return KvReadResult.None
        KvReadResult.Value(cipher.decrypt(key, stored))
    } catch (failure: Exception) {
        KvReadResult.Failed(failure.message ?: "加密字符串读取失败")
    }

    override suspend fun write(key: String, value: String): KvWriteResult = try {
        storage.write(preferenceKey(key), cipher.encrypt(key, value))
        KvWriteResult.Done
    } catch (failure: Exception) {
        KvWriteResult.Failed(failure.message ?: "加密字符串写入失败")
    }

    override suspend fun remove(key: String): KvRemoveResult = try {
        if (storage.remove(preferenceKey(key))) KvRemoveResult.Done else KvRemoveResult.None
    } catch (failure: Exception) {
        KvRemoveResult.Failed(failure.message ?: "加密字符串删除失败")
    }

    override suspend fun contains(key: String): Boolean = try {
        storage.contains(preferenceKey(key))
    } catch (_: Exception) {
        false
    }

    private fun preferenceKey(key: String) = "$KEY_PREFIX$key"
}

/** 第一步:存储(DataStore 最小读写面)。 */
internal interface KvStorage {
    suspend fun read(key: String): String?

    suspend fun write(key: String, value: String)

    /** 返回 true 表示确实删掉了。 */
    suspend fun remove(key: String): Boolean

    suspend fun contains(key: String): Boolean
}

/** 第二步:加密(明文 ↔ 密文)。 */
internal interface KvCipher {
    fun encrypt(key: String, plaintext: String): String

    fun decrypt(key: String, ciphertext: String): String
}

private class DataStoreKvStorage(
    private val dataStore: DataStore<Preferences>,
) : KvStorage {
    override suspend fun read(key: String): String? =
        dataStore.data.first()[stringPreferencesKey(key)]

    override suspend fun write(key: String, value: String) {
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey(key)] = value
        }
    }

    override suspend fun remove(key: String): Boolean {
        var existed = false
        dataStore.edit { preferences ->
            val preferenceKey = stringPreferencesKey(key)
            existed = preferences.contains(preferenceKey)
            preferences.remove(preferenceKey)
        }
        return existed
    }

    override suspend fun contains(key: String): Boolean =
        dataStore.data.first().contains(stringPreferencesKey(key))
}

internal class TinkAeadCipher(
    private val aead: Aead,
) : KvCipher {
    override fun encrypt(key: String, plaintext: String): String =
        aead.encrypt(plaintext.toByteArray(Charsets.UTF_8), associatedData(key))
            .toByteString().base64()

    override fun decrypt(key: String, ciphertext: String): String {
        val decoded = ciphertext.decodeBase64()?.toByteArray()
            ?: throw IllegalArgumentException("加密字符串格式无效")
        return aead.decrypt(decoded, associatedData(key)).toString(Charsets.UTF_8)
    }

    private fun associatedData(key: String): ByteArray =
        "$AAD_PREFIX$key".toByteArray(Charsets.UTF_8)

    private companion object {
        const val AAD_PREFIX = "migratedev/string-entropy/"
    }
}
