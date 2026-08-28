package com.biosensor.migratedev.port.adapter.localport.sql

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.biosensor.migratedev.database.DeviceBindingQueries
import com.biosensor.migratedev.database.LocalDatabase
import com.biosensor.migratedev.port.adapter.localport.SqlStore

/** 结构化实现浅壳:查询对象直接暴露,不映射、不建表接口。 */
class SqliteStore internal constructor(
    database: LocalDatabase,
) : SqlStore {

    override val deviceBinding: DeviceBindingQueries = database.deviceBindingQueries

    companion object {
        fun create(context: Context): SqlStore {
            val driver = AndroidSqliteDriver(
                schema = LocalDatabase.Schema,
                context = context.applicationContext,
                name = DATABASE_NAME,
            )
            return SqliteStore(LocalDatabase(driver))
        }

        private const val DATABASE_NAME = "migratedev.db"
    }
}
