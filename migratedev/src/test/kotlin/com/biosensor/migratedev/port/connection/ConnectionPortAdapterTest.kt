package com.biosensor.migratedev.port.connection

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.biosensor.migratedev.database.LocalDatabase
import com.biosensor.migratedev.decisioncore.connection.ConnectionEffect
import com.biosensor.migratedev.decisioncore.connection.ConnectionEvent
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothCommand
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothPort
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ConnectionPortAdapterTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: LocalDatabase
    private lateinit var bluetooth: FakeBluetoothPort
    private lateinit var port: ConnectionPortAdapter

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LocalDatabase.Schema.create(driver)
        database = LocalDatabase(driver)
        bluetooth = FakeBluetoothPort()
        port = ConnectionPortAdapter(
            sqlite = database,
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
            listOf(BindingSnapshot.Missing),
            port.readBinding("user-1").toList()
        )

        assertEquals(
            listOf(ConnectionEvent.BindingSaved("AA:01")),
            port.execute(
                ConnectionEffect.SaveBinding(
                    userId = "user-1",
                    deviceId = "AA:01"
                )
            ).toList()
        )
        assertEquals(
            listOf(BindingSnapshot.Found("AA:01")),
            port.readBinding("user-1").toList()
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
    fun `connect device is wrapped into bluetooth command and mapped to event`() =
        runTest {
            bluetooth.results = flowOf(
                BluetoothResult.Connected(
                    BluetoothDeviceInfo(
                        id = "AA:01",
                        name = "BT24-S",
                        isBle = true
                    )
                )
            )

            assertEquals(
                listOf(
                    ConnectionEvent.DeviceConnected(
                        BluetoothDeviceInfo(
                            id = "AA:01",
                            name = "BT24-S",
                            isBle = true
                        )
                    )
                ),
                port.execute(
                    ConnectionEffect.ConnectDevice("AA:01")
                ).toList()
            )
            assertEquals(BluetoothCommand.Connect("AA:01"), bluetooth.lastCommand)
        }

    @Test
    fun `disconnect device is wrapped into bluetooth command`() =
        runTest {
            bluetooth.results = flowOf(BluetoothResult.Disconnected)

            assertEquals(
                listOf(ConnectionEvent.DeviceDisconnected),
                port.execute(ConnectionEffect.DisconnectDevice).toList()
            )
            assertEquals(BluetoothCommand.Disconnect, bluetooth.lastCommand)
        }

    @Test
    fun `scan device stream is passed through unchanged`() =
        runTest {
            val device = BluetoothDeviceInfo(
                id = "AA:01",
                name = "BT24-S",
                isBle = true
            )
            bluetooth.devices = flowOf(device)

            assertEquals(
                listOf(device),
                port.scanDevices().toList()
            )
        }

    private class FakeBluetoothPort : BluetoothPort {
        var lastCommand: BluetoothCommand? = null
        var results: Flow<BluetoothResult> = flowOf()
        var devices: Flow<BluetoothDeviceInfo> = flowOf()

        override fun scanDevices(): Flow<BluetoothDeviceInfo> = devices

        override fun execute(command: BluetoothCommand): Flow<BluetoothResult> {
            lastCommand = command
            return results
        }
    }
}
