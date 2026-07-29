package com.biosensor.migratedev.port.adapter.localport.database

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.biosensor.migratedev.database.LocalDatabase

object LocalDatabaseFactory {
    fun create(context: Context): LocalDatabase {
        val driver = AndroidSqliteDriver(
            schema = LocalDatabase.Schema,
            context = context.applicationContext,
            name = DATABASE_NAME
        )
        return LocalDatabase(driver)
    }

    private const val DATABASE_NAME = "migratedev.db"
}
