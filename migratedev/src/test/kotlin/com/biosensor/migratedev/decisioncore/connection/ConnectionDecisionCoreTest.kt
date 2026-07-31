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
    fun `access starts binding lookup without scan effect`() {
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
        assertEquals(
            listOf(ConnectionEffect.ReadBinding("user-1")),
            granted.effects
        )
    }

    @Test
    fun `binding then device triggers auto connect once`() {
        val withBinding = reduce(
            scanning(),
            ConnectionEvent.BindingLoaded(device.id)
        )
        val target = reduce(
            withBinding.newState,
            ConnectionEvent.DevicesUpdated(
                listOf(device)
            )
        )

        assertEquals(
            ConnectionState.Connecting(
                userId = "user-1",
                deviceId = device.id,
                devices = listOf(device),
                binding = BindingLookup.Found(device.id),
                source = ConnectionSource.Automatic
            ),
            target.newState
        )
        assertEquals(
            listOf(
                ConnectionEffect.ConnectDevice(device.id)
            ),
            target.effects
        )

        val duplicate = reduce(
            target.newState,
            ConnectionEvent.DevicesUpdated(
                listOf(device)
            )
        )
        assertEquals(target.newState, duplicate.newState)
        assertEquals(
            emptyList<ConnectionEffect>(),
            duplicate.effects
        )
    }

    @Test
    fun `device then binding also triggers auto connect`() {
        val withDevice = reduce(
            scanning(),
            ConnectionEvent.DevicesUpdated(
                listOf(device)
            )
        )
        val result = reduce(
            withDevice.newState,
            ConnectionEvent.BindingLoaded(device.id)
        )

        assertEquals(
            listOf(
                ConnectionEffect.ConnectDevice(device.id)
            ),
            result.effects
        )
        assertEquals(
            ConnectionSource.Automatic,
            (result.newState as ConnectionState.Connecting)
                .source
        )
    }

    @Test
    fun `missing binding still allows manual connect`() {
        val afterBinding = reduce(
            scanning(),
            ConnectionEvent.BindingMissing
        )
        val afterDevices = reduce(
            afterBinding.newState,
            ConnectionEvent.DevicesUpdated(
                listOf(device)
            )
        )
        val selected = reduce(
            afterDevices.newState,
            ConnectionEvent.DeviceSelected(device.id)
        )

        assertEquals(
            listOf(
                ConnectionEffect.ConnectDevice(device.id)
            ),
            selected.effects
        )
        assertEquals(
            ConnectionSource.Manual,
            (selected.newState as ConnectionState.Connecting)
                .source
        )
    }

    @Test
    fun `refresh changes no scan resource state`() {
        val failedScan = reduce(
            scanning().copy(devices = listOf(device)),
            ConnectionEvent.ScanFailed("scanner failed")
        )
        val refreshed = reduce(
            failedScan.newState,
            ConnectionEvent.RefreshRequested
        )

        assertEquals(
            scanning(),
            refreshed.newState
        )
        assertEquals(
            emptyList<ConnectionEffect>(),
            refreshed.effects
        )
    }

    @Test
    fun `only successful manual connection saves binding`() {
        val manual = ConnectionState.Connecting(
            userId = "user-1",
            deviceId = device.id,
            devices = listOf(device),
            binding = BindingLookup.Missing,
            source = ConnectionSource.Manual
        )
        val automatic = manual.copy(
            source = ConnectionSource.Automatic
        )

        assertEquals(
            listOf(
                ConnectionEffect.SaveBinding(
                    "user-1",
                    device.id
                )
            ),
            reduce(
                manual,
                ConnectionEvent.DeviceConnected(device)
            ).effects
        )
        assertEquals(
            emptyList<ConnectionEffect>(),
            reduce(
                automatic,
                ConnectionEvent.DeviceConnected(device)
            ).effects
        )
    }

    @Test
    fun `failed automatic connection does not retry after refresh`() {
        val connecting = ConnectionState.Connecting(
            userId = "user-1",
            deviceId = device.id,
            devices = listOf(device),
            binding = BindingLookup.Found(device.id),
            source = ConnectionSource.Automatic
        )
        val failed = reduce(
            connecting,
            ConnectionEvent.DeviceConnectFailed("timeout")
        )
        val refreshed = reduce(
            failed.newState,
            ConnectionEvent.RefreshRequested
        )
        val devicesAgain = reduce(
            refreshed.newState,
            ConnectionEvent.DevicesUpdated(
                listOf(device)
            )
        )

        assertEquals(
            emptyList<ConnectionEffect>(),
            devicesAgain.effects
        )
        assertEquals(
            true,
            (devicesAgain.newState as ConnectionState.Scanning)
                .autoConnectAttempted
        )
    }

    @Test
    fun `logout only waits when connection must disconnect`() {
        val scanningLogout = reduce(
            scanning(),
            ConnectionEvent.LogoutRequested
        )
        assertEquals(
            ConnectionState.LogoutReady,
            scanningLogout.newState
        )
        assertEquals(
            emptyList<ConnectionEffect>(),
            scanningLogout.effects
        )

        val connected = ConnectionState.Connected(
            userId = "user-1",
            device = device,
            bindingMessage = null
        )
        val ending = reduce(
            connected,
            ConnectionEvent.LogoutRequested
        )
        assertEquals(
            ConnectionState.EndingSession,
            ending.newState
        )
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

    private fun scanning() =
        ConnectionState.Scanning(
            userId = "user-1",
            devices = emptyList(),
            binding = BindingLookup.Loading,
            autoConnectAttempted = false,
            message = null
        )

    private fun reduce(
        state: ConnectionState,
        event: ConnectionEvent
    ) = ConnectionDecisionCore.reduce(state, event)
}
