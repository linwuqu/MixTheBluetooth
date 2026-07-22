package com.biosensor.migratedev.translation.connection

import com.biosensor.migratedev.decisioncore.connection.BluetoothDeviceInfo
import com.biosensor.migratedev.decisioncore.connection.ConnectionEffect
import com.biosensor.migratedev.decisioncore.connection.ConnectionEvent
import com.biosensor.migratedev.decisioncore.connection.ConnectionState
import com.biosensor.migratedev.orchestrator.WorkflowOrchestrator
import com.biosensor.migratedev.translation.Translation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface ConnectionIntent {
    data object StartScan : ConnectionIntent
    data object StopScan : ConnectionIntent
    data class SelectDevice(val deviceId: String) : ConnectionIntent
    data object Disconnect : ConnectionIntent
    data object ResetError : ConnectionIntent
}

sealed interface ConnectionUiState {
    data object Idle : ConnectionUiState
    data object Scanning : ConnectionUiState
    data class Devices(val devices: List<BluetoothDeviceInfo>) : ConnectionUiState
    data class Connecting(val deviceId: String) : ConnectionUiState
    data class Connected(val device: BluetoothDeviceInfo) : ConnectionUiState
    data class Error(val message: String) : ConnectionUiState
}

class ConnectionTranslation(
    private val orchestrator: WorkflowOrchestrator<ConnectionState, ConnectionEvent, ConnectionEffect>,
    scope: CoroutineScope
) : Translation<ConnectionIntent, ConnectionUiState> {

    override val uiState: StateFlow<ConnectionUiState> = orchestrator.state
        .map { it.toUiState() }
        .stateIn(scope, SharingStarted.Eagerly, orchestrator.state.value.toUiState())

    override fun submit(intent: ConnectionIntent) {
        orchestrator.dispatch(intent.toEvent())
    }

    private fun ConnectionIntent.toEvent(): ConnectionEvent {
        return when (this) {
            ConnectionIntent.StartScan -> ConnectionEvent.StartScan
            ConnectionIntent.StopScan -> ConnectionEvent.StopScan
            is ConnectionIntent.SelectDevice -> ConnectionEvent.SelectDevice(deviceId)
            ConnectionIntent.Disconnect -> ConnectionEvent.Disconnect
            ConnectionIntent.ResetError -> ConnectionEvent.ResetError
        }
    }

    private fun ConnectionState.toUiState(): ConnectionUiState {
        return when (this) {
            ConnectionState.Idle -> ConnectionUiState.Idle
            ConnectionState.Scanning -> ConnectionUiState.Scanning
            is ConnectionState.DeviceFound -> ConnectionUiState.Devices(devices)
            is ConnectionState.Connecting -> ConnectionUiState.Connecting(deviceId)
            is ConnectionState.Connected -> ConnectionUiState.Connected(device)
            is ConnectionState.Error -> ConnectionUiState.Error(message)
        }
    }
}
