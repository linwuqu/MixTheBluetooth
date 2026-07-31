package com.biosensor.migratedev.translation.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.biosensor.migratedev.decisioncore.connection.BindingLookup
import com.biosensor.migratedev.decisioncore.connection.ConnectionDecisionCore
import com.biosensor.migratedev.decisioncore.connection.ConnectionEvent
import com.biosensor.migratedev.decisioncore.connection.ConnectionState
import com.biosensor.migratedev.orchestrator.WorkflowOrchestrator
import com.biosensor.migratedev.orchestrator.connection.ConnectionEffectExecutor
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import com.biosensor.migratedev.port.connection.ConnectionPort
import com.biosensor.migratedev.translation.Translation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ConnectionIntent {
    data object BluetoothAccessGranted : ConnectionIntent
    data object Refresh : ConnectionIntent
    data class SelectDevice(
        val deviceId: String
    ) : ConnectionIntent

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

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionTranslation private constructor(
    userId: String, private val port: ConnectionPort, private val report: (ConnectionOutput) -> Unit
) : ViewModel(), Translation<ConnectionIntent, ConnectionUiState> {

    private val orchestrator = WorkflowOrchestrator(
        initialState = ConnectionState.Idle,
        decisionCore = ConnectionDecisionCore,
        effectExecutor = ConnectionEffectExecutor(port),
        scope = viewModelScope,
        logTag = "Connection.Workflow",
        onTransition = ::reportRootOutput
    )

    private val bluetoothAccessGranted = MutableStateFlow(false)
    private val visible = MutableStateFlow(false)
    private val scanRestart = MutableStateFlow(0)
    private val discovered = linkedMapOf<String, BluetoothDeviceInfo>()
    private var scanActive = false
    private var logoutStarted = false

    override val uiState: StateFlow<ConnectionUiState> =
        orchestrator.state.map(ConnectionState::toUiState).stateIn(
            viewModelScope, SharingStarted.Eagerly, ConnectionState.Idle.toUiState()
        )

    private val scanCollection: Job = viewModelScope.launch {
        combine(
            bluetoothAccessGranted, visible, orchestrator.state, scanRestart
        ) { access, isVisible, state, restart ->
            ScanRequest(
                enabled = access && isVisible && state is ConnectionState.Scanning,
                restart = restart
            )
        }.distinctUntilChanged().flatMapLatest { request ->
            if (request.enabled) scanFlow()
            else emptyFlow()
        }.collect { device ->
            discovered[device.id] = device
            orchestrator.dispatch(
                ConnectionEvent.DevicesUpdated(
                    discovered.values.toList()
                )
            )
        }
    }

    init {
        orchestrator.dispatch(
            ConnectionEvent.ConnectionCreated(userId)
        )
    }

    override fun submit(intent: ConnectionIntent) {
        when (intent) {
            ConnectionIntent.BluetoothAccessGranted -> {
                bluetoothAccessGranted.value = true
                orchestrator.dispatch(
                    ConnectionEvent.BluetoothAccessGranted
                )
            }

            ConnectionIntent.Refresh -> {
                discovered.clear()
                orchestrator.dispatch(
                    ConnectionEvent.RefreshRequested
                )
                if (!scanActive) {
                    scanRestart.value += 1
                }
            }

            is ConnectionIntent.SelectDevice -> orchestrator.dispatch(
                ConnectionEvent.DeviceSelected(
                    intent.deviceId
                )
            )

            ConnectionIntent.BecameVisible -> visible.value = true

            ConnectionIntent.BecameHidden -> visible.value = false

            ConnectionIntent.Logout -> beginLogout()
        }
    }

    override fun onCleared() {
        scanCollection.cancel()
        orchestrator.close()
    }

    private fun scanFlow(): Flow<BluetoothDeviceInfo> = port.scanDevices().onStart {
        discovered.clear()
        orchestrator.dispatch(
            ConnectionEvent.DevicesUpdated(emptyList())
        )
        scanActive = true
    }.onCompletion {
        scanActive = false
    }.catch { error ->
        orchestrator.dispatch(
            ConnectionEvent.ScanFailed(
                error.message?.takeIf(String::isNotBlank) ?: "蓝牙扫描失败"
            )
        )
    }

    private fun beginLogout() {
        if (logoutStarted) return
        logoutStarted = true
        report(ConnectionOutput.LogoutRequested)
        viewModelScope.launch {
            scanCollection.cancelAndJoin()
            orchestrator.dispatch(
                ConnectionEvent.LogoutRequested
            )
        }
    }

    private fun reportRootOutput(
        previous: ConnectionState, event: ConnectionEvent, current: ConnectionState
    ) {
        if (previous != ConnectionState.LogoutReady && current == ConnectionState.LogoutReady) report(
            ConnectionOutput.Stopped
        )
    }

    private data class ScanRequest(
        val enabled: Boolean, val restart: Int
    )

    companion object {
        fun factory(
            userId: String, port: ConnectionPort, report: (ConnectionOutput) -> Unit = {}
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
                    userId, port, report
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

            ConnectionState.EndingSession -> ConnectionPhase.EndingSession
            ConnectionState.LogoutReady -> ConnectionPhase.LogoutReady
        },
        devices = bluetoothDevices.sortedWith(compareByDescending<BluetoothDeviceInfo> { it.id == remembered }.thenByDescending {
            it.rssi ?: Int.MIN_VALUE
        }.thenBy { it.name ?: it.id }).map {
            DeviceItemUi(
                id = it.id,
                name = it.name ?: it.id,
                rssi = it.rssi,
                isRemembered = it.id == remembered
            )
        },
        rememberedDeviceId = remembered,
        isRefreshing = false,
        message = message
    )
}
