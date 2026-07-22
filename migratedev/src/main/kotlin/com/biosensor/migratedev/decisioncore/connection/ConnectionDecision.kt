package com.biosensor.migratedev.decisioncore.connection

import com.biosensor.migratedev.decisioncore.DecisionCore
import com.biosensor.migratedev.decisioncore.Transition

data class BluetoothDeviceInfo(
    val id: String,
    val name: String?,
    val isBle: Boolean,
    val rssi: Int? = null
)

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Scanning : ConnectionState
    data class DeviceFound(val devices: List<BluetoothDeviceInfo>) : ConnectionState
    data class Connecting(val deviceId: String) : ConnectionState
    data class Connected(val device: BluetoothDeviceInfo) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

sealed interface ConnectionEvent {
    data object StartScan : ConnectionEvent
    data object StopScan : ConnectionEvent
    data class DevicesFound(val devices: List<BluetoothDeviceInfo>) : ConnectionEvent
    data object ScanStopped : ConnectionEvent
    data class ScanFailed(val message: String) : ConnectionEvent
    data class SelectDevice(val deviceId: String) : ConnectionEvent
    data class ConnectAccepted(val device: BluetoothDeviceInfo) : ConnectionEvent
    data class ConnectFailed(val message: String) : ConnectionEvent
    data object ConnectTimeout : ConnectionEvent
    data object Disconnect : ConnectionEvent
    data object Disconnected : ConnectionEvent
    data object ResetError : ConnectionEvent
}

sealed interface ConnectionEffect {
    data object StartScan : ConnectionEffect
    data object StopScan : ConnectionEffect
    data class ConnectDevice(val deviceId: String) : ConnectionEffect
    data object DisconnectDevice : ConnectionEffect
}

object ConnectionDecisionCore : DecisionCore<ConnectionState, ConnectionEvent, ConnectionEffect> {
    override fun reduce(
        currentState: ConnectionState,
        event: ConnectionEvent
    ): Transition<ConnectionState, ConnectionEffect> {
        return when (event) {
            ConnectionEvent.StartScan -> when (currentState) {
                ConnectionState.Idle, is ConnectionState.Error -> Transition(
                    newState = ConnectionState.Scanning,
                    effects = listOf(ConnectionEffect.StartScan)
                )

                else -> Transition(newState = currentState)
            }

            ConnectionEvent.StopScan -> when (currentState) {
                ConnectionState.Scanning, is ConnectionState.DeviceFound -> Transition(
                    newState = ConnectionState.Idle,
                    effects = listOf(ConnectionEffect.StopScan)
                )

                else -> Transition(newState = currentState)
            }

            is ConnectionEvent.DevicesFound -> when (currentState) {
                ConnectionState.Scanning -> Transition(
                    newState = ConnectionState.DeviceFound(event.devices)
                )

                else -> Transition(newState = currentState)
            }

            ConnectionEvent.ScanStopped -> when (currentState) {
                ConnectionState.Scanning, is ConnectionState.DeviceFound -> Transition(
                    newState = ConnectionState.Idle
                )

                else -> Transition(newState = currentState)
            }

            is ConnectionEvent.ScanFailed -> when (currentState) {
                ConnectionState.Scanning -> Transition(
                    newState = ConnectionState.Error(event.message)
                )

                else -> Transition(newState = currentState)
            }

            is ConnectionEvent.SelectDevice -> when (currentState) {
                is ConnectionState.DeviceFound -> Transition(
                    newState = ConnectionState.Connecting(event.deviceId),
                    effects = listOf(ConnectionEffect.ConnectDevice(event.deviceId))
                )

                else -> Transition(newState = currentState)
            }

            is ConnectionEvent.ConnectAccepted -> when (currentState) {
                is ConnectionState.Connecting -> Transition(
                    newState = ConnectionState.Connected(event.device)
                )

                else -> Transition(newState = currentState)
            }

            is ConnectionEvent.ConnectFailed -> when (currentState) {
                is ConnectionState.Connecting -> Transition(
                    newState = ConnectionState.Error(event.message)
                )

                else -> Transition(newState = currentState)
            }

            ConnectionEvent.ConnectTimeout -> when (currentState) {
                is ConnectionState.Connecting -> Transition(
                    newState = ConnectionState.Error("连接超时")
                )

                else -> Transition(newState = currentState)
            }

            ConnectionEvent.Disconnect -> when (currentState) {
                is ConnectionState.Connected -> Transition(
                    newState = ConnectionState.Idle,
                    effects = listOf(ConnectionEffect.DisconnectDevice)
                )

                else -> Transition(newState = currentState)
            }

            ConnectionEvent.Disconnected -> Transition(newState = ConnectionState.Idle)
            ConnectionEvent.ResetError -> when (currentState) {
                is ConnectionState.Error -> Transition(newState = ConnectionState.Idle)
                else -> Transition(newState = currentState)
            }
        }
    }
}
