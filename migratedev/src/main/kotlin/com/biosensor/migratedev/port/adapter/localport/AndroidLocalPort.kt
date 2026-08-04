package com.biosensor.migratedev.port.adapter.localport

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.biosensor.migratedev.database.LocalDatabase
import com.biosensor.migratedev.port.adapter.localport.database.LocalDatabaseFactory
import com.biosensor.migratedev.port.adapter.localport.entropy.TinkStringEntropy
import com.biosensor.migratedev.port.adapter.localport.file.OkioLocalFileClient
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Android 侧的能力构造工厂:一次创建键值存储、sqlite、文件三个能力。
 * 只负责构造,不再实现任何捆绑接口。
 */
class AndroidLocalPort private constructor(
    val entropy: StringEntropy, val sqlite: LocalDatabase, val files: LocalFileClient
) {
    companion object {
        fun create(
            context: Context, applicationScope: CoroutineScope
        ): AndroidLocalPort {
            val application = context.applicationContext
            // 创建一个 DataStore 对象 (DataStore 用来替代老旧的 SharedPreferences 它用异步、协程的方式保存键值对)
            val dataStore = PreferenceDataStoreFactory.create(
                scope = applicationScope, produceFile = {
                    application.preferencesDataStoreFile(
                        ENTROPY_DATASTORE_NAME
                    )
                })
            val fileClient = OkioLocalFileClient(
                fileSystem = FileSystem.SYSTEM, roots = mapOf(
                    // 使用相对路径确定绝对路径然后转换成 Path 对象
                    FileSpace.LOGS to application.filesDir.resolve("logs").absolutePath.toPath(),
                    FileSpace.RECEIVED to application.filesDir.resolve("received").absolutePath.toPath(),
                    FileSpace.OUTGOING to application.filesDir.resolve("outgoing").absolutePath.toPath(),
                    FileSpace.CACHE to application.cacheDir.resolve("cache").absolutePath.toPath()
                )
            )
            return AndroidLocalPort(
                entropy = TinkStringEntropy(dataStore = dataStore, aead = createAead(application)),
                sqlite = LocalDatabaseFactory.create(application),
                files = fileClient
            )
        }

        private fun createAead(context: Context): Aead {
            AeadConfig.register()
            val keyset = AndroidKeysetManager.Builder().withSharedPref(
                context, KEYSET_NAME, KEYSET_PREFERENCES_NAME
            ).withKeyTemplate(KeyTemplates.get("AES256_GCM")).withMasterKeyUri(MASTER_KEY_URI)
                .build().keysetHandle
            return keyset.getPrimitive(
                RegistryConfiguration.get(), Aead::class.java
            )
        }

        private const val ENTROPY_DATASTORE_NAME = "string_entropy.preferences_pb"
        private const val KEYSET_NAME = "string_entropy_keyset"
        private const val KEYSET_PREFERENCES_NAME = "string_entropy_keyset_preferences"
        private const val MASTER_KEY_URI = "android-keystore://migratedev.string_entropy.master"
    }
}
