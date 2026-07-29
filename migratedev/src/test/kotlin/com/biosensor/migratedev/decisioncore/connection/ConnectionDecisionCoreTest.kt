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
    fun `creation waits for Android access before starting work`() {
        val created = reduce(
            ConnectionState.Idle,
            ConnectionEvent.ConnectionCreated("user-1")
        )
        assertEquals(
            ConnectionState.AwaitingBluetoothAccess("user-1"),
            created.newState
        )
        assertEquals(emptyList<ConnectionEffect>(), created.effects)

        val granted = reduce(
            created.newState,
            ConnectionEvent.BluetoothAccessGranted("scan-1")
        )
        assertEquals(
            scanning(),
            granted.newState
        )
        assertEquals(
            listOf(
                ConnectionEffect.ReadBinding("user-1"),
                ConnectionEffect.StartScan("scan-1")
            ),
            granted.effects
        )
    }

    @Test
    fun `binding then device triggers auto connect exactly once`() {
        val withBinding = reduce(
            scanning(),
            ConnectionEvent.BindingLoaded(device.id)
        )
        val connectedTarget = reduce(
            withBinding.newState,
            ConnectionEvent.DevicesUpdated(
                "scan-1",
                1,
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
            connectedTarget.newState
        )
        assertEquals(
            listOf(ConnectionEffect.ConnectDevice(device.id)),
            connectedTarget.effects
        )

        val duplicate = reduce(
            connectedTarget.newState,
            ConnectionEvent.DevicesUpdated(
                "scan-1",
                1,
                listOf(device)
            )
        )
        assertEquals(connectedTarget.newState, duplicate.newState)
        assertEquals(emptyList<ConnectionEffect>(), duplicate.effects)
    }

    @Test
    fun `device then binding also triggers auto connect`() {
        val withDevice = reduce(
            scanning(),
            ConnectionEvent.DevicesUpdated(
                "scan-1",
                1,
                listOf(device)
            )
        )
        val result = reduce(
            withDevice.newState,
            ConnectionEvent.BindingLoaded(device.id)
        )

        assertEquals(
            listOf(ConnectionEffect.ConnectDevice(device.id)),
            result.effects
        )
        assertEquals(
            ConnectionSource.Automatic,
            (result.newState as ConnectionState.Connecting).source
        )
    }

    @Test
    fun `missing or failed binding does not stop scan or manual connect`() {
        listOf(
            ConnectionEvent.BindingMissing,
            ConnectionEvent.BindingFailed("database unavailable")
        ).forEach { bindingEvent ->
            val afterBinding = reduce(scanning(), bindingEvent)
            val afterDevices = reduce(
                afterBinding.newState,
                ConnectionEvent.DevicesUpdated(
                    "scan-1",
                    1,
                    listOf(device)
                )
            )
            assertEquals(
                emptyList<ConnectionEffect>(),
                afterDevices.effects
            )

            val selected = reduce(
                afterDevices.newState,
                ConnectionEvent.DeviceSelected(device.id)
            )
            assertEquals(
                listOf(ConnectionEffect.ConnectDevice(device.id)),
                selected.effects
            )
            assertEquals(
                ConnectionSource.Manual,
                (selected.newState as ConnectionState.Connecting).source
            )
        }
    }

    @Test
    fun `device updates keep the state scanning and refresh uses one command`() {
        val updated = reduce(
            scanning(),
            ConnectionEvent.DevicesUpdated(
                "scan-1",
                3,
                listOf(device)
            )
        )
        assertEquals(
            ScanProgress.Active(3),
            (updated.newState as ConnectionState.Scanning).progress
        )

        val refreshed = reduce(
            updated.newState,
            ConnectionEvent.RefreshRequested("scan-2")
        )
        assertEquals(
            ScanProgress.Refreshing(3),
            (refreshed.newState as ConnectionState.Scanning).progress
        )
        assertEquals(
            listOf(ConnectionEffect.RefreshScan("scan-1")),
            refreshed.effects
        )

        val duplicateRefresh = reduce(
            refreshed.newState,
            ConnectionEvent.RefreshRequested("scan-3")
        )
        assertEquals(refreshed.newState, duplicateRefresh.newState)
        assertEquals(
            emptyList<ConnectionEffect>(),
            duplicateRefresh.effects
        )
    }

    @Test
    fun `only a successful manual connection saves binding`() {
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
            ConnectionEvent.RefreshRequested("scan-2")
        )
        val devicesAgain = reduce(
            refreshed.newState,
            ConnectionEvent.DevicesUpdated(
                "scan-2",
                1,
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
    fun `visibility stops and resumes scan while connected remains connected`() {
        val hidden = reduce(
            scanning().copy(
                progress = ScanProgress.Active(2)
            ),
            ConnectionEvent.BecameHidden
        )
        assertEquals(
            ScanProgress.Stopping(),
            (hidden.newState as ConnectionState.Scanning).progress
        )
        assertEquals(
            listOf(ConnectionEffect.StopScan("scan-1")),
            hidden.effects
        )

        val resumeRequested = reduce(
            hidden.newState,
            ConnectionEvent.BecameVisible("scan-2")
        )
        assertEquals(
            ScanProgress.Stopping("scan-2"),
            (resumeRequested.newState as ConnectionState.Scanning).progress
        )
        assertEquals(
            emptyList<ConnectionEffect>(),
            resumeRequested.effects
        )

        val visible = reduce(
            resumeRequested.newState,
            ConnectionEvent.ScanStopped("scan-1")
        )
        assertEquals(
            listOf(ConnectionEffect.StartScan("scan-2")),
            visible.effects
        )

        val connected = ConnectionState.Connected(
            userId = "user-1",
            device = device,
            bindingMessage = null
        )
        assertEquals(
            connected,
            reduce(
                connected,
                ConnectionEvent.BecameHidden
            ).newState
        )
    }

    @Test
    fun `logout releases owned resource before becoming ready`() {
        val ending = reduce(
            scanning(),
            ConnectionEvent.LogoutRequested
        )
        assertEquals(
            ConnectionState.EndingSession(
                pending = setOf(SessionResource.Scan)
            ),
            ending.newState
        )
        assertEquals(
            listOf(ConnectionEffect.StopScan("scan-1")),
            ending.effects
        )

        assertEquals(
            ConnectionState.LogoutReady,
            reduce(
                ending.newState,
                ConnectionEvent.ScanStopped("scan-1")
            ).newState
        )
    }

    @Test
    fun `logout while scan is already stopping does not stop twice`() {
        val stopping = scanning().copy(
            progress = ScanProgress.Stopping(),
            isVisible = false
        )

        val ending = reduce(
            stopping,
            ConnectionEvent.LogoutRequested
        )

        assertEquals(
            ConnectionState.EndingSession(
                pending = setOf(SessionResource.Scan)
            ),
            ending.newState
        )
        assertEquals(emptyList<ConnectionEffect>(), ending.effects)
    }

    private fun scanning() = ConnectionState.Scanning(
        userId = "user-1",
        scanSessionId = "scan-1",
        devices = emptyList(),
        binding = BindingLookup.Loading,
        progress = ScanProgress.Starting,
        autoConnectAttempted = false,
        message = null,
        isVisible = true
    )

    private fun reduce(
        state: ConnectionState,
        event: ConnectionEvent
    ) = ConnectionDecisionCore.reduce(state, event)
}
