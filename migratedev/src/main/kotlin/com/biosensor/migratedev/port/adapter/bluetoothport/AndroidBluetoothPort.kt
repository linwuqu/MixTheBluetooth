package com.biosensor.migratedev.port.adapter.bluetoothport

import android.app.Application
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/**
 * AndroidBluetoothPort 是蓝牙端口的基础实现
 * 目前有两个功能 汇集了 AndroidNativeBleScanner 的扫描能力 & HcBluetoothLibraryClient 的蓝牙库能力
 * 之所以扫描独立出来是想使用安卓原生的 callbackFlow 进行控制
 *
 * 1. 对于扫描
 * 重写了 scanDevices 通过在 callbackFlow 上实现 listener: NativeBleScanListener 的所有回调
 * 并使用 channel 进行信息流订阅获取全部扫描信息
 *
 * 2. 对于蓝牙库
 * 在 execute 中进行业务路由 实现了 BluetoothEffect 两个路由的业务实现 connect & disconnect
 *
 * 值得注意的是所有的业务函数都是建立在冷流中的 所以都有很好的性质: 即用即关、用到时才主动释放(emit、trySend)和收集
 */
class AndroidBluetoothPort internal constructor(
    private val client: BluetoothLibraryClient,
    private val scanner: NativeBleScanner,
    private val filter: BluetoothAdvertisementFilter = Bt24AdvertisementFilter
) : BluetoothPort {

    constructor(
        application: Application,
        parameters: LegacyBluetoothParameters = LegacyBluetoothParameters(),
        filter: BluetoothAdvertisementFilter = Bt24AdvertisementFilter
    ) : this(
        client = HcBluetoothLibraryClient(application, parameters),
        scanner = AndroidNativeBleScanner(application),
        filter = filter
    )

    private val lock = Any()
    private val discovered = linkedMapOf<String, ScannedBleDevice>()
    private var activeScan: ScanCollection? = null
    private var connectionSession: ConnectionSession? = null

    private val libraryListener = object : BluetoothLibraryListener {
        override fun onConnected(device: BluetoothDeviceInfo) {
            handleConnected(device)
        }

        override fun onConnectionLost(deviceId: String?) {
            handleConnectionLost(deviceId)
        }
    }

    init {
        client.setListener(libraryListener)
    }

    override fun scanDevices(): Flow<BluetoothDeviceInfo> = callbackFlow {
        lateinit var listener: NativeBleScanListener
        listener = object : NativeBleScanListener {
            override fun onDeviceFound(device: ScannedBleDevice) {
                if (!filter.matches(device.advertisement)) return
                val isCurrent = synchronized(lock) {
                    if (activeScan?.listener !== listener) false
                    else {
                        discovered[device.info.id] = device
                        true
                    }
                }
                if (isCurrent) {
                    trySend(device.info)
                }
            }

            override fun onScanFailed(failure: BluetoothScanException) {
                finishActiveScan(expected = listener, failure = failure)
            }
        }

        val accepted = synchronized(lock) {
            if (activeScan != null) false
            else {
                discovered.clear()
                activeScan = ScanCollection(listener = listener, output = channel)
                true
            }
        }
        if (!accepted) {
            close(BluetoothScanException("蓝牙扫描已经在进行中"))
            return@callbackFlow
        }

        val started = runCatching { scanner.start(listener) }
        if (started.isFailure) {
            finishActiveScan(
                expected = listener,
                failure = started.exceptionOrNull()?.toBluetoothScanException()
                    ?: BluetoothScanException("启动蓝牙扫描失败")
            )
        } else Timber.tag(BLUETOOTH_TAG).i("native BLE scan started")


        awaitClose {
            finishActiveScan(expected = listener)
        }
    }

    override fun execute(
        effect: BluetoothEffect
    ): Flow<BluetoothEvent> = when (effect) {
        is BluetoothEffect.Connect -> connect(effect.deviceId, effect.timeoutMillis)
        BluetoothEffect.Disconnect -> disconnect()
    }

    private fun finishActiveScan(
        expected: NativeBleScanListener? = null, failure: Throwable? = null
    ) {
        val scan = synchronized(lock) {
            activeScan?.takeIf { expected == null || it.listener === expected }
                ?.also { activeScan = null }
        } ?: return

        runCatching { scanner.stop(scan.listener) }
        scan.output.close(failure)
        Timber.tag(BLUETOOTH_TAG).i("native BLE scan stopped")
    }

    private fun connect(
        deviceId: String, timeoutMillis: Long
    ): Flow<BluetoothEvent> = callbackFlow {
        require(timeoutMillis > 0) { "连接超时时间必须大于 0" }
        finishActiveScan()

        val target = synchronized(lock) { discovered[deviceId] }
        if (target == null) {
            trySend(BluetoothEvent.ConnectFailed("找不到待连接的蓝牙设备"))
            close()
            return@callbackFlow
        }

        val session = ConnectionSession(deviceId = deviceId, output = channel)
        val accepted = synchronized(lock) {
            if (connectionSession != null) false
            else {
                connectionSession = session
                true
            }
        }
        if (!accepted) {
            trySend(BluetoothEvent.ConnectFailed("已有蓝牙连接会话正在进行"))
            close()
            return@callbackFlow
        }

        val started = runCatching { client.connect(target) }
        if (started.isFailure || !started.getOrDefault(false)) {
            synchronized(lock) {
                if (connectionSession === session) connectionSession = null
            }
            trySend(
                BluetoothEvent.ConnectFailed(
                    started.exceptionOrNull()?.bluetoothFailureMessage("找不到待连接的蓝牙设备")
                        ?: "找不到待连接的蓝牙设备"
                )
            )
            close()
            return@callbackFlow
        }

        val timeout = launch {
            delay(timeoutMillis.milliseconds)
            val timedOut = synchronized(lock) {
                if (connectionSession === session && !session.connected) {
                    connectionSession = null
                    true
                } else false
            }
            if (timedOut) {
                trySend(BluetoothEvent.ConnectTimeout)
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
                } else false
            }
            if (ownsConnection) {
                runCatching {
                    client.disconnect(deviceId)
                }
            }
        }
    }

    private fun disconnect(): Flow<BluetoothEvent> = flow {
        finishActiveScan()
        val session = synchronized(lock) {
            connectionSession.also {
                connectionSession = null
            }
        }
        runCatching { client.disconnect(session?.deviceId) }
        session?.output?.close()
        emit(BluetoothEvent.Disconnected)
    }

    private fun handleConnected(device: BluetoothDeviceInfo) {
        val session = synchronized(lock) {
            connectionSession?.takeIf { it.deviceId == device.id }?.also { it.connected = true }
        }
        session?.output?.trySend(BluetoothEvent.Connected(device))
    }

    private fun handleConnectionLost(deviceId: String?) {
        val session = synchronized(lock) {
            connectionSession?.takeIf { deviceId == null || it.deviceId == deviceId }
                ?.also { connectionSession = null }
        } ?: return

        session.output.trySend(
            if (session.connected) {
                BluetoothEvent.Disconnected
            } else BluetoothEvent.ConnectFailed("蓝牙连接失败")
        )
        session.output.close()
    }

    private class ScanCollection(
        val listener: NativeBleScanListener, val output: SendChannel<BluetoothDeviceInfo>
    )

    private class ConnectionSession(
        val deviceId: String,
        val output: SendChannel<BluetoothEvent>,
        var connected: Boolean = false
    )

    private companion object {
        const val BLUETOOTH_TAG = "Connection.Bluetooth"
    }
}

private fun Throwable.toBluetoothScanException(): BluetoothScanException = BluetoothScanException(
    bluetoothFailureMessage("启动蓝牙扫描失败"), this
)

private fun Throwable.bluetoothFailureMessage(fallback: String): String =
    if (this is SecurityException) {
        "缺少蓝牙扫描或连接权限"
    } else {
        message?.takeIf { it.isNotBlank() } ?: fallback
    }
