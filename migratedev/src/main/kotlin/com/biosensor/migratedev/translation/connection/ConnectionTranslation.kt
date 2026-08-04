package com.biosensor.migratedev.translation.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.biosensor.migratedev.decisioncore.connection.ConnectionDecisionCore
import com.biosensor.migratedev.decisioncore.connection.ConnectionEvent
import com.biosensor.migratedev.decisioncore.connection.ConnectionState
import com.biosensor.migratedev.orchestrator.WorkflowOrchestrator
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import com.biosensor.migratedev.port.connection.BindingSnapshot
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

/**
 * ConnectionTranslation 是蓝牙扫描和连接部分的重要枢纽
 * 除了负责翻译层的本职工作建立 Orchestrator 外
 * 还负责了三个协程任务的管理
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionTranslation private constructor(
    private val userId: String,
    private val port: ConnectionPort,
    private val report: (ConnectionOutput) -> Unit
) : ViewModel(), Translation<ConnectionIntent, ConnectionUiState> {

    private val bluetoothAccessGranted = MutableStateFlow(false)
    private val visible = MutableStateFlow(false)
    private val scanRestart = MutableStateFlow(0)
    private val binding = MutableStateFlow<BindingSnapshot>(BindingSnapshot.Loading)
    private val devices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    private val discovered = linkedMapOf<String, BluetoothDeviceInfo>()
    private var scanActive = false
    private var autoConnectConsumed = false
    private var logoutStarted = false

    private val orchestrator = WorkflowOrchestrator(
        initialState = ConnectionState.Idle,
        decisionCore = ConnectionDecisionCore,
        effectExecutor = port,
        scope = viewModelScope,
        logTag = "Connection.Workflow",
        onTransition = ::onTransition
    )

    override val uiState: StateFlow<ConnectionUiState> = combine(
        orchestrator.state, binding, devices
    ) { state, currentBinding, currentDevices ->
        state.toUiState(currentBinding, currentDevices)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ConnectionState.Idle.toUiState(BindingSnapshot.Loading, emptyList())
    )

    private val bindingCollection: Job = viewModelScope.launch {
        port.readBinding(userId).catch { error ->
            emit(
                BindingSnapshot.Failed(
                    error.message?.takeIf(String::isNotBlank) ?: "读取设备绑定失败"
                )
            )
        }.collect { binding.value = it }
    }

    private val scanCollection: Job = viewModelScope.launch {
        combine(
            bluetoothAccessGranted, visible, orchestrator.state, scanRestart
        ) { access, isVisible, state, restart ->
            ScanRequest(
                enabled = access && isVisible && state is ConnectionState.Scanning,
                restart = restart
            )
        }.distinctUntilChanged().flatMapLatest { request ->
            if (request.enabled) scanFlow() else emptyFlow()
        }.collect { device ->
            discovered[device.id] = device
            devices.value = discovered.values.toList()
        }
    }

    private val autoConnectCollection: Job = viewModelScope.launch {
        combine(binding, devices) { currentBinding, currentDevices ->
            (currentBinding as? BindingSnapshot.Found)?.deviceId?.takeIf { id ->
                currentDevices.any { it.id == id }
            }
        }.distinctUntilChanged().collect { deviceId ->
            if (deviceId != null) requestConnection(deviceId, automatic = true)
        }
    }

    init {
        orchestrator.dispatch(ConnectionEvent.ConnectionCreated(userId))
    }

    override fun submit(intent: ConnectionIntent) {
        when (intent) {
            ConnectionIntent.BluetoothAccessGranted -> {
                bluetoothAccessGranted.value = true
                orchestrator.dispatch(ConnectionEvent.BluetoothAccessGranted)
            }

            ConnectionIntent.Refresh -> {
                clearDevices()
                orchestrator.dispatch(ConnectionEvent.RefreshRequested)
                if (!scanActive) scanRestart.value += 1
            }

            is ConnectionIntent.SelectDevice -> requestConnection(
                intent.deviceId, automatic = false
            )

            ConnectionIntent.BecameVisible -> visible.value = true
            ConnectionIntent.BecameHidden -> visible.value = false
            ConnectionIntent.Logout -> beginLogout()
        }
    }

    override fun onCleared() {
        bindingCollection.cancel()
        scanCollection.cancel()
        autoConnectCollection.cancel()
        orchestrator.close()
    }

    private fun scanFlow(): Flow<BluetoothDeviceInfo> = port.scanDevices().onStart {
        clearDevices()
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

    private fun requestConnection(
        deviceId: String, automatic: Boolean
    ) {
        if (logoutStarted) return
        if (automatic) {
            if (autoConnectConsumed) return
            autoConnectConsumed = true
        } else {
            if (deviceId !in discovered) return
            autoConnectConsumed = true
        }
        orchestrator.dispatch(ConnectionEvent.ConnectRequested(deviceId))
    }

    private fun clearDevices() {
        discovered.clear()
        devices.value = emptyList()
    }

    private fun beginLogout() {
        if (logoutStarted) return
        logoutStarted = true
        report(ConnectionOutput.LogoutRequested)
        viewModelScope.launch {
            scanCollection.cancelAndJoin()
            orchestrator.dispatch(ConnectionEvent.LogoutRequested)
        }
    }

    private fun onTransition(
        previous: ConnectionState, event: ConnectionEvent, current: ConnectionState
    ) {
        if (event is ConnectionEvent.BindingSaved) {
            binding.value = BindingSnapshot.Found(event.deviceId)
        }
        if (previous != ConnectionState.LogoutReady && current == ConnectionState.LogoutReady) {
            report(ConnectionOutput.Stopped)
        }
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
                    modelClass.isAssignableFrom(ConnectionTranslation::class.java)
                )
                return ConnectionTranslation(userId, port, report) as T
            }
        }
    }
}

private fun ConnectionState.toUiState(
    binding: BindingSnapshot, scannedDevices: List<BluetoothDeviceInfo>
): ConnectionUiState {
    val remembered = (binding as? BindingSnapshot.Found)?.deviceId
    val visibleDevices = when (this) {
        is ConnectionState.Scanning, is ConnectionState.Connecting, is ConnectionState.ConnectionFailed -> scannedDevices

        is ConnectionState.Connected -> listOf(device)
        else -> emptyList()
    }
    val stateMessage = when (this) {
        is ConnectionState.Scanning -> message
        is ConnectionState.Connected -> bindingMessage
        is ConnectionState.ConnectionFailed -> message
        else -> null
    }
    val bindingMessage = (binding as? BindingSnapshot.Failed)?.message
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
        devices = visibleDevices.sortedWith(compareByDescending<BluetoothDeviceInfo> { it.id == remembered }.thenByDescending {
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
        message = stateMessage ?: bindingMessage
    )
}
