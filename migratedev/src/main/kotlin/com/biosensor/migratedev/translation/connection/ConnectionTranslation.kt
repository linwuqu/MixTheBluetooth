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
 * 对于这个文件的 flow 只需要理解四个名词
 * combine 合流
 * flatMapLatest 分流
 * distinctUntilChanged 去重:值相等则不下发
 * stateIn 热化:把派生流变成常驻状态流
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
    private val scanning = MutableStateFlow(false)
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
        orchestrator.state, binding, devices, scanning
    ) { state, currentBinding, currentDevices, isScanning ->
        state.toUiState(currentBinding, currentDevices, isRefreshing = isScanning)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ConnectionState.Idle.toUiState(BindingSnapshot.Loading, emptyList(), isRefreshing = false)
    )

    // 用于从数据库中收集绑定信息
    private val bindingCollection: Job = viewModelScope.launch {
        port.readBinding(userId).catch { error ->
            emit(
                BindingSnapshot.Failed(
                    error.message?.takeIf(String::isNotBlank) ?: "读取设备绑定失败"
                )
            )
        }.collect { binding.value = it }
    }

    // 用于从不同的状态间判断时候开启蓝牙扫描 并收集设备信息
    private val scanCollection: Job = viewModelScope.launch {
        combine(
            bluetoothAccessGranted, visible, orchestrator.state, scanRestart
        ) { access, isVisible, state, restart ->
            ScanRequest(
                // 有条件地开启/关闭蓝牙扫描
                enabled = access && isVisible && state is ConnectionState.Scanning,
                restart = restart
            )
        }.distinctUntilChanged() // 只要组合对象不等就会触发这里 但是 flatMapLatest 还会根据 enabled 情况判断是否启动扫描
            .flatMapLatest { request ->
            if (request.enabled) scanFlow() // 真正调用 port.scanDevices()
            else emptyFlow()
        }.collect { device ->
            discovered[device.id] = device
            devices.value = discovered.values.toList()
        }
        // 所以说这里如果只是 restart + 1 但是其他状态没有更改的话并不会重启扫描流
    }

    // 用于从收集到的列表中进行比较判断决定是否进行自动连接
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
                // if (scanning.value) 此时正在扫描 那么只需要清空发现的设备 让其再发现即可 避免重启
                clearDevices()
                orchestrator.dispatch(ConnectionEvent.RefreshRequested)
                // 此时才需要重启扫描
                if (!scanning.value) scanRestart.value += 1
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

    private fun scanFlow(): Flow<BluetoothDeviceInfo> = port.scanDevices()
        .onStart {
            clearDevices()
            scanning.value = true
        }
        .onCompletion {
            scanning.value = false
        }
        .catch { error ->
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

// 作用是将内部复杂的业务状态变成 ui 可渲染的扁平数据结构
private fun ConnectionState.toUiState(
    binding: BindingSnapshot,
    scannedDevices: List<BluetoothDeviceInfo>,
    isRefreshing: Boolean
): ConnectionUiState {
    // “记住的设备ID”
    val remembered = (binding as? BindingSnapshot.Found)?.deviceId
    // 当前展示什么列表
    // 正在扫描、正在连接、连接失败时：显示所有扫描到的设备
    // 已连接时：只显示当前连接的设备
    // 其他状态（空闲、等待权限、登出等）：不显示任何设备
    val visibleDevices = when (this) {
        is ConnectionState.Scanning, is ConnectionState.Connecting, is ConnectionState.ConnectionFailed -> scannedDevices

        is ConnectionState.Connected -> listOf(device)
        else -> emptyList()
    }
    // 提取可能有的状态信息
    val stateMessage = when (this) {
        is ConnectionState.Scanning -> message
        is ConnectionState.Connected -> bindingMessage
        is ConnectionState.ConnectionFailed -> message
        else -> null
    }
    val bindingMessage = (binding as? BindingSnapshot.Failed)?.message


    return ConnectionUiState(
        // 整理成 ui 所需 phase
        phase = when (this) {
            ConnectionState.Idle, is ConnectionState.AwaitingBluetoothAccess -> ConnectionPhase.AwaitingBluetoothAccess

            is ConnectionState.Scanning -> ConnectionPhase.Scanning
            is ConnectionState.Connecting -> ConnectionPhase.Connecting
            is ConnectionState.Connected -> ConnectionPhase.Connected
            is ConnectionState.ConnectionFailed -> ConnectionPhase.Failed
            ConnectionState.EndingSession -> ConnectionPhase.EndingSession
            ConnectionState.LogoutReady -> ConnectionPhase.LogoutReady
        },
        // 设备列表排序
        // 记住的设备永远排第一 信号强度越强越靠前 同信号强度按名称排序
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
        isRefreshing = isRefreshing,
        message = stateMessage ?: bindingMessage
    )
}
