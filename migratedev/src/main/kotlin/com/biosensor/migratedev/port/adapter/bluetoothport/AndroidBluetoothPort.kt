package com.biosensor.migratedev.port.adapter.bluetoothport

import android.app.Application
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/**
 * 回调分发器:引擎回调 → 活跃订阅者(callbackFlow 生产者)。
 * 注册/注销发生在 flow 启动/取消阶段,分发发生在引擎回调线程(SDK 主线程 handler)。
 * 事件类型即身份:每个 hub 只分一类事件,各 flow 各取所需,互不抢道——
 * 这就是"消费权转移"的替代:多命令 flow 可并发订阅,事件广播给所有订阅者。
 */
internal class CallbackHub<T> {
    private val subscribers = LinkedHashMap<ProducerScope<*>, (T) -> Unit>()

    fun register(scope: ProducerScope<*>, handler: (T) -> Unit) {
        synchronized(this) { subscribers[scope] = handler }
    }

    fun unregister(scope: ProducerScope<*>) {
        synchronized(this) { subscribers.remove(scope) }
    }

    fun dispatch(event: T) {
        synchronized(this) { subscribers.values.toList() }.forEach { it(event) }
    }
}

class AndroidBluetoothPort internal constructor(
    private val engine: BluetoothEngine,
    private val scanHub: CallbackHub<BluetoothDeviceInfo> = CallbackHub(),
    private val connectHub: CallbackHub<BluetoothEvent> = CallbackHub(),
    private val sessionHub: CallbackHub<BluetoothEvent> = CallbackHub(),
) : BluetoothPort {

    constructor(
        application: Application,
        parameters: LibraryParam = LibraryParam()
    ) : this(BluetoothClient(application, parameters))

    // 引擎 → hub:引擎回调统一吸收,按订阅域分发
    private val engineListener = object : BluetoothEngineListener {
        override fun onDeviceFound(device: BluetoothDeviceInfo) = scanHub.dispatch(device)

        override fun onConnected(device: BluetoothDeviceInfo) =
            connectHub.dispatch(BluetoothEvent.Connected(device))

        // 连接期失败 → 连接 flow 收终态 ConnectFailed;会话期断线 → 命令流收 Disconnected(引擎已区分阶段)
        override fun onConnectionFailed(deviceId: String?) =
            connectHub.dispatch(BluetoothEvent.ConnectFailed("蓝牙连接失败"))

        override fun onDisconnected(deviceId: String?) =
            sessionHub.dispatch(BluetoothEvent.Disconnected)

        override fun onDataReceived(data: ByteArray) =
            sessionHub.dispatch(BluetoothEvent.DataReceived(data))

        override fun onDataSent(bytesSent: Int) =
            sessionHub.dispatch(BluetoothEvent.DataSentAck(bytesSent))

        override fun onMtuChanged(mtu: Int) =
            sessionHub.dispatch(BluetoothEvent.MtuChanged(mtu))
    }

    init {
        engine.setListener(engineListener)
    }

    override fun scanDevices(): Flow<BluetoothDeviceInfo> = callbackFlow {
        scanHub.register(this) { device -> trySend(device) }
        // 同步异常 = 指令没发出(权限/蓝牙不可用),转 BluetoothScanException 关闭流
        try {
            engine.startScan()
        } catch (e: Exception) {
            scanHub.unregister(this)
            close(e.bluetoothScanException())
            return@callbackFlow
        }
        Timber.tag(BLUETOOTH_TAG).i("蓝牙扫描已启动")
        awaitClose {
            scanHub.unregister(this)
            engine.stopScan()
        }
    }

    override fun execute(effect: BluetoothEffect): Flow<BluetoothEvent> = when (effect) {
        is BluetoothEffect.Connect -> connect(effect.deviceId, effect.timeoutMillis)
        BluetoothEffect.Disconnect -> disconnect()
        is BluetoothEffect.SendData -> sendData(effect.data)
        is BluetoothEffect.RequestMtu -> requestMtu(effect.mtu)
    }

    private fun connect(deviceId: String, timeoutMillis: Long): Flow<BluetoothEvent> = callbackFlow {
        require(timeoutMillis > 0) { "连接超时时间必须大于 0" }
        // 连接期订阅:只收终态,终态发出即 close——连接 flow 结束,会话交由引擎持有
        connectHub.register(this) { event ->
            when (event) {
                is BluetoothEvent.Connected,
                is BluetoothEvent.ConnectFailed,
                BluetoothEvent.ConnectTimeout -> {
                    if (trySend(event).isSuccess) close()
                }
                else -> Unit   // 数据/MTU 在连接期不出现,丢弃是安全兜底
            }
        }
        // 同步异常 = 指令没发出(设备不在扫描表),转 ConnectFailed 终态
        try {
            engine.connect(deviceId)
        } catch (e: Exception) {
            connectHub.unregister(this)
            trySend(BluetoothEvent.ConnectFailed(e.message ?: "找不到待连接的蓝牙设备"))
            close()
            return@callbackFlow
        }
        // 超时兜底:桥接层唯一业务补充(SDK 无连接超时回调)
        val timer = launch {
            delay(timeoutMillis.milliseconds)
            trySend(BluetoothEvent.ConnectTimeout)
            engine.disconnect()   // 与现状一致:超时兜底断开
            close()
        }
        awaitClose {
            timer.cancel()
            connectHub.unregister(this)
        }
    }

    private fun sendData(data: ByteArray): Flow<BluetoothEvent> = callbackFlow {
        // 会话期订阅:类型即身份,命令 flow 自行过滤;并发命令各自订阅,互不抢道
        sessionHub.register(this) { event -> trySend(event) }
        // 同步异常 = 指令没发出(未连接),转 ConnectFailed 终态
        try {
            engine.sendData(data)
        } catch (e: Exception) {
            sessionHub.unregister(this)
            trySend(BluetoothEvent.ConnectFailed(e.message ?: "未连接,无法发送"))
            close()
            return@callbackFlow
        }
        awaitClose { sessionHub.unregister(this) }
    }

    private fun disconnect(): Flow<BluetoothEvent> = flow {
        engine.disconnect()
        emit(BluetoothEvent.Disconnected)
    }

    private fun requestMtu(mtu: Int): Flow<BluetoothEvent> = flowOf<BluetoothEvent>().onStart {
        engine.requestMtu(mtu)   // 异常传播给收集者(诊断功能,失败让上层可见)
    }

    private companion object {
        const val BLUETOOTH_TAG = "Connection.Bluetooth"
    }
}

private fun Throwable.bluetoothScanException(): BluetoothScanException =
    if (this is BluetoothScanException) this
    else BluetoothScanException(
        if (this is SecurityException) "缺少蓝牙扫描或连接权限" else (message ?: "启动蓝牙扫描失败"),
        this
    )
