package com.biosensor.migratedev.translation.connection

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.biosensor.migratedev.decisioncore.connection.ConnectionEffect
import com.biosensor.migratedev.decisioncore.connection.ConnectionEvent
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import com.biosensor.migratedev.port.connection.BindingSnapshot
import com.biosensor.migratedev.port.connection.ConnectionPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
                assertEquals(emptyList<ConnectionEffect>(), port.commands)
                assertEquals(listOf("user-1"), port.bindingUsers)

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
                assertEquals(emptyList<ConnectionEffect>(), port.commands)
                assertEquals(1, port.scanCollections)
            } finally {
                store.clear()
                advanceUntilIdle()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `binding and scan streams submit one automatic connection`() =
        runTest {
            Dispatchers.setMain(
                StandardTestDispatcher(testScheduler)
            )
            val store = ViewModelStore()
            val port = RecordingConnectionPort()
            val device = BluetoothDeviceInfo(
                id = "AA:BB:CC:DD:EE:FF",
                name = "BT24-S",
                isBle = true,
                rssi = -40
            )
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

                port.devices.emit(device)
                advanceUntilIdle()
                assertEquals(emptyList<ConnectionEffect>(), port.commands)

                port.binding.value = BindingSnapshot.Found(device.id)
                advanceUntilIdle()

                val connect = ConnectionEffect.ConnectDevice(device.id)
                assertEquals(listOf(connect), port.commands)

                port.devices.emit(device.copy(rssi = -41))
                port.binding.value = BindingSnapshot.Found(device.id)
                advanceUntilIdle()
                assertEquals(listOf(connect), port.commands)
            } finally {
                store.clear()
                advanceUntilIdle()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `manual selection uses the same connection command`() = runTest {
        Dispatchers.setMain(
            StandardTestDispatcher(testScheduler)
        )
        val store = ViewModelStore()
        val port = RecordingConnectionPort()
        val device = BluetoothDeviceInfo(
            id = "AA:BB:CC:DD:EE:FF",
            name = "BT24-S",
            isBle = true,
            rssi = -40
        )
        try {
            val translation = ViewModelProvider(
                store,
                ConnectionTranslation.factory(
                    userId = "user-1",
                    port = port
                )
            )[ConnectionTranslation::class.java]

            translation.submit(ConnectionIntent.BecameVisible)
            translation.submit(ConnectionIntent.BluetoothAccessGranted)
            advanceUntilIdle()
            port.devices.emit(device)
            advanceUntilIdle()

            translation.submit(ConnectionIntent.SelectDevice(device.id))
            advanceUntilIdle()

            assertEquals(
                listOf(ConnectionEffect.ConnectDevice(device.id)),
                port.commands
            )
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
    fun `connection success reports Connected with device id`() = runTest {
        Dispatchers.setMain(
            StandardTestDispatcher(testScheduler)
        )
        val store = ViewModelStore()
        val outputs = mutableListOf<ConnectionOutput>()
        val port = RecordingConnectionPort()
        val device = BluetoothDeviceInfo(
            id = "AA:BB:CC:DD:EE:FF",
            name = "BT24-S",
            isBle = true,
            rssi = -40
        )
        port.executeResults = { effect ->
            when (effect) {
                is ConnectionEffect.ConnectDevice ->
                    flowOf(ConnectionEvent.DeviceConnected(device))

                else -> emptyFlow()
            }
        }
        try {
            val translation = ViewModelProvider(
                store,
                ConnectionTranslation.factory(
                    userId = "user-1",
                    port = port,
                    report = outputs::add
                )
            )[ConnectionTranslation::class.java]

            translation.submit(ConnectionIntent.BecameVisible)
            translation.submit(ConnectionIntent.BluetoothAccessGranted)
            advanceUntilIdle()
            port.devices.emit(device)
            advanceUntilIdle()

            translation.submit(ConnectionIntent.SelectDevice(device.id))
            advanceUntilIdle()

            assertEquals(
                ConnectionPhase.Connected,
                translation.uiState.value.phase
            )
            assertEquals(
                listOf(ConnectionOutput.Connected(device.id)),
                outputs
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
        val commands = mutableListOf<ConnectionEffect>()
        val binding = MutableStateFlow<BindingSnapshot>(BindingSnapshot.Missing)
        val bindingUsers = mutableListOf<String>()
        val devices =
            MutableSharedFlow<BluetoothDeviceInfo>(
                extraBufferCapacity = 8
            )
        var scanCollections = 0
        var scanStops = 0
        var executeResults: (ConnectionEffect) -> Flow<ConnectionEvent> = { emptyFlow() }

        override fun readBinding(
            userId: String
        ): Flow<BindingSnapshot> = flow {
            bindingUsers += userId
            emitAll(binding)
        }

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
            effect: ConnectionEffect
        ): Flow<ConnectionEvent> {
            commands += effect
            return executeResults(effect)
        }
    }
}
