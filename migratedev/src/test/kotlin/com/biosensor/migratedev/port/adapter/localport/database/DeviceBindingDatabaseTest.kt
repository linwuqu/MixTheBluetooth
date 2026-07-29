package com.biosensor.migratedev.port.adapter.localport.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.biosensor.migratedev.database.LocalDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DeviceBindingDatabaseTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: LocalDatabase

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LocalDatabase.Schema.create(driver)
        database = LocalDatabase(driver)
    }

    @After
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `upsert keeps one current device for each user`() {
        database.deviceBindingQueries.upsert(
            userId = "user-1",
            deviceId = "AA:BB",
            updatedAtMillis = 10L
        )
        database.deviceBindingQueries.upsert(
            userId = "user-1",
            deviceId = "CC:DD",
            updatedAtMillis = 20L
        )

        val binding = database.deviceBindingQueries
            .findByUserId("user-1")
            .executeAsOne()

        assertEquals("CC:DD", binding.deviceId)
        assertEquals(20L, binding.updatedAtMillis)
    }

    @Test
    fun `bindings are isolated by user and can be forgotten`() {
        database.deviceBindingQueries.upsert("user-1", "AA:BB", 10L)
        database.deviceBindingQueries.upsert("user-2", "CC:DD", 20L)

        database.deviceBindingQueries.deleteByUserId("user-1")

        assertNull(
            database.deviceBindingQueries
                .findByUserId("user-1")
                .executeAsOneOrNull()
        )
        assertEquals(
            "CC:DD",
            database.deviceBindingQueries
                .findByUserId("user-2")
                .executeAsOne()
                .deviceId
        )
    }
}
