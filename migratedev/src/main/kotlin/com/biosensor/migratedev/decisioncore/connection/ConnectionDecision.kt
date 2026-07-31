package com.biosensor.migratedev.decisioncore.connection

import com.biosensor.migratedev.decisioncore.DecisionCore
import com.biosensor.migratedev.decisioncore.Transition
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo

sealed interface ConnectionState {
    data object Idle : ConnectionState

    data class AwaitingBluetoothAccess(
        val userId: String
    ) : ConnectionState

    data class Scanning(
        val userId: String, val message: String?
    ) : ConnectionState

    data class Connecting(
        val userId: String, val deviceId: String
    ) : ConnectionState

    data class Connected(
        val userId: String, val device: BluetoothDeviceInfo, val bindingMessage: String?
    ) : ConnectionState

    data class ConnectionFailed(
        val userId: String, val deviceId: String, val message: String
    ) : ConnectionState

    data object EndingSession : ConnectionState
    data object LogoutReady : ConnectionState
}

sealed interface ConnectionEvent {
    data class ConnectionCreated(
        val userId: String
    ) : ConnectionEvent

    data object BluetoothAccessGranted : ConnectionEvent

    data class ScanFailed(
        val message: String
    ) : ConnectionEvent

    data object RefreshRequested : ConnectionEvent

    data class ConnectRequested(
        val deviceId: String
    ) : ConnectionEvent

    data class DeviceConnected(
        val device: BluetoothDeviceInfo
    ) : ConnectionEvent

    data class DeviceConnectFailed(
        val message: String
    ) : ConnectionEvent

    data object DeviceConnectTimeout : ConnectionEvent
    data object DeviceDisconnected : ConnectionEvent

    data class BindingSaved(
        val deviceId: String
    ) : ConnectionEvent

    data class BindingSaveFailed(
        val message: String
    ) : ConnectionEvent

    data object LogoutRequested : ConnectionEvent
}

sealed interface ConnectionEffect {
    data class SaveBinding(
        val userId: String, val deviceId: String
    ) : ConnectionEffect

    data class ConnectDevice(
        val deviceId: String
    ) : ConnectionEffect

    data object DisconnectDevice : ConnectionEffect
}

object ConnectionDecisionCore : DecisionCore<ConnectionState, ConnectionEvent, ConnectionEffect> {

    override fun reduce(
        currentState: ConnectionState, event: ConnectionEvent
    ): Transition<ConnectionState, ConnectionEffect> = when (event) {
        is ConnectionEvent.ConnectionCreated -> onCreated(currentState, event)
        ConnectionEvent.BluetoothAccessGranted -> onAccessGranted(currentState)

        is ConnectionEvent.ScanFailed -> if (currentState is ConnectionState.Scanning) {
            Transition(currentState.copy(message = event.message))
        } else {
            Transition(currentState)
        }

        ConnectionEvent.RefreshRequested -> onRefresh(currentState)

        is ConnectionEvent.ConnectRequested -> onConnectRequested(
            currentState, event.deviceId
        )

        is ConnectionEvent.DeviceConnected -> onDeviceConnected(
            currentState, event.device
        )

        is ConnectionEvent.DeviceConnectFailed -> onConnectFailed(
            currentState, event.message
        )

        ConnectionEvent.DeviceConnectTimeout -> onConnectFailed(
            currentState, "连接超时"
        )

        ConnectionEvent.DeviceDisconnected -> onDisconnected(currentState)

        is ConnectionEvent.BindingSaved -> onBindingSaved(
            currentState, event.deviceId
        )

        is ConnectionEvent.BindingSaveFailed -> onBindingSaveFailed(
            currentState, event.message
        )

        ConnectionEvent.LogoutRequested -> onLogout(currentState)
    }

    private fun onCreated(
        state: ConnectionState, event: ConnectionEvent.ConnectionCreated
    ): Transition<ConnectionState, ConnectionEffect> = if (state == ConnectionState.Idle) {
        Transition(ConnectionState.AwaitingBluetoothAccess(event.userId))
    } else {
        Transition(state)
    }

    private fun onAccessGranted(
        state: ConnectionState
    ): Transition<ConnectionState, ConnectionEffect> =
        if (state is ConnectionState.AwaitingBluetoothAccess) {
            Transition(ConnectionState.Scanning(state.userId, message = null))
        } else {
            Transition(state)
        }

    private fun onRefresh(
        state: ConnectionState
    ): Transition<ConnectionState, ConnectionEffect> = when (state) {
        is ConnectionState.Scanning -> Transition(state.copy(message = null))
        is ConnectionState.ConnectionFailed -> Transition(
            ConnectionState.Scanning(state.userId, message = null)
        )

        else -> Transition(state)
    }

    private fun onConnectRequested(
        state: ConnectionState, deviceId: String
    ): Transition<ConnectionState, ConnectionEffect> {
        val userId = when (state) {
            is ConnectionState.Scanning -> state.userId
            is ConnectionState.ConnectionFailed -> state.userId
            else -> return Transition(state)
        }
        return Transition(
            newState = ConnectionState.Connecting(userId, deviceId),
            effects = listOf(ConnectionEffect.ConnectDevice(deviceId))
        )
    }

    private fun onDeviceConnected(
        state: ConnectionState, device: BluetoothDeviceInfo
    ): Transition<ConnectionState, ConnectionEffect> =
        if (state is ConnectionState.Connecting && state.deviceId == device.id) {
            Transition(
                newState = ConnectionState.Connected(
                    userId = state.userId, device = device, bindingMessage = null
                ), effects = listOf(
                    ConnectionEffect.SaveBinding(state.userId, device.id)
                )
            )
        } else {
            Transition(state)
        }

    private fun onConnectFailed(
        state: ConnectionState, message: String
    ): Transition<ConnectionState, ConnectionEffect> = if (state is ConnectionState.Connecting) {
        Transition(
            ConnectionState.ConnectionFailed(
                userId = state.userId, deviceId = state.deviceId, message = message
            )
        )
    } else {
        Transition(state)
    }

    private fun onDisconnected(
        state: ConnectionState
    ): Transition<ConnectionState, ConnectionEffect> = when (state) {
        ConnectionState.EndingSession -> Transition(ConnectionState.LogoutReady)

        is ConnectionState.Connected -> Transition(
            ConnectionState.ConnectionFailed(
                userId = state.userId, deviceId = state.device.id, message = "蓝牙连接已断开"
            )
        )

        else -> Transition(state)
    }

    private fun onBindingSaved(
        state: ConnectionState, deviceId: String
    ): Transition<ConnectionState, ConnectionEffect> =
        if (state is ConnectionState.Connected && state.device.id == deviceId) {
            Transition(state.copy(bindingMessage = null))
        } else {
            Transition(state)
        }

    private fun onBindingSaveFailed(
        state: ConnectionState, message: String
    ): Transition<ConnectionState, ConnectionEffect> = if (state is ConnectionState.Connected) {
        Transition(state.copy(bindingMessage = message))
    } else {
        Transition(state)
    }

    private fun onLogout(
        state: ConnectionState
    ): Transition<ConnectionState, ConnectionEffect> = when (state) {
        is ConnectionState.Connecting, is ConnectionState.Connected -> Transition(
            newState = ConnectionState.EndingSession,
            effects = listOf(ConnectionEffect.DisconnectDevice)
        )

        ConnectionState.EndingSession, ConnectionState.LogoutReady -> Transition(state)

        else -> Transition(ConnectionState.LogoutReady)
    }
}
