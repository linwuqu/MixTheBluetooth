package com.biosensor.migratedev.port.connection

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.biosensor.migratedev.database.LocalDatabase
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothCommand
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothPort
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothResult
import com.biosensor.migratedev.port.adapter.localport.FileDeleteResult
import com.biosensor.migratedev.port.adapter.localport.FileEntry
import com.biosensor.migratedev.port.adapter.localport.FilePruneResult
import com.biosensor.migratedev.port.adapter.localport.FileSpace
import com.biosensor.migratedev.port.adapter.localport.LocalFileClient
import com.biosensor.migratedev.port.adapter.localport.LocalPort
import com.biosensor.migratedev.port.adapter.localport.RetentionPolicy
import com.biosensor.migratedev.port.adapter.localport.StringEntropy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.Sink
import okio.Source
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DefaultConnectionPortTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: LocalDatabase
    private lateinit var bluetooth: FakeBluetoothPort
    private lateinit var port: DefaultConnectionPort

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LocalDatabase.Schema.create(driver)
        database = LocalDatabase(driver)
        bluetooth = FakeBluetoothPort()
        port = DefaultConnectionPort(
            local = object : LocalPort {
                override val entropy: StringEntropy
                    get() = error("not used")
                override val sqlite: LocalDatabase = database
                override val files: LocalFileClient
                    get() = UnusedFiles
            },
            bluetooth = bluetooth,
            nowMillis = { 1234L }
        )
    }

    @After
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `read and save binding are routed to sqlite`() = runTest {
        assertEquals(
            listOf(ConnectionResult.BindingMissing),
            port.execute(
                ConnectionCommand.ReadBinding("user-1")
            ).toList()
        )

        assertEquals(
            listOf(ConnectionResult.BindingSaved("AA:01")),
            port.execute(
                ConnectionCommand.SaveBinding(
                    userId = "user-1",
                    deviceId = "AA:01"
                )
            ).toList()
        )
        assertEquals(
            listOf(ConnectionResult.BindingLoaded("AA:01")),
            port.execute(
                ConnectionCommand.ReadBinding("user-1")
            ).toList()
        )
        assertEquals(
            1234L,
            database.deviceBindingQueries
                .findByUserId("user-1")
                .executeAsOne()
                .updatedAtMillis
        )
    }

    @Test
    fun `bluetooth command and stream are passed through unchanged`() =
        runTest {
            val command = BluetoothCommand.StartScan("scan-1")
            bluetooth.results = flowOf(
                BluetoothResult.ScanStarted("scan-1", 1)
            )

            assertEquals(
                listOf(
                    ConnectionResult.Bluetooth(
                        BluetoothResult.ScanStarted("scan-1", 1)
                    )
                ),
                port.execute(
                    ConnectionCommand.Bluetooth(command)
                ).toList()
            )
            assertEquals(command, bluetooth.lastCommand)
        }

    private class FakeBluetoothPort : BluetoothPort {
        var lastCommand: BluetoothCommand? = null
        var results: Flow<BluetoothResult> = flowOf()

        override fun execute(
            command: BluetoothCommand
        ): Flow<BluetoothResult> {
            lastCommand = command
            return results
        }
    }

    private object UnusedFiles : LocalFileClient {
        override fun source(
            space: FileSpace,
            relativePath: String
        ): Source = error("not used")

        override fun sink(
            space: FileSpace,
            relativePath: String,
            append: Boolean
        ): Sink = error("not used")

        override fun list(
            space: FileSpace,
            relativePath: String
        ): List<FileEntry> = error("not used")

        override fun delete(
            space: FileSpace,
            relativePath: String
        ): FileDeleteResult = error("not used")

        override fun prune(
            space: FileSpace,
            policy: RetentionPolicy
        ): FilePruneResult = error("not used")
    }
}
