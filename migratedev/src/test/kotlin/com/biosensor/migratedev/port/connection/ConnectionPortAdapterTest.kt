package com.biosensor.migratedev.port.connection

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.biosensor.migratedev.database.LocalDatabase
import com.biosensor.migratedev.decisioncore.connection.ConnectionEffect
import com.biosensor.migratedev.decisioncore.connection.ConnectionEvent
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothEffect
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothEvent
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothPort
import com.biosensor.migratedev.port.adapter.localport.sql.SqliteStore
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
    private lateinit var sql: SqliteStore
    private lateinit var bluetooth: FakeBluetoothPort
    private lateinit var port: ConnectionPortAdapter

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LocalDatabase.Schema.create(driver)
        sql = SqliteStore(LocalDatabase(driver))
        bluetooth = FakeBluetoothPort()
        port = ConnectionPortAdapter(
            sql = sql,
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
            sql.deviceBinding
                .findByUserId("user-1")
                .executeAsOne()
                .updatedAtMillis
        )
    }

    @Test
    fun `connect device is wrapped into bluetooth command and mapped to event`() =
        runTest {
            bluetooth.results = flowOf(
                BluetoothEvent.Connected(
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
            assertEquals(BluetoothEffect.Connect("AA:01"), bluetooth.lastEffect)
        }

    @Test
    fun `disconnect device is wrapped into bluetooth command`() =
        runTest {
            bluetooth.results = flowOf(BluetoothEvent.Disconnected)

            assertEquals(
                listOf(ConnectionEvent.DeviceDisconnected),
                port.execute(ConnectionEffect.DisconnectDevice).toList()
            )
            assertEquals(BluetoothEffect.Disconnect, bluetooth.lastEffect)
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
        var lastEffect: BluetoothEffect? = null
        var results: Flow<BluetoothEvent> = flowOf()
        var devices: Flow<BluetoothDeviceInfo> = flowOf()

        override fun scanDevices(): Flow<BluetoothDeviceInfo> = devices

        override fun execute(effect: BluetoothEffect): Flow<BluetoothEvent> {
            lastEffect = effect
            return results
        }
    }
}
