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
    /**
     * 内部信号(非 UI):断线自动重连,Root 转发(Cgm 域上报)。
     * 消费权转移后连接域听不到断线事件——断线由命令期消费者(Cgm 命令 flow)发现,
     * 经 Root 转回驱动重连(实测 2026-08-07:svc bluetooth disable 仅 Cgm.Read 收到断线)。
     */
    data object Reconnect : ConnectionIntent
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
    /** 连接成功(架构 07 §10.4):Root 据此进入 Cgm 页面。 */
    data class Connected(val deviceId: String) : ConnectionOutput
    /** 重连失败:Root 转发 Cgm 域,闭环终止(不再悬挂)。 */
    data class ReconnectFailed(val message: String) : ConnectionOutput
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
    /** 断线重连进行中(Reconnect intent 置位,Connected/Failed 复位):用于区分初次连接失败与重连失败。 */
    private var reconnecting = false

    private val orchestrator = WorkflowOrchestrator(
        initialState = ConnectionState.Idle,
        decisionCore = ConnectionDecisionCore,
        effectExecutor = port::execute,
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

            // 内部信号:断线自动重连。连接域状态可能仍是 Connected(消费权转移,没收到断线)——
            // 先补断线信号让状态机进入 ConnectionFailed(onTransition 自动重连),或直接重连(已是失败态)
            ConnectionIntent.Reconnect -> {
                if (logoutStarted) return
                when (val current = orchestrator.state.value) {
                    is ConnectionState.Connected -> {
                        reconnecting = true
                        orchestrator.dispatch(ConnectionEvent.DeviceDisconnected)
                        // 顺序入队:DeviceDisconnected(Connected → ConnectionFailed)先处理,
                        // onTransition 检到"Connected → ConnectionFailed"自动重连
                    }
                    is ConnectionState.ConnectionFailed -> {
                        reconnecting = true
                        orchestrator.dispatch(ConnectionEvent.ConnectRequested(current.deviceId))
                    }
                    else -> Unit
                }
            }
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
        // 进入 Connected(含断线重连成功)→ 上报,Root 进入 Cgm 页面
        if (previous !is ConnectionState.Connected && current is ConnectionState.Connected) {
            reconnecting = false
            report(ConnectionOutput.Connected(current.device.id))
        }
        // 断线(Connected → ConnectionFailed)→ 默认自动重连,不静默失败:
        // 覆盖两条路径——连接域自己收到断线(主动断开/连接期消费者存活),或 Reconnect intent
        // 补发的断线信号(消费权转移后 Cgm 域上报,见 submit)。绕过 requestConnection 守卫:
        // 设备可能不在 discovered,自动重连是重复动作不受 autoConnectConsumed 一次性限制
        if (previous is ConnectionState.Connected && current is ConnectionState.ConnectionFailed) {
            if (!logoutStarted) {
                reconnecting = true
                orchestrator.dispatch(ConnectionEvent.ConnectRequested(current.deviceId))
            }
        }
        // 重连失败(Connecting → ConnectionFailed 且确在重连中)→ 上报闭环终止
        if (previous is ConnectionState.Connecting &&
            current is ConnectionState.ConnectionFailed && reconnecting
        ) {
            reconnecting = false
            report(ConnectionOutput.ReconnectFailed(current.message))
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
