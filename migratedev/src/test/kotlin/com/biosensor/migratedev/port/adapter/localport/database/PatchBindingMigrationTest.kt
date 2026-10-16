package com.biosensor.migratedev.port.adapter.localport.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.biosensor.migratedev.database.LocalDatabase
import com.biosensor.migratedev.port.adapter.localport.sql.SqliteStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * v1 → v2 迁移的守门员(架构 13 §4.4)。
 *
 * 这个坑最容易漏,因为**新装机器一切正常**:`.sq` 里加了表,`Schema.create` 就建得出来。
 * 只有老库升级时才走 `migrate()`,而没有 `.sqm` 文件时 `Schema.version` 一直是 1、
 * `migrate()` 是空实现 → 表永远不会建 → 用户一扫码就崩。
 *
 * 这里手工造一个「只有 deviceBinding 的 v1 老库」,再走一次真实的迁移路径。
 */
class PatchBindingMigrationTest {
    private lateinit var driver: JdbcSqliteDriver

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // v1 装出来的样子:deviceBinding.sq 是当时唯一的表
        driver.execute(
            null,
            """
            CREATE TABLE deviceBinding (
                userId TEXT NOT NULL PRIMARY KEY,
                deviceId TEXT NOT NULL,
                updatedAtMillis INTEGER NOT NULL
            )
            """.trimIndent(),
            0
        )
        driver.execute(
            null,
            "INSERT INTO deviceBinding(userId, deviceId, updatedAtMillis) " +
                "VALUES ('user-1', 'AA:01', 10)",
            0
        )
        driver.execute(null, "PRAGMA user_version = 1", 0)
    }

    @After
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `the schema knows about version two`() {
        assertEquals(
            "版本号由 .sqm 文件数 + 1 决定:没有 1.sqm 就还是 1,migrate 也就不会被触发",
            2L, LocalDatabase.Schema.version
        )
    }

    @Test
    fun `migrating from v1 creates a writable patchBinding table`() {
        LocalDatabase.Schema.migrate(driver, 1, 2).value

        val store = SqliteStore(LocalDatabase(driver))
        store.patchBinding.upsert(
            userId = "user-1", deviceId = "AA:01", patchId = "patch-a",
            modelId = 1L, lotNo = "DEMO-260701", manufacturedOn = null, expiresOn = null,
            maxUseMinutes = 120L, calibrationRef = null, rawCode = "raw",
            boundAtMillis = 99L
        )

        val rows = store.patchBinding.selectByDevice("user-1", "AA:01").executeAsList()
        assertEquals(1, rows.size)
        assertEquals("patch-a", rows.single().patchId)
        assertEquals(99L, rows.single().boundAtMillis)
    }

    @Test
    fun `migrating keeps the existing device bindings`() {
        LocalDatabase.Schema.migrate(driver, 1, 2).value

        val store = SqliteStore(LocalDatabase(driver))
        val binding = store.deviceBinding.findByUserId("user-1").executeAsOne()

        assertEquals("AA:01", binding.deviceId)
        assertEquals(10L, binding.updatedAtMillis)
    }

    @Test
    fun `a fresh install and an upgrade end up with the same schema`() {
        // 新装:Schema.create 直接把两条 DDL 都跑了
        val freshDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            LocalDatabase.Schema.create(freshDriver)
            val freshTables = freshDriver.tableNames()

            // 升级:老库跑迁移
            LocalDatabase.Schema.migrate(driver, 1, 2).value
            val upgradedTables = driver.tableNames()

            assertEquals(freshTables, upgradedTables)
        } finally {
            freshDriver.close()
        }
    }

    /** 只看业务表,忽略 SQLDelight / SQLite 自己的东西。 */
    private fun JdbcSqliteDriver.tableNames(): List<String> =
        executeQuery(
            null,
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' ORDER BY name",
            { cursor ->
                app.cash.sqldelight.db.QueryResult.Value(
                    buildList {
                        while (cursor.next().value) add(cursor.getString(0).orEmpty())
                    }
                )
            },
            0
        ).value
}
