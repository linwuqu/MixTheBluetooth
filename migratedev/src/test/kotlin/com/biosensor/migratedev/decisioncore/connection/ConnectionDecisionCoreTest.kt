package com.biosensor.migratedev.decisioncore.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionDecisionCoreTest {

    private val device = BluetoothDeviceInfo(
        id = "AA:BB:CC:DD:EE:FF",
        name = "CGM",
        isBle = true,
        rssi = -40
    )

    @Test
    fun scanFindsDevicesAndSelectionStartsConnection() {
        val scanning = ConnectionDecisionCore.reduce(
            ConnectionState.Idle,
            ConnectionEvent.StartScan
        )
        assertEquals(ConnectionState.Scanning, scanning.newState)
        assertEquals(
            listOf(ConnectionEffect.StartScan),
            scanning.effects
        )

        val found = ConnectionDecisionCore.reduce(
            scanning.newState,
            ConnectionEvent.DevicesFound(listOf(device))
        )
        assertEquals(ConnectionState.DeviceFound(listOf(device)), found.newState)

        val connecting = ConnectionDecisionCore.reduce(
            found.newState,
            ConnectionEvent.SelectDevice(device.id)
        )
        assertEquals(ConnectionState.Connecting(device.id), connecting.newState)
        assertEquals(
            listOf(ConnectionEffect.ConnectDevice(device.id)),
            connecting.effects
        )
    }

    @Test
    fun connectionFailureReturnsToError() {
        val result = ConnectionDecisionCore.reduce(
            ConnectionState.Connecting(device.id),
            ConnectionEvent.ConnectFailed("connection refused")
        )

        assertEquals(ConnectionState.Error("connection refused"), result.newState)
        assertEquals(emptyList<ConnectionEffect>(), result.effects)
    }

    @Test
    fun disconnectStopsTheConnectedWorkflow() {
        val result = ConnectionDecisionCore.reduce(
            ConnectionState.Connected(device),
            ConnectionEvent.Disconnect
        )

        assertEquals(ConnectionState.Idle, result.newState)
        assertEquals(listOf(ConnectionEffect.DisconnectDevice), result.effects)
    }
}
