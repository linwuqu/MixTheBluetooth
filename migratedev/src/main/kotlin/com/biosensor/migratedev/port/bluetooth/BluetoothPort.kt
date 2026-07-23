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

data class LegacyBluetoothParameters(
    val bleSendDelayState: Int = 1,
    val regularSendIntervalLevel: Int = 0,
    val bleReadBufferBytes: Int = 1_000,
    val classicReadBufferBytes: Int = 1_500,
    val receiveQuietPeriodMillis: Int = 100,
    val checkNewline: Boolean = true
)

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
    // Port 主要维护这两个可变量 所有可能并发访问这两个变量的地方 都包在 synchronized(lock) 里
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
        // channel: SendChannel<BluetoothResult> SendChannel<T>是发送端 对应Flow的收集端会收到
        // 后续发现设备时，回调需要通过它把事件推给 Flow 的收集者。
        val output = channel
        // 进入临界区
        val accepted = synchronized(lock) {
            // 说明已经有一个正在运行的扫描 Flow
            if (scanOutput != null) {
                false
            } else {
                scannedDevices.clear()
                // 在这里闭包捕获到全局变量 scanOutput
                scanOutput = output
                true
            }
        }

        // 互斥失败发送异常直接退出
        if (!accepted) {
            trySend(BluetoothResult.ScanFailed("蓝牙扫描已经在进行中"))
            close()
            return@callbackFlow
        }

        val started = runCatching { client.startMixedScan() }
        if (started.isFailure || !started.getOrDefault(false)) {
            // client.startMixedScan() 是一个耗时较长的操作 失败情况下对于信道的清空应该确保这段时间内没有其他操作修改信道对象
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
        // 这里与前面的awaitClose进行配合 ownsScan为false 不会重复调用stopScan()
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
            // 加入新 Device
            scannedDevices[device.id] = device.toDeviceInfo()
            scanOutput to scannedDevices.values.toList()
            // (output, devices): tuple(SendChannel<BluetoothResult>?, List<LibraryBluetoothDevice>
        }
        // 只在scanOutput不为空的时候发送找到设备的通知 前面说了scanOutput不为空说明“有一个正在进行的、且有人在监听的扫描会话”
        // 所以底层通知只在我们主动监听的时候才上报 否则静默丢弃
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
