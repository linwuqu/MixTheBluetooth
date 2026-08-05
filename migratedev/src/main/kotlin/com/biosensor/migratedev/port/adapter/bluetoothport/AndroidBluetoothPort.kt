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
 * 1. 对于扫描(引用计数共享)
 * 一份 native 扫描,多个订阅者挂载:第一个订阅者负责启动(并清空缓存),
 * 最后一个订阅者离开才停止;中间订阅/退订只是集合增删,零 native 成本。
 * 这样 flatMapLatest 的重启 = 引用切换,没有 stop/start 竞争,也不会触发系统限流。
 *
 * 2. 对于蓝牙库
 * 在 execute 中进行业务路由 实现了 BluetoothCommand 两个路由的业务实现 connect & disconnect
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
    private val scanSubscribers = mutableSetOf<ScanSubscriber>()
    private var nativeScanning = false
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

    // ── 扫描:引用计数共享 ───────────────────────────────────

    private val sharedScanListener = object : NativeBleScanListener {
        override fun onDeviceFound(device: ScannedBleDevice) {
            if (!filter.matches(device.advertisement)) return
            synchronized(lock) {
                discovered[device.info.id] = device
            }
            scanSubscribers.toList().forEach { it.onDeviceFound(device) }
        }

        override fun onScanFailed(failure: BluetoothScanException) {
            finishActiveScan(failure = failure)
        }
    }

    override fun scanDevices(): Flow<BluetoothDeviceInfo> = callbackFlow {
        val subscriber = object : ScanSubscriber {
            override fun onDeviceFound(device: ScannedBleDevice) {
                trySend(device.info)
            }

            override fun onScanFailed(failure: BluetoothScanException) {
                close(failure)
            }

            // 会话被主动停止(如连接打断扫描):正常完成,不是失败
            override fun onSessionStopped() {
                close()
            }
        }

        // 第一个订阅者:启动 native(幂等:已在跑则跳过),同时清空会话缓存
        val startFailure = synchronized(lock) {
            val firstSubscriber = scanSubscribers.isEmpty()
            scanSubscribers += subscriber
            if (firstSubscriber && !nativeScanning) {
                discovered.clear()
                val started = runCatching { scanner.start(sharedScanListener) }
                nativeScanning = started.isSuccess
                started.exceptionOrNull()
            } else null
        }
        if (startFailure != null) {
            synchronized(lock) { scanSubscribers -= subscriber }
            close(startFailure.toBluetoothScanException())
            return@callbackFlow
        }
        Timber.tag(BLUETOOTH_TAG).i("原生蓝牙扫描已启动")

        awaitClose {
            val shouldStop = synchronized(lock) {
                scanSubscribers -= subscriber
                if (scanSubscribers.isEmpty() && nativeScanning) {
                    nativeScanning = false
                    true
                } else false
            }
            if (shouldStop) {
                runCatching { scanner.stop(sharedScanListener) }
                Timber.tag(BLUETOOTH_TAG).i("原生蓝牙扫描已停止")
            }
        }
    }

    /** 停止 native 扫描并通知所有订阅者:失败则按失败关闭,否则按正常完成关闭。 */
    private fun finishActiveScan(failure: Throwable? = null) {
        val subscribers = synchronized(lock) {
            if (!nativeScanning) emptyList()
            else {
                nativeScanning = false
                runCatching { scanner.stop(sharedScanListener) }
                Timber.tag(BLUETOOTH_TAG).i("原生蓝牙扫描已停止")
                scanSubscribers.toList().also { scanSubscribers.clear() }
            }
        }
        subscribers.forEach { subscriber ->
            if (failure != null) {
                subscriber.onScanFailed(
                    failure.toBluetoothScanException()
                )
            } else {
                subscriber.onSessionStopped()
            }
        }
    }

    // ── 蓝牙库:连接 / 断开 ─────────────────────────────────

    override fun execute(
        command: BluetoothCommand
    ): Flow<BluetoothResult> = when (command) {
        is BluetoothCommand.Connect -> connect(command.deviceId, command.timeoutMillis)
        BluetoothCommand.Disconnect -> disconnect()
    }

    private fun connect(
        deviceId: String, timeoutMillis: Long
    ): Flow<BluetoothResult> = callbackFlow {
        require(timeoutMillis > 0) { "连接超时时间必须大于 0" }
        finishActiveScan()

        val target = synchronized(lock) { discovered[deviceId] }
        if (target == null) {
            trySend(BluetoothResult.ConnectFailed("找不到待连接的蓝牙设备"))
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
            trySend(BluetoothResult.ConnectFailed("已有蓝牙连接会话正在进行"))
            close()
            return@callbackFlow
        }

        val started = runCatching { client.connect(target) }
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
            delay(timeoutMillis.milliseconds)
            val timedOut = synchronized(lock) {
                if (connectionSession === session && !session.connected) {
                    connectionSession = null
                    true
                } else false
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
                } else false
            }
            if (ownsConnection) {
                runCatching {
                    client.disconnect(deviceId)
                }
            }
        }
    }

    private fun disconnect(): Flow<BluetoothResult> = flow {
        finishActiveScan()
        val session = synchronized(lock) {
            connectionSession.also {
                connectionSession = null
            }
        }
        runCatching { client.disconnect(session?.deviceId) }
        session?.output?.close()
        emit(BluetoothResult.Disconnected)
    }

    private fun handleConnected(device: BluetoothDeviceInfo) {
        val session = synchronized(lock) {
            connectionSession?.takeIf { it.deviceId == device.id }?.also { it.connected = true }
        }
        session?.output?.trySend(BluetoothResult.Connected(device))
    }

    private fun handleConnectionLost(deviceId: String?) {
        val session = synchronized(lock) {
            connectionSession?.takeIf { deviceId == null || it.deviceId == deviceId }
                ?.also { connectionSession = null }
        } ?: return

        session.output.trySend(
            if (session.connected) {
                BluetoothResult.Disconnected
            } else BluetoothResult.ConnectFailed("蓝牙连接失败")
        )
        session.output.close()
    }

    /** 扫描订阅者:在 native 回调之上,增加"会话被主动停止"的通知。 */
    private interface ScanSubscriber : NativeBleScanListener {
        fun onSessionStopped()
    }

    private class ConnectionSession(
        val deviceId: String,
        val output: SendChannel<BluetoothResult>,
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
