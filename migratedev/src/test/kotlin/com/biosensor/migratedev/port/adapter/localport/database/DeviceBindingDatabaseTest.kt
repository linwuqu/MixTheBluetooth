package com.biosensor.migratedev.port.adapter.localport.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.biosensor.migratedev.database.LocalDatabase
import com.biosensor.migratedev.port.adapter.localport.sql.SqliteStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DeviceBindingDatabaseTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: SqliteStore

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LocalDatabase.Schema.create(driver)
        store = SqliteStore(LocalDatabase(driver))
    }

    @After
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `upsert keeps one current device for each user`() {
        store.deviceBinding.upsert(
            userId = "user-1",
            deviceId = "AA:BB",
            updatedAtMillis = 10L
        )
        store.deviceBinding.upsert(
            userId = "user-1",
            deviceId = "CC:DD",
            updatedAtMillis = 20L
        )

        val binding = store.deviceBinding
            .findByUserId("user-1")
            .executeAsOne()

        assertEquals("CC:DD", binding.deviceId)
        assertEquals(20L, binding.updatedAtMillis)
    }

    @Test
    fun `bindings are isolated by user and can be forgotten`() {
        store.deviceBinding.upsert("user-1", "AA:BB", 10L)
        store.deviceBinding.upsert("user-2", "CC:DD", 20L)

        store.deviceBinding.deleteByUserId("user-1")

        assertNull(
            store.deviceBinding
                .findByUserId("user-1")
                .executeAsOneOrNull()
        )
        assertEquals(
            "CC:DD",
            store.deviceBinding
                .findByUserId("user-2")
                .executeAsOne()
                .deviceId
        )
    }
}
