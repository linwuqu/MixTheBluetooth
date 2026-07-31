package com.biosensor.migratedev.translation.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.biosensor.migratedev.decisioncore.connection.BindingLookup
import com.biosensor.migratedev.decisioncore.connection.ConnectionDecisionCore
import com.biosensor.migratedev.decisioncore.connection.ConnectionEffect
import com.biosensor.migratedev.decisioncore.connection.ConnectionEvent
import com.biosensor.migratedev.decisioncore.connection.ConnectionState
import com.biosensor.migratedev.decisioncore.connection.ScanProgress
import com.biosensor.migratedev.orchestrator.WorkflowOrchestrator
import com.biosensor.migratedev.orchestrator.connection.ConnectionEffectExecutor
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import com.biosensor.migratedev.port.connection.ConnectionPort
import com.biosensor.migratedev.translation.Translation
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface ConnectionIntent {
    data object BluetoothAccessGranted : ConnectionIntent
    data object Refresh : ConnectionIntent
    data class SelectDevice(val deviceId: String) : ConnectionIntent
    data object BecameVisible : ConnectionIntent
    data object BecameHidden : ConnectionIntent
    data object Logout : ConnectionIntent
}

enum class ConnectionPhase {
    AwaitingBluetoothAccess, Scanning, Connecting, Connected, Failed, EndingSession, LogoutReady
}

data class DeviceItemUi(
    val id: String, val name: String, val rssi: Int?, val isRemembered: Boolean
)

data class ConnectionUiState(
    val phase: ConnectionPhase,
    val devices: List<DeviceItemUi>,
    val rememberedDeviceId: String?,
    val isRefreshing: Boolean,
    val message: String?
)

sealed interface ConnectionOutput {
    data object LogoutRequested : ConnectionOutput
    data object Stopped : ConnectionOutput
}

class ConnectionTranslation private constructor(
    userId: String,
    port: ConnectionPort,
    private val scanSessionIdFactory: () -> String,
    private val report: (ConnectionOutput) -> Unit
) : ViewModel(), Translation<ConnectionIntent, ConnectionUiState> {
    private val orchestrator = WorkflowOrchestrator(
        initialState = ConnectionState.Idle,
        decisionCore = ConnectionDecisionCore,
        effectExecutor = ConnectionEffectExecutor(port),
        scope = viewModelScope,
        logTag = "Connection.Workflow",
        onTransition = ::reportRootOutput
    )

    override val uiState: StateFlow<ConnectionUiState> =
        orchestrator.state.map(ConnectionState::toUiState).stateIn(
            viewModelScope, SharingStarted.Eagerly, ConnectionState.Idle.toUiState()
        )

    init {
        orchestrator.dispatch(
            ConnectionEvent.ConnectionCreated(userId)
        )
    }

    override fun submit(intent: ConnectionIntent) {
        orchestrator.dispatch(
            when (intent) {
                ConnectionIntent.BluetoothAccessGranted -> ConnectionEvent.BluetoothAccessGranted(
                    scanSessionIdFactory()
                )

                ConnectionIntent.Refresh -> ConnectionEvent.RefreshRequested(
                    scanSessionIdFactory()
                )

                is ConnectionIntent.SelectDevice -> ConnectionEvent.DeviceSelected(intent.deviceId)

                ConnectionIntent.BecameVisible -> ConnectionEvent.BecameVisible(
                    scanSessionIdFactory()
                )

                ConnectionIntent.BecameHidden -> ConnectionEvent.BecameHidden

                ConnectionIntent.Logout -> ConnectionEvent.LogoutRequested
            }
        )
    }

    override fun onCleared() {
        orchestrator.close()
    }

    private fun reportRootOutput(
        previous: ConnectionState, event: ConnectionEvent, current: ConnectionState
    ) {
        if (event == ConnectionEvent.LogoutRequested && previous != current) {
            report(ConnectionOutput.LogoutRequested)
        }
        if (previous != ConnectionState.LogoutReady && current == ConnectionState.LogoutReady) {
            report(ConnectionOutput.Stopped)
        }
    }

    companion object {
        fun factory(
            userId: String, port: ConnectionPort, scanSessionIdFactory: () -> String = {
                UUID.randomUUID().toString()
            }, report: (ConnectionOutput) -> Unit = {}
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>
            ): T {
                require(
                    modelClass.isAssignableFrom(
                        ConnectionTranslation::class.java
                    )
                )
                return ConnectionTranslation(
                    userId, port, scanSessionIdFactory, report
                ) as T
            }
        }
    }
}

private fun ConnectionState.toUiState(): ConnectionUiState {
    val binding = when (this) {
        is ConnectionState.Scanning -> binding
        is ConnectionState.Connecting -> binding
        is ConnectionState.ConnectionFailed -> binding
        else -> null
    }
    val remembered = (binding as? BindingLookup.Found)?.deviceId
    val bluetoothDevices: List<BluetoothDeviceInfo> = when (this) {
        is ConnectionState.Scanning -> devices
        is ConnectionState.Connecting -> devices
        is ConnectionState.Connected -> listOf(device)
        is ConnectionState.ConnectionFailed -> devices
        else -> emptyList()
    }
    val message = when (this) {
        is ConnectionState.Scanning -> message
        is ConnectionState.Connected -> bindingMessage
        is ConnectionState.ConnectionFailed -> message
        else -> null
    }
    return ConnectionUiState(
        phase = when (this) {
            ConnectionState.Idle, is ConnectionState.AwaitingBluetoothAccess -> ConnectionPhase.AwaitingBluetoothAccess

            is ConnectionState.Scanning -> ConnectionPhase.Scanning

            is ConnectionState.Connecting -> ConnectionPhase.Connecting

            is ConnectionState.Connected -> ConnectionPhase.Connected

            is ConnectionState.ConnectionFailed -> ConnectionPhase.Failed

            is ConnectionState.EndingSession -> ConnectionPhase.EndingSession

            ConnectionState.LogoutReady -> ConnectionPhase.LogoutReady
        },
        devices = bluetoothDevices.sortedWith(compareByDescending<BluetoothDeviceInfo> {
            it.id == remembered
        }.thenByDescending { it.rssi ?: Int.MIN_VALUE }.thenBy { it.name ?: it.id }).map {
            DeviceItemUi(
                id = it.id,
                name = it.name ?: it.id,
                rssi = it.rssi,
                isRemembered = it.id == remembered
            )
        },
        rememberedDeviceId = remembered,
        isRefreshing = (this as? ConnectionState.Scanning)?.progress is ScanProgress.Refreshing,
        message = message
    )
}
