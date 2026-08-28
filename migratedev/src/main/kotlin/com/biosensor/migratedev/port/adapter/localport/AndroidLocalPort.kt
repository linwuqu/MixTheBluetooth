package com.biosensor.migratedev.port.adapter.localport

import android.content.Context
import com.biosensor.migratedev.port.adapter.localport.file.OkioFileStore
import com.biosensor.migratedev.port.adapter.localport.kv.TinkKvStore
import com.biosensor.migratedev.port.adapter.localport.sql.SqliteStore
import kotlinx.coroutines.CoroutineScope

object AndroidLocalPort {
    fun create(context: Context, applicationScope: CoroutineScope): LocalPort = LocalPort(
        kv = TinkKvStore.create(context, applicationScope),
        sql = SqliteStore.create(context),
        files = OkioFileStore.create(context),
    )
}
