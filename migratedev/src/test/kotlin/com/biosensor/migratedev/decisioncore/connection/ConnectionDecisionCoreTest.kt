package com.biosensor.migratedev.decisioncore.connection

import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionDecisionCoreTest {
    private val device = BluetoothDeviceInfo(
        id = "AA:BB:CC:DD:EE:FF",
        name = "BT24-S",
        isBle = true,
        rssi = -40
    )

    @Test
    fun `access enters scanning without binding effect`() {
        val created = reduce(
            ConnectionState.Idle,
            ConnectionEvent.ConnectionCreated("user-1")
        )
        assertEquals(
            ConnectionState.AwaitingBluetoothAccess("user-1"),
            created.newState
        )

        val granted = reduce(
            created.newState,
            ConnectionEvent.BluetoothAccessGranted
        )

        assertEquals(scanning(), granted.newState)
        assertEquals(emptyList<ConnectionEffect>(), granted.effects)
    }

    @Test
    fun `connect request uses one business transition`() {
        val result = reduce(
            scanning(),
            ConnectionEvent.ConnectRequested(device.id)
        )

        assertEquals(
            ConnectionState.Connecting("user-1", device.id),
            result.newState
        )
        assertEquals(
            listOf(ConnectionEffect.ConnectDevice(device.id)),
            result.effects
        )
    }

    @Test
    fun `failed connection can be selected again`() {
        val failed = ConnectionState.ConnectionFailed(
            userId = "user-1",
            deviceId = device.id,
            message = "timeout"
        )

        val result = reduce(
            failed,
            ConnectionEvent.ConnectRequested(device.id)
        )

        assertEquals(
            ConnectionState.Connecting("user-1", device.id),
            result.newState
        )
        assertEquals(
            listOf(ConnectionEffect.ConnectDevice(device.id)),
            result.effects
        )
    }

    @Test
    fun `every successful connection saves binding`() {
        val result = reduce(
            ConnectionState.Connecting("user-1", device.id),
            ConnectionEvent.DeviceConnected(device)
        )

        assertEquals(
            ConnectionState.Connected(
                userId = "user-1",
                device = device,
                bindingMessage = null
            ),
            result.newState
        )
        assertEquals(
            listOf(ConnectionEffect.SaveBinding("user-1", device.id)),
            result.effects
        )
    }

    @Test
    fun `refresh clears scan failure and returns connection failure to scanning`() {
        val scanFailure = reduce(
            scanning(),
            ConnectionEvent.ScanFailed("scanner failed")
        )
        assertEquals(
            scanning().copy(message = "scanner failed"),
            scanFailure.newState
        )
        assertEquals(
            scanning(),
            reduce(
                scanFailure.newState,
                ConnectionEvent.RefreshRequested
            ).newState
        )

        val connectionFailure = ConnectionState.ConnectionFailed(
            userId = "user-1",
            deviceId = device.id,
            message = "timeout"
        )
        assertEquals(
            scanning(),
            reduce(
                connectionFailure,
                ConnectionEvent.RefreshRequested
            ).newState
        )
    }

    @Test
    fun `disconnect while connecting fails immediately instead of hanging`() {
        val result = reduce(
            ConnectionState.Connecting("user-1", device.id),
            ConnectionEvent.DeviceDisconnected
        )
        // 连接/重连过程中再断线:立即失败(否则要等 DeviceConnectTimeout 才收尾)
        assertEquals(
            ConnectionState.ConnectionFailed(
                userId = "user-1", deviceId = device.id, message = "连接过程中设备断开"
            ),
            result.newState
        )
        assertEquals(emptyList<ConnectionEffect>(), result.effects)
    }

    @Test
    fun `disconnect while scanning stays scanning`() {
        val result = reduce(scanning(), ConnectionEvent.DeviceDisconnected)
        assertEquals(scanning(), result.newState)
        assertEquals(emptyList<ConnectionEffect>(), result.effects)
    }

    @Test
    fun `logout only waits when connection must disconnect`() {
        val scanningLogout = reduce(
            scanning(),
            ConnectionEvent.LogoutRequested
        )
        assertEquals(ConnectionState.LogoutReady, scanningLogout.newState)
        assertEquals(emptyList<ConnectionEffect>(), scanningLogout.effects)

        val connected = ConnectionState.Connected(
            userId = "user-1",
            device = device,
            bindingMessage = null
        )
        val ending = reduce(
            connected,
            ConnectionEvent.LogoutRequested
        )
        assertEquals(ConnectionState.EndingSession, ending.newState)
        assertEquals(
            listOf(ConnectionEffect.DisconnectDevice),
            ending.effects
        )
        assertEquals(
            ConnectionState.LogoutReady,
            reduce(
                ending.newState,
                ConnectionEvent.DeviceDisconnected
            ).newState
        )
    }

    private fun scanning() = ConnectionState.Scanning(
        userId = "user-1",
        message = null
    )

    private fun reduce(
        state: ConnectionState,
        event: ConnectionEvent
    ) = ConnectionDecisionCore.reduce(state, event)
}
