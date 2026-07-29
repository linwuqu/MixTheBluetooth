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

sealed interface ScanProgress {
    data object Starting : ScanProgress
    data class Active(val roundId: Long) : ScanProgress
    data class Refreshing(val previousRoundId: Long?) : ScanProgress
    data class Stopping(
        val resumeScanSessionId: String? = null
    ) : ScanProgress

    data object Stopped : ScanProgress
}

enum class ConnectionSource {
    Automatic, Manual
}

enum class SessionResource {
    Scan, Connection
}

sealed interface ConnectionState {
    data object Idle : ConnectionState

    data class AwaitingBluetoothAccess(
        val userId: String
    ) : ConnectionState

    data class Scanning(
        val userId: String,
        val scanSessionId: String,
        val devices: List<BluetoothDeviceInfo>,
        val binding: BindingLookup,
        val progress: ScanProgress,
        val autoConnectAttempted: Boolean,
        val message: String?,
        val isVisible: Boolean
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

    data class EndingSession(
        val pending: Set<SessionResource>
    ) : ConnectionState

    data object LogoutReady : ConnectionState
}

sealed interface ConnectionEvent {
    data class ConnectionCreated(
        val userId: String
    ) : ConnectionEvent

    data class BluetoothAccessGranted(
        val scanSessionId: String
    ) : ConnectionEvent

    data class BindingLoaded(
        val deviceId: String
    ) : ConnectionEvent

    data object BindingMissing : ConnectionEvent

    data class BindingFailed(
        val message: String
    ) : ConnectionEvent

    data class ScanStarted(
        val scanSessionId: String, val roundId: Long
    ) : ConnectionEvent

    data class DevicesUpdated(
        val scanSessionId: String, val roundId: Long, val devices: List<BluetoothDeviceInfo>
    ) : ConnectionEvent

    data class ScanRoundEnded(
        val scanSessionId: String, val roundId: Long
    ) : ConnectionEvent

    data class ScanRefreshed(
        val scanSessionId: String, val roundId: Long
    ) : ConnectionEvent

    data class ScanFailed(
        val scanSessionId: String, val message: String
    ) : ConnectionEvent

    data class ScanStopped(
        val scanSessionId: String
    ) : ConnectionEvent

    data class RefreshRequested(
        val replacementScanSessionId: String
    ) : ConnectionEvent

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

    data object BecameHidden : ConnectionEvent

    data class BecameVisible(
        val scanSessionId: String
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

    data class StartScan(
        val scanSessionId: String
    ) : ConnectionEffect

    data class RefreshScan(
        val scanSessionId: String
    ) : ConnectionEffect

    data class StopScan(
        val scanSessionId: String
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

        is ConnectionEvent.BluetoothAccessGranted -> onAccessGranted(currentState, event)

        is ConnectionEvent.BindingLoaded -> updateScanning(currentState) {
            it.copy(
                binding = BindingLookup.Found(event.deviceId)
            )
        }

        ConnectionEvent.BindingMissing -> updateScanning(currentState) {
            it.copy(binding = BindingLookup.Missing)
        }

        is ConnectionEvent.BindingFailed -> updateScanning(currentState) {
            it.copy(
                binding = BindingLookup.Failed(event.message), message = event.message
            )
        }

        is ConnectionEvent.ScanStarted -> updateMatchingScan(
            currentState, event.scanSessionId
        ) {
            it.copy(
                progress = ScanProgress.Active(event.roundId), message = null
            )
        }

        is ConnectionEvent.DevicesUpdated -> updateMatchingScan(
            currentState, event.scanSessionId
        ) {
            it.copy(
                devices = event.devices, progress = ScanProgress.Active(event.roundId)
            )
        }

        is ConnectionEvent.ScanRoundEnded -> updateMatchingScan(
            currentState, event.scanSessionId
        ) {
            it.copy(progress = ScanProgress.Starting)
        }

        is ConnectionEvent.ScanRefreshed -> updateMatchingScan(
            currentState, event.scanSessionId
        ) {
            it.copy(
                progress = ScanProgress.Active(event.roundId), message = null
            )
        }

        is ConnectionEvent.ScanFailed -> updateMatchingScan(
            currentState, event.scanSessionId
        ) {
            it.copy(
                progress = ScanProgress.Stopped, message = event.message
            )
        }

        is ConnectionEvent.ScanStopped -> onScanStopped(currentState, event)

        is ConnectionEvent.RefreshRequested -> onRefresh(currentState, event)

        is ConnectionEvent.DeviceSelected -> onDeviceSelected(currentState, event.deviceId)

        is ConnectionEvent.DeviceConnected -> onDeviceConnected(currentState, event.device)

        is ConnectionEvent.DeviceConnectFailed -> onConnectFailed(currentState, event.message)

        ConnectionEvent.DeviceConnectTimeout -> onConnectFailed(currentState, "连接超时")

        ConnectionEvent.DeviceDisconnected -> onDisconnected(currentState)

        is ConnectionEvent.BindingSaved -> onBindingSaved(currentState, event.deviceId)

        is ConnectionEvent.BindingSaveFailed -> onBindingSaveFailed(currentState, event.message)

        ConnectionEvent.BecameHidden -> onHidden(currentState)

        is ConnectionEvent.BecameVisible -> onVisible(currentState, event.scanSessionId)

        ConnectionEvent.LogoutRequested -> onLogout(currentState)
    }

    private fun onCreated(
        state: ConnectionState, event: ConnectionEvent.ConnectionCreated
    ): Transition<ConnectionState, ConnectionEffect> = if (state == ConnectionState.Idle) {
        Transition(
            ConnectionState.AwaitingBluetoothAccess(event.userId)
        )
    } else {
        Transition(state)
    }

    private fun onAccessGranted(
        state: ConnectionState, event: ConnectionEvent.BluetoothAccessGranted
    ): Transition<ConnectionState, ConnectionEffect> =
        if (state is ConnectionState.AwaitingBluetoothAccess) {
            Transition(
                newState = ConnectionState.Scanning(
                    userId = state.userId,
                    scanSessionId = event.scanSessionId,
                    devices = emptyList(),
                    binding = BindingLookup.Loading,
                    progress = ScanProgress.Starting,
                    autoConnectAttempted = false,
                    message = null,
                    isVisible = true
                ), effects = listOf(
                    ConnectionEffect.ReadBinding(state.userId),
                    ConnectionEffect.StartScan(event.scanSessionId)
                )
            )
        } else {
            Transition(state)
        }

    private fun updateScanning(
        state: ConnectionState, update: (ConnectionState.Scanning) -> ConnectionState.Scanning
    ): Transition<ConnectionState, ConnectionEffect> = if (state is ConnectionState.Scanning) {
        maybeAutoConnect(update(state))
    } else {
        Transition(state)
    }

    private fun updateMatchingScan(
        state: ConnectionState,
        scanSessionId: String,
        update: (ConnectionState.Scanning) -> ConnectionState.Scanning
    ): Transition<ConnectionState, ConnectionEffect> =
        if (state is ConnectionState.Scanning && state.scanSessionId == scanSessionId) {
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
        state: ConnectionState, event: ConnectionEvent.RefreshRequested
    ): Transition<ConnectionState, ConnectionEffect> = when (state) {
        is ConnectionState.Scanning -> {
            if (!state.isVisible) {
                Transition(state)
            } else if (state.progress == ScanProgress.Stopped) {
                Transition(
                    state.copy(
                        scanSessionId = event.replacementScanSessionId,
                        progress = ScanProgress.Starting,
                        message = null
                    ), listOf(
                        ConnectionEffect.StartScan(
                            event.replacementScanSessionId
                        )
                    )
                )
            } else if (state.progress is ScanProgress.Active) {
                val previousRound = state.progress.roundId
                Transition(
                    state.copy(
                        progress = ScanProgress.Refreshing(
                            previousRound
                        ), message = null
                    ), listOf(
                        ConnectionEffect.RefreshScan(
                            state.scanSessionId
                        )
                    )
                )
            } else {
                Transition(state)
            }
        }

        is ConnectionState.ConnectionFailed -> Transition(
            newState = ConnectionState.Scanning(
                userId = state.userId,
                scanSessionId = event.replacementScanSessionId,
                devices = state.devices,
                binding = state.binding,
                progress = ScanProgress.Starting,
                autoConnectAttempted = state.source == ConnectionSource.Automatic,
                message = null,
                isVisible = true
            ), effects = listOf(
                ConnectionEffect.StartScan(
                    event.replacementScanSessionId
                )
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
            ConnectionState.Connecting(
                userId = userId,
                deviceId = deviceId,
                devices = devices,
                binding = binding,
                source = ConnectionSource.Manual
            ), listOf(ConnectionEffect.ConnectDevice(deviceId))
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
        is ConnectionState.EndingSession -> finishResource(state, SessionResource.Connection)

        is ConnectionState.Connected -> Transition(
            ConnectionState.ConnectionFailed(
                userId = state.userId,
                deviceId = state.device.id,
                devices = listOf(state.device),
                binding = BindingLookup.Found(state.device.id),
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

    private fun onHidden(
        state: ConnectionState
    ): Transition<ConnectionState, ConnectionEffect> = if (state is ConnectionState.Scanning) {
        when (state.progress) {
            is ScanProgress.Stopping -> Transition(
                state.copy(
                    progress = ScanProgress.Stopping(), isVisible = false
                )
            )

            ScanProgress.Stopped -> Transition(
                state.copy(isVisible = false)
            )

            else -> Transition(
                state.copy(
                    progress = ScanProgress.Stopping(), isVisible = false
                ), listOf(
                    ConnectionEffect.StopScan(
                        state.scanSessionId
                    )
                )
            )
        }
    } else {
        Transition(state)
    }

    private fun onVisible(
        state: ConnectionState, scanSessionId: String
    ): Transition<ConnectionState, ConnectionEffect> = if (state is ConnectionState.Scanning) {
        when (state.progress) {
            ScanProgress.Stopped -> Transition(
                state.copy(
                    scanSessionId = scanSessionId,
                    progress = ScanProgress.Starting,
                    isVisible = true,
                    message = null
                ), listOf(ConnectionEffect.StartScan(scanSessionId))
            )

            is ScanProgress.Stopping -> Transition(
                state.copy(
                    progress = ScanProgress.Stopping(
                        resumeScanSessionId = scanSessionId
                    ), isVisible = true
                )
            )

            else -> Transition(state.copy(isVisible = true))
        }
    } else {
        Transition(state)
    }

    private fun onLogout(
        state: ConnectionState
    ): Transition<ConnectionState, ConnectionEffect> = when (state) {
        is ConnectionState.Scanning -> if (state.progress == ScanProgress.Stopped) {
            Transition(ConnectionState.LogoutReady)
        } else if (state.progress is ScanProgress.Stopping) {
            Transition(
                ConnectionState.EndingSession(
                    setOf(SessionResource.Scan)
                )
            )
        } else {
            Transition(
                ConnectionState.EndingSession(
                    setOf(SessionResource.Scan)
                ), listOf(
                    ConnectionEffect.StopScan(
                        state.scanSessionId
                    )
                )
            )
        }

        is ConnectionState.Connecting, is ConnectionState.Connected -> Transition(
            ConnectionState.EndingSession(
                setOf(SessionResource.Connection)
            ), listOf(ConnectionEffect.DisconnectDevice)
        )

        is ConnectionState.EndingSession, ConnectionState.LogoutReady -> Transition(state)

        else -> Transition(ConnectionState.LogoutReady)
    }

    private fun onScanStopped(
        state: ConnectionState, event: ConnectionEvent.ScanStopped
    ): Transition<ConnectionState, ConnectionEffect> = when (state) {
        is ConnectionState.Scanning -> if (state.scanSessionId == event.scanSessionId) {
            val resumeScanSessionId =
                (state.progress as? ScanProgress.Stopping)?.resumeScanSessionId
            if (resumeScanSessionId == null) {
                Transition(
                    state.copy(progress = ScanProgress.Stopped)
                )
            } else {
                Transition(
                    state.copy(
                        scanSessionId = resumeScanSessionId, progress = ScanProgress.Starting
                    ), listOf(
                        ConnectionEffect.StartScan(
                            resumeScanSessionId
                        )
                    )
                )
            }
        } else {
            Transition(state)
        }

        is ConnectionState.EndingSession -> finishResource(state, SessionResource.Scan)

        else -> Transition(state)
    }

    private fun finishResource(
        state: ConnectionState.EndingSession, resource: SessionResource
    ): Transition<ConnectionState, ConnectionEffect> {
        val remaining = state.pending - resource
        return Transition(
            if (remaining.isEmpty()) {
                ConnectionState.LogoutReady
            } else {
                state.copy(pending = remaining)
            }
        )
    }
}
