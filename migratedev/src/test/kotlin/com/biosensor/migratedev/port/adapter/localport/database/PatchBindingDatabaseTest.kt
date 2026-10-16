package com.biosensor.migratedev.port.adapter.localport.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.biosensor.migratedev.database.LocalDatabase
import com.biosensor.migratedev.port.adapter.localport.sql.SqliteStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** 贴片绑定的落库规矩:1:n 成立、同片重扫不增行、按账号与设备各算各的。 */
class PatchBindingDatabaseTest {
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

    private fun bind(
        userId: String = "user-1",
        deviceId: String = "AA:01",
        patchId: String,
        lotNo: String? = null,
        boundAtMillis: Long = 0L,
    ) = store.patchBinding.upsert(
        userId = userId, deviceId = deviceId, patchId = patchId,
        modelId = 1L, lotNo = lotNo, manufacturedOn = null, expiresOn = null,
        maxUseMinutes = null, calibrationRef = null, rawCode = "raw-$patchId",
        boundAtMillis = boundAtMillis
    )

    private fun patchIds(userId: String = "user-1", deviceId: String = "AA:01") =
        store.patchBinding.selectByDevice(userId, deviceId).executeAsList().map { it.patchId }

    @Test
    fun `one device binds many patches newest first`() {
        bind(patchId = "patch-a", boundAtMillis = 10L)
        bind(patchId = "patch-b", boundAtMillis = 20L)
        bind(patchId = "patch-c", boundAtMillis = 30L)

        assertEquals(listOf("patch-c", "patch-b", "patch-a"), patchIds())
    }

    @Test
    fun `rescanning the same patch refreshes the time without adding a row`() {
        bind(patchId = "patch-a", boundAtMillis = 10L)
        bind(patchId = "patch-b", boundAtMillis = 20L)

        bind(patchId = "patch-a", boundAtMillis = 99L)

        assertEquals("同片重扫不该长行", listOf("patch-a", "patch-b"), patchIds())
        val row = store.patchBinding.selectByDevice("user-1", "AA:01")
            .executeAsList().first { it.patchId == "patch-a" }
        assertEquals(99L, row.boundAtMillis)
    }

    @Test
    fun `rescanning updates the payload fields as well`() {
        bind(patchId = "patch-a", lotNo = "DEMO-260701", boundAtMillis = 10L)

        bind(patchId = "patch-a", lotNo = "DEMO-260799", boundAtMillis = 20L)

        assertEquals(
            "DEMO-260799",
            store.patchBinding.selectByDevice("user-1", "AA:01").executeAsOne().lotNo
        )
    }

    @Test
    fun `bindings are isolated by user`() {
        bind(userId = "user-1", patchId = "patch-a")
        bind(userId = "user-2", patchId = "patch-b")

        assertEquals(listOf("patch-a"), patchIds(userId = "user-1"))
        assertEquals(listOf("patch-b"), patchIds(userId = "user-2"))
    }

    @Test
    fun `bindings are isolated by device`() {
        bind(deviceId = "AA:01", patchId = "patch-a")
        bind(deviceId = "BB:02", patchId = "patch-b")

        assertEquals(listOf("patch-a"), patchIds(deviceId = "AA:01"))
        assertEquals(listOf("patch-b"), patchIds(deviceId = "BB:02"))
        assertEquals(emptyList<String>(), patchIds(deviceId = "CC:03"))
    }

    @Test
    fun `same patch on two devices counts twice`() {
        bind(deviceId = "AA:01", patchId = "patch-a")
        bind(deviceId = "BB:02", patchId = "patch-a")

        assertEquals(listOf("patch-a"), patchIds(deviceId = "AA:01"))
        assertEquals(listOf("patch-a"), patchIds(deviceId = "BB:02"))
    }

    @Test
    fun `the raw code is kept for later protocols`() {
        val raw = "CGPATCH/1|patchId=patch-a|futureField=xyz"
        store.patchBinding.upsert(
            userId = "user-1", deviceId = "AA:01", patchId = "patch-a",
            modelId = null, lotNo = null, manufacturedOn = null, expiresOn = null,
            maxUseMinutes = null, calibrationRef = null, rawCode = raw,
            boundAtMillis = 1L
        )

        assertEquals(
            raw,
            store.patchBinding.selectByDevice("user-1", "AA:01").executeAsOne().rawCode
        )
    }

    @Test
    fun `deleting a device forgets its patches only`() {
        bind(userId = "user-1", patchId = "patch-a")
        bind(userId = "user-1", deviceId = "BB:02", patchId = "patch-b")

        store.patchBinding.deleteByDevice("user-1", "AA:01")

        assertEquals(emptyList<String>(), patchIds(deviceId = "AA:01"))
        assertEquals(listOf("patch-b"), patchIds(deviceId = "BB:02"))
    }
}
