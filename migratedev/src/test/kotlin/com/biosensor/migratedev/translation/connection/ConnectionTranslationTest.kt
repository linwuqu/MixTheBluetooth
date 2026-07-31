package com.biosensor.migratedev.translation.connection

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import com.biosensor.migratedev.port.connection.ConnectionCommand
import com.biosensor.migratedev.port.connection.ConnectionPort
import com.biosensor.migratedev.port.connection.ConnectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionTranslationTest {

    @Test
    fun `logout reports request before resources are stopped`() =
        runTest {
            Dispatchers.setMain(
                StandardTestDispatcher(testScheduler)
            )
            val store = ViewModelStore()
            val outputs = mutableListOf<ConnectionOutput>()
            val port = RecordingConnectionPort()
            try {
                val translation = ViewModelProvider(
                    store,
                    ConnectionTranslation.factory(
                        userId = "user-1",
                        port = port,
                        report = outputs::add
                    )
                )[ConnectionTranslation::class.java]
                advanceUntilIdle()

                translation.submit(ConnectionIntent.BecameVisible)
                translation.submit(
                    ConnectionIntent.BluetoothAccessGranted
                )
                advanceUntilIdle()

                translation.submit(ConnectionIntent.Logout)
                advanceUntilIdle()

                assertEquals(
                    listOf(
                        ConnectionOutput.LogoutRequested,
                        ConnectionOutput.Stopped
                    ),
                    outputs
                )
                assertEquals(1, port.scanStops)
            } finally {
                store.clear()
                advanceUntilIdle()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `access starts binding and visible scan collection`() =
        runTest {
            Dispatchers.setMain(
                StandardTestDispatcher(testScheduler)
            )
            val store = ViewModelStore()
            val port = RecordingConnectionPort()
            try {
                val translation = ViewModelProvider(
                    store,
                    ConnectionTranslation.factory(
                        userId = "user-1",
                        port = port
                    )
                )[ConnectionTranslation::class.java]
                advanceUntilIdle()

                assertEquals(
                    ConnectionPhase.AwaitingBluetoothAccess,
                    translation.uiState.value.phase
                )
                assertEquals(emptyList<ConnectionCommand>(), port.commands)

                translation.submit(
                    ConnectionIntent.BecameVisible
                )
                translation.submit(
                    ConnectionIntent.BluetoothAccessGranted
                )
                advanceUntilIdle()

                assertEquals(
                    ConnectionPhase.Scanning,
                    translation.uiState.value.phase
                )
                assertEquals(
                    setOf(
                        ConnectionCommand.ReadBinding("user-1")
                    ),
                    port.commands.toSet()
                )
                assertEquals(1, port.scanCollections)
            } finally {
                store.clear()
                advanceUntilIdle()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `hidden cancels scan and visible starts one new collection`() =
        runTest {
            Dispatchers.setMain(
                StandardTestDispatcher(testScheduler)
            )
            val store = ViewModelStore()
            val port = RecordingConnectionPort()
            try {
                val translation = ViewModelProvider(
                    store,
                    ConnectionTranslation.factory(
                        userId = "user-1",
                        port = port
                    )
                )[ConnectionTranslation::class.java]

                translation.submit(ConnectionIntent.BecameVisible)
                translation.submit(
                    ConnectionIntent.BluetoothAccessGranted
                )
                advanceUntilIdle()
                assertEquals(1, port.scanCollections)

                port.devices.emit(
                    BluetoothDeviceInfo(
                        id = "AA:BB:CC:DD:EE:FF",
                        name = "BT24-S",
                        isBle = true,
                        rssi = -40
                    )
                )
                advanceUntilIdle()
                assertEquals(1, translation.uiState.value.devices.size)

                translation.submit(ConnectionIntent.BecameHidden)
                advanceUntilIdle()
                assertEquals(1, port.scanStops)

                translation.submit(ConnectionIntent.BecameVisible)
                advanceUntilIdle()
                assertEquals(2, port.scanCollections)
                assertEquals(
                    emptyList<DeviceItemUi>(),
                    translation.uiState.value.devices
                )
            } finally {
                store.clear()
                advanceUntilIdle()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `refresh clears devices without restarting active scan`() =
        runTest {
            Dispatchers.setMain(
                StandardTestDispatcher(testScheduler)
            )
            val store = ViewModelStore()
            val port = RecordingConnectionPort()
            try {
                val translation = ViewModelProvider(
                    store,
                    ConnectionTranslation.factory(
                        userId = "user-1",
                        port = port
                    )
                )[ConnectionTranslation::class.java]

                translation.submit(ConnectionIntent.BecameVisible)
                translation.submit(
                    ConnectionIntent.BluetoothAccessGranted
                )
                advanceUntilIdle()

                port.devices.emit(
                    BluetoothDeviceInfo(
                        id = "AA:BB:CC:DD:EE:FF",
                        name = "BT24-S",
                        isBle = true,
                        rssi = -40
                    )
                )
                advanceUntilIdle()
                assertEquals(1, translation.uiState.value.devices.size)

                translation.submit(ConnectionIntent.Refresh)
                advanceUntilIdle()

                assertEquals(
                    emptyList<DeviceItemUi>(),
                    translation.uiState.value.devices
                )
                assertEquals(1, port.scanCollections)
            } finally {
                store.clear()
                advanceUntilIdle()
                Dispatchers.resetMain()
            }
        }

    private class RecordingConnectionPort : ConnectionPort {
        val commands = mutableListOf<ConnectionCommand>()
        val devices =
            MutableSharedFlow<BluetoothDeviceInfo>(
                extraBufferCapacity = 8
            )
        var scanCollections = 0
        var scanStops = 0

        override fun scanDevices():
            Flow<BluetoothDeviceInfo> = flow {
            scanCollections += 1
            try {
                emitAll(devices)
            } finally {
                scanStops += 1
            }
        }

        override fun execute(
            command: ConnectionCommand
        ): Flow<ConnectionResult> {
            commands += command
            return emptyFlow()
        }
    }
}
