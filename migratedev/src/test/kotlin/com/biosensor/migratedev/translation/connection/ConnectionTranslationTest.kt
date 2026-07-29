package com.biosensor.migratedev.translation.connection

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothCommand
import com.biosensor.migratedev.port.connection.ConnectionCommand
import com.biosensor.migratedev.port.connection.ConnectionPort
import com.biosensor.migratedev.port.connection.ConnectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
    fun `creation is one event and access starts binding plus scan`() =
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
                        port = port,
                        scanSessionIdFactory = { "scan-1" }
                    )
                )[ConnectionTranslation::class.java]
                advanceUntilIdle()

                assertEquals(
                    ConnectionPhase.AwaitingBluetoothAccess,
                    translation.uiState.value.phase
                )
                assertEquals(emptyList<ConnectionCommand>(), port.commands)

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
                        ConnectionCommand.ReadBinding("user-1"),
                        ConnectionCommand.Bluetooth(
                            BluetoothCommand.StartScan("scan-1")
                        )
                    ),
                    port.commands.toSet()
                )
            } finally {
                store.clear()
                Dispatchers.resetMain()
            }
        }

    private class RecordingConnectionPort : ConnectionPort {
        val commands = mutableListOf<ConnectionCommand>()

        override fun execute(
            command: ConnectionCommand
        ): Flow<ConnectionResult> {
            commands += command
            return emptyFlow()
        }
    }
}
