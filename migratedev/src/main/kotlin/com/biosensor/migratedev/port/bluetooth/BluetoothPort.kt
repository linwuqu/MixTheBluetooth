package com.biosensor.migratedev.port.bluetooth

import android.app.Activity
import com.biosensor.migratedev.decisioncore.connection.BluetoothDeviceInfo
import com.biosensor.migratedev.port.CommandPort
import com.hc.bluetoothlibrary.AllBluetoothManage
import com.hc.bluetoothlibrary.DeviceModule
import com.hc.bluetoothlibrary.IBluetooth
import com.hc.bluetoothlibrary.tootl.DataMemory
import com.hc.bluetoothlibrary.tootl.ModuleParameters
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

sealed interface BluetoothCommand {
    data object StartScan : BluetoothCommand
    data object StopScan : BluetoothCommand
    data class Connect(val deviceId: String) : BluetoothCommand
    data object Disconnect : BluetoothCommand
}

sealed interface BluetoothResult {
    data class DevicesFound(val devices: List<BluetoothDeviceInfo>) : BluetoothResult
    data object ScanStopped : BluetoothResult
    data class ScanFailed(val message: String) : BluetoothResult
    data class Connected(val device: BluetoothDeviceInfo) : BluetoothResult
    data class ConnectFailed(val message: String) : BluetoothResult
    data object ConnectTimeout : BluetoothResult
    data object Disconnected : BluetoothResult
}

interface BluetoothPort : CommandPort<BluetoothCommand, BluetoothResult>

/**
 * 旧 App 实际使用的蓝牙库通信参数。
 *
 * `AllBluetoothManage(activity) -> 恢复旧值；没有旧值时使用这里的默认值`
 *
 * - `bleSendDelayState=1`：BLE 常规发送基础延时为 `5 + 10 * state = 15ms`；
 * - `regularSendIntervalLevel=0`：常规发送不增加额外的 `level * 10ms` 延时；
 * - 文件速率 API 的 1~4 档分别标称 9600、115200、230400、460800 波特率；
 * - BLE 初始分包载荷为 20 字节，可请求 MTU 为 23~512；
 * - BLE 服务和读写特征由库自动发现，经典蓝牙回退 UUID 为
 *   `00001101-0000-1000-8000-00805F9B34FB`。
 *
 * 旧 App 的正常业务没有调用文件速率和 MTU 设置接口，因此这里也不主动调用，避免把
 * “把数值抄进来”变成实际通信行为变化。
 */
data class LegacyBluetoothParameters(
    val bleSendDelayState: Int = 1,
    val regularSendIntervalLevel: Int = 0,
    val bleReadBufferBytes: Int = 1_000,
    val classicReadBufferBytes: Int = 1_500,
    val receiveQuietPeriodMillis: Int = 100,
    val checkNewline: Boolean = true
)

/**
 * bluetoothlibrary 的真实 Port。
 *
 * `execute(command) -> 本次扫描或连接会话的 Flow<result>`
 *
 * 扫描 Flow 在扫描结束时关闭；连接 Flow 在连接成功后继续存活，直到断开或超时才关闭。
 */
class AndroidBluetoothPort internal constructor(
    private val client: BluetoothLibraryClient,
    private val connectionTimeoutMillis: Long = 15_000
) : BluetoothPort {

    constructor(
        activity: Activity,
        parameters: LegacyBluetoothParameters = LegacyBluetoothParameters(),
        connectionTimeoutMillis: Long = 15_000
    ) : this(
        client = HcBluetoothLibraryClient(activity, parameters),
        connectionTimeoutMillis = connectionTimeoutMillis
    )

    private val lock = Any()
    private val scannedDevices = linkedMapOf<String, BluetoothDeviceInfo>()
    private var scanOutput: SendChannel<BluetoothResult>? = null
    private var connectionSession: ConnectionSession? = null
    private val libraryListener = object : BluetoothLibraryListener {
        override fun onDeviceFound(device: LibraryBluetoothDevice) {
            handleDeviceFound(device)
        }

        override fun onScanFinished() {
            handleScanFinished()
        }

        override fun onConnected(device: LibraryBluetoothDevice) {
            handleConnected(device)
        }

        override fun onConnectionLost(deviceId: String?) {
            handleConnectionLost(deviceId)
        }
    }

    init {
        require(connectionTimeoutMillis > 0) { "连接超时时间必须大于 0" }
        client.setListener(libraryListener)
    }

    override fun execute(command: BluetoothCommand): Flow<BluetoothResult> {
        return when (command) {
            BluetoothCommand.StartScan -> startScan()
            BluetoothCommand.StopScan -> stopScan()
            is BluetoothCommand.Connect -> connect(command.deviceId)
            BluetoothCommand.Disconnect -> disconnect()
        }
    }

    private fun startScan(): Flow<BluetoothResult> = callbackFlow {
        val output = channel
        val accepted = synchronized(lock) {
            if (scanOutput != null) {
                false
            } else {
                scannedDevices.clear()
                scanOutput = output
                true
            }
        }

        if (!accepted) {
            trySend(BluetoothResult.ScanFailed("蓝牙扫描已经在进行中"))
            close()
            return@callbackFlow
        }

        val started = runCatching { client.startMixedScan() }
        if (started.isFailure || !started.getOrDefault(false)) {
            synchronized(lock) {
                if (scanOutput === output) scanOutput = null
            }
            trySend(
                BluetoothResult.ScanFailed(
                    started.exceptionOrNull()?.bluetoothFailureMessage("启动蓝牙扫描失败")
                        ?: "启动蓝牙扫描失败"
                )
            )
            close()
            return@callbackFlow
        }

        awaitClose {
            val ownsScan = synchronized(lock) {
                if (scanOutput === output) {
                    scanOutput = null
                    true
                } else {
                    false
                }
            }
            if (ownsScan) runCatching { client.stopScan() }
        }
    }

    private fun stopScan(): Flow<BluetoothResult> = flow {
        val output = synchronized(lock) {
            scanOutput.also { scanOutput = null }
        }
        runCatching { client.stopScan() }
        output?.trySend(BluetoothResult.ScanStopped)
        output?.close()
        emit(BluetoothResult.ScanStopped)
    }

    private fun connect(deviceId: String): Flow<BluetoothResult> = callbackFlow {
        val session = ConnectionSession(deviceId = deviceId, output = channel)
        val accepted = synchronized(lock) {
            if (connectionSession != null) {
                false
            } else {
                connectionSession = session
                true
            }
        }

        if (!accepted) {
            trySend(BluetoothResult.ConnectFailed("已有蓝牙连接会话正在进行"))
            close()
            return@callbackFlow
        }

        val started = runCatching { client.connect(deviceId) }
        if (started.isFailure || !started.getOrDefault(false)) {
            synchronized(lock) {
                if (connectionSession === session) connectionSession = null
            }
            trySend(
                BluetoothResult.ConnectFailed(
                    started.exceptionOrNull()?.bluetoothFailureMessage("找不到待连接的蓝牙设备")
                        ?: "找不到待连接的蓝牙设备"
                )
            )
            close()
            return@callbackFlow
        }

        val timeout = launch {
            delay(connectionTimeoutMillis)
            val timedOut = synchronized(lock) {
                if (connectionSession === session && !session.connected) {
                    connectionSession = null
                    true
                } else {
                    false
                }
            }
            if (timedOut) {
                trySend(BluetoothResult.ConnectTimeout)
                runCatching { client.disconnect(deviceId) }
                close()
            }
        }

        awaitClose {
            timeout.cancel()
            val ownsConnection = synchronized(lock) {
                if (connectionSession === session) {
                    connectionSession = null
                    true
                } else {
                    false
                }
            }
            if (ownsConnection) runCatching { client.disconnect(deviceId) }
        }
    }

    private fun disconnect(): Flow<BluetoothResult> = flow {
        val session = synchronized(lock) {
            connectionSession.also { connectionSession = null }
        }
        runCatching { client.disconnect(session?.deviceId) }
        session?.output?.trySend(BluetoothResult.Disconnected)
        session?.output?.close()
        emit(BluetoothResult.Disconnected)
    }

    private fun handleDeviceFound(device: LibraryBluetoothDevice) {
        val (output, devices) = synchronized(lock) {
            scannedDevices[device.id] = device.toDeviceInfo()
            scanOutput to scannedDevices.values.toList()
        }
        output?.trySend(BluetoothResult.DevicesFound(devices))
    }

    private fun handleScanFinished() {
        val output = synchronized(lock) {
            scanOutput.also { scanOutput = null }
        }
        output?.trySend(BluetoothResult.ScanStopped)
        output?.close()
    }

    private fun handleConnected(device: LibraryBluetoothDevice) {
        val session = synchronized(lock) {
            connectionSession
                ?.takeIf { it.deviceId == device.id }
                ?.also { it.connected = true }
        }
        session?.output?.trySend(BluetoothResult.Connected(device.toDeviceInfo()))
    }

    private fun handleConnectionLost(deviceId: String?) {
        val session = synchronized(lock) {
            connectionSession
                ?.takeIf { deviceId == null || it.deviceId == deviceId }
                ?.also { connectionSession = null }
        }
        session ?: return

        val result = if (session.connected) {
            BluetoothResult.Disconnected
        } else {
            BluetoothResult.ConnectFailed("蓝牙连接失败")
        }
        session.output.trySend(result)
        session.output.close()
    }

    private class ConnectionSession(
        val deviceId: String,
        val output: SendChannel<BluetoothResult>,
        var connected: Boolean = false
    )
}

internal data class LibraryBluetoothDevice(
    val id: String,
    val name: String?,
    val isBle: Boolean,
    val rssi: Int?
)

internal fun LibraryBluetoothDevice.toDeviceInfo() = BluetoothDeviceInfo(
    id = id,
    name = name,
    isBle = isBle,
    rssi = rssi
)

internal interface BluetoothLibraryListener {
    fun onDeviceFound(device: LibraryBluetoothDevice)
    fun onScanFinished()
    fun onConnected(device: LibraryBluetoothDevice)
    fun onConnectionLost(deviceId: String?)
}

internal interface BluetoothLibraryClient {
    fun setListener(listener: BluetoothLibraryListener)
    fun startMixedScan(): Boolean
    fun stopScan()
    fun connect(deviceId: String): Boolean
    fun disconnect(deviceId: String?)
}

/** 将 AllBluetoothManage 的全局回调收拢为 Port 可管理的四类事件。 */
internal class HcBluetoothLibraryClient(
    private val activity: Activity,
    parameters: LegacyBluetoothParameters
) : BluetoothLibraryClient {

    private var listener: BluetoothLibraryListener? = null
    private val nativeDevices = linkedMapOf<String, DeviceModule>()
    private val manager = AllBluetoothManage(activity, object : IBluetooth {
        override fun updateList(deviceModule: DeviceModule?) = publishDevice(deviceModule)

        override fun connectSucceed(deviceModule: DeviceModule?) {
            deviceModule?.let { listener?.onConnected(it.toLibraryDevice()) }
        }

        override fun updateEnd() {
            listener?.onScanFinished()
        }

        override fun updateMessyCode(deviceModule: DeviceModule?) = publishDevice(deviceModule)

        override fun readData(mac: String?, data: ByteArray?) = Unit

        override fun reading(isStart: Boolean) = Unit

        override fun errorDisconnect(deviceModule: DeviceModule?) {
            listener?.onConnectionLost(deviceModule?.mac)
        }

        override fun readNumber(number: Int) = Unit

        override fun readLog(className: String?, data: String?, lv: String?) = Unit

        override fun readVelocity(velocity: Int) = Unit

        override fun callbackMTU(mtu: Int) = Unit
    })

    init {
        // 旧库会先恢复用户已保存的值；只有首次运行没有旧值时，才写入旧 App 的默认参数。
        if (DataMemory(activity).parameters == null) {
            ModuleParameters.setParameters(
                parameters.bleSendDelayState,
                parameters.bleReadBufferBytes,
                parameters.classicReadBufferBytes,
                parameters.receiveQuietPeriodMillis,
                activity
            )
        }
        if (!activity.getSharedPreferences("data", Activity.MODE_PRIVATE)
                .contains("ModuleLevel")
        ) {
            ModuleParameters.saveLevel(parameters.regularSendIntervalLevel, activity)
        }
        ModuleParameters.setNewline(parameters.checkNewline)
    }

    override fun setListener(listener: BluetoothLibraryListener) {
        this.listener = listener
    }

    override fun startMixedScan(): Boolean = manager.mixScan()

    override fun stopScan() = manager.stopScan()

    override fun connect(deviceId: String): Boolean {
        val device = nativeDevices[deviceId] ?: return false
        manager.connect(device)
        return true
    }

    override fun disconnect(deviceId: String?) {
        manager.disconnect(deviceId?.let(nativeDevices::get))
    }

    private fun publishDevice(deviceModule: DeviceModule?) {
        deviceModule ?: return
        nativeDevices[deviceModule.mac] = deviceModule
        listener?.onDeviceFound(deviceModule.toLibraryDevice())
    }

    private fun DeviceModule.toLibraryDevice() = LibraryBluetoothDevice(
        id = mac,
        name = name,
        isBle = isBLE,
        rssi = rssi
    )
}

private fun Throwable.bluetoothFailureMessage(fallback: String): String {
    return if (this is SecurityException) {
        "缺少蓝牙扫描或连接权限"
    } else {
        message?.takeIf { it.isNotBlank() } ?: fallback
    }
}
