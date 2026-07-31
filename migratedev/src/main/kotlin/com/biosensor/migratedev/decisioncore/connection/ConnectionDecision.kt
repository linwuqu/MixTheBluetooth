package com.biosensor.migratedev.decisioncore.connection

import com.biosensor.migratedev.decisioncore.DecisionCore
import com.biosensor.migratedev.decisioncore.Transition
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo

sealed interface BindingLookup {
    data object Loading : BindingLookup
    data object Missing : BindingLookup
    data class Found(val deviceId: String) : BindingLookup
    data class Failed(val message: String) : BindingLookup
}

enum class ConnectionSource {
    Automatic, Manual
}

sealed interface ConnectionState {
    data object Idle : ConnectionState

    data class AwaitingBluetoothAccess(
        val userId: String
    ) : ConnectionState

    data class Scanning(
        val userId: String,
        val devices: List<BluetoothDeviceInfo>,
        val binding: BindingLookup,
        val autoConnectAttempted: Boolean,
        val message: String?
    ) : ConnectionState

    data class Connecting(
        val userId: String,
        val deviceId: String,
        val devices: List<BluetoothDeviceInfo>,
        val binding: BindingLookup,
        val source: ConnectionSource
    ) : ConnectionState

    data class Connected(
        val userId: String, val device: BluetoothDeviceInfo, val bindingMessage: String?
    ) : ConnectionState

    data class ConnectionFailed(
        val userId: String,
        val deviceId: String,
        val devices: List<BluetoothDeviceInfo>,
        val binding: BindingLookup,
        val source: ConnectionSource,
        val message: String
    ) : ConnectionState

    data object EndingSession : ConnectionState
    data object LogoutReady : ConnectionState
}

sealed interface ConnectionEvent {
    data class ConnectionCreated(
        val userId: String
    ) : ConnectionEvent

    data object BluetoothAccessGranted : ConnectionEvent

    data class BindingLoaded(
        val deviceId: String
    ) : ConnectionEvent
    data object BindingMissing : ConnectionEvent
    data class BindingFailed(
        val message: String
    ) : ConnectionEvent

    data class DevicesUpdated(
        val devices: List<BluetoothDeviceInfo>
    ) : ConnectionEvent
    data class ScanFailed(
        val message: String
    ) : ConnectionEvent

    data object RefreshRequested : ConnectionEvent

    data class DeviceSelected(
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
    data class ReadBinding(
        val userId: String
    ) : ConnectionEffect

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

        is ConnectionEvent.BindingLoaded -> updateScanning(currentState) {
            it.copy(
                binding = BindingLookup.Found(
                    event.deviceId
                )
            )
        }

        ConnectionEvent.BindingMissing -> updateScanning(currentState) {
            it.copy(binding = BindingLookup.Missing)
        }

        is ConnectionEvent.BindingFailed -> updateScanning(currentState) {
            it.copy(
                binding = BindingLookup.Failed(
                    event.message
                ), message = event.message
            )
        }

        is ConnectionEvent.DevicesUpdated -> updateScanning(currentState) {
            it.copy(devices = event.devices)
        }

        is ConnectionEvent.ScanFailed -> updateScanning(currentState) {
            it.copy(message = event.message)
        }

        ConnectionEvent.RefreshRequested -> onRefresh(currentState)

        is ConnectionEvent.DeviceSelected -> onDeviceSelected(
            currentState, event.deviceId
        )

        is ConnectionEvent.DeviceConnected -> onDeviceConnected(
            currentState, event.device
        )

        is ConnectionEvent.DeviceConnectFailed -> onConnectFailed(
            currentState, event.message
        )

        ConnectionEvent.DeviceConnectTimeout -> onConnectFailed(currentState, "连接超时")

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
        Transition(
            ConnectionState.AwaitingBluetoothAccess(
                event.userId
            )
        )
    } else {
        Transition(state)
    }

    private fun onAccessGranted(
        state: ConnectionState
    ): Transition<ConnectionState, ConnectionEffect> =
        if (state is ConnectionState.AwaitingBluetoothAccess) {
            Transition(
                newState = ConnectionState.Scanning(
                    userId = state.userId,
                    devices = emptyList(),
                    binding = BindingLookup.Loading,
                    autoConnectAttempted = false,
                    message = null
                ), effects = listOf(
                    ConnectionEffect.ReadBinding(state.userId)
                )
            )
        } else {
            Transition(state)
        }

    private fun updateScanning(
        state: ConnectionState, update: (
            ConnectionState.Scanning
        ) -> ConnectionState.Scanning
    ): Transition<ConnectionState, ConnectionEffect> = if (state is ConnectionState.Scanning) {
        maybeAutoConnect(update(state))
    } else {
        Transition(state)
    }

    private fun maybeAutoConnect(
        state: ConnectionState.Scanning
    ): Transition<ConnectionState, ConnectionEffect> {
        if (state.autoConnectAttempted) {
            return Transition(state)
        }
        val remembered =
            (state.binding as? BindingLookup.Found)?.deviceId ?: return Transition(state)
        if (state.devices.none { it.id == remembered }) {
            return Transition(state)
        }
        return Transition(
            newState = ConnectionState.Connecting(
                userId = state.userId,
                deviceId = remembered,
                devices = state.devices,
                binding = state.binding,
                source = ConnectionSource.Automatic
            ), effects = listOf(
                ConnectionEffect.ConnectDevice(remembered)
            )
        )
    }

    private fun onRefresh(
        state: ConnectionState
    ): Transition<ConnectionState, ConnectionEffect> = when (state) {
        is ConnectionState.Scanning -> Transition(
            state.copy(
                devices = emptyList(), message = null
            )
        )

        is ConnectionState.ConnectionFailed -> Transition(
            ConnectionState.Scanning(
                userId = state.userId,
                devices = emptyList(),
                binding = state.binding,
                autoConnectAttempted = state.source == ConnectionSource.Automatic,
                message = null
            )
        )

        else -> Transition(state)
    }

    private fun onDeviceSelected(
        state: ConnectionState, deviceId: String
    ): Transition<ConnectionState, ConnectionEffect> {
        val userId: String
        val devices: List<BluetoothDeviceInfo>
        val binding: BindingLookup
        when (state) {
            is ConnectionState.Scanning -> {
                userId = state.userId
                devices = state.devices
                binding = state.binding
            }

            is ConnectionState.ConnectionFailed -> {
                userId = state.userId
                devices = state.devices
                binding = state.binding
            }

            else -> return Transition(state)
        }
        if (devices.none { it.id == deviceId }) {
            return Transition(state)
        }
        return Transition(
            newState = ConnectionState.Connecting(
                userId = userId,
                deviceId = deviceId,
                devices = devices,
                binding = binding,
                source = ConnectionSource.Manual
            ), effects = listOf(
                ConnectionEffect.ConnectDevice(deviceId)
            )
        )
    }

    private fun onDeviceConnected(
        state: ConnectionState, device: BluetoothDeviceInfo
    ): Transition<ConnectionState, ConnectionEffect> =
        if (state is ConnectionState.Connecting && state.deviceId == device.id) {
            Transition(
                newState = ConnectionState.Connected(
                    userId = state.userId, device = device, bindingMessage = null
                ), effects = if (state.source == ConnectionSource.Manual) {
                    listOf(
                        ConnectionEffect.SaveBinding(
                            state.userId, device.id
                        )
                    )
                } else {
                    emptyList()
                }
            )
        } else {
            Transition(state)
        }

    private fun onConnectFailed(
        state: ConnectionState, message: String
    ): Transition<ConnectionState, ConnectionEffect> = if (state is ConnectionState.Connecting) {
        Transition(
            ConnectionState.ConnectionFailed(
                userId = state.userId,
                deviceId = state.deviceId,
                devices = state.devices,
                binding = state.binding,
                source = state.source,
                message = message
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
                userId = state.userId,
                deviceId = state.device.id,
                devices = listOf(state.device),
                binding = BindingLookup.Found(
                    state.device.id
                ),
                source = ConnectionSource.Automatic,
                message = "蓝牙连接已断开"
            )
        )

        else -> Transition(state)
    }

    private fun onBindingSaved(
        state: ConnectionState, deviceId: String
    ): Transition<ConnectionState, ConnectionEffect> =
        if (state is ConnectionState.Connected && state.device.id == deviceId) {
            Transition(
                state.copy(bindingMessage = null)
            )
        } else {
            Transition(state)
        }

    private fun onBindingSaveFailed(
        state: ConnectionState, message: String
    ): Transition<ConnectionState, ConnectionEffect> = if (state is ConnectionState.Connected) {
        Transition(
            state.copy(bindingMessage = message)
        )
    } else {
        Transition(state)
    }

    private fun onLogout(
        state: ConnectionState
    ): Transition<ConnectionState, ConnectionEffect> = when (state) {
        is ConnectionState.Connecting, is ConnectionState.Connected -> Transition(
            newState = ConnectionState.EndingSession, effects = listOf(
                ConnectionEffect.DisconnectDevice
            )
        )

        ConnectionState.EndingSession, ConnectionState.LogoutReady -> Transition(state)

        else -> Transition(ConnectionState.LogoutReady)
    }
}
