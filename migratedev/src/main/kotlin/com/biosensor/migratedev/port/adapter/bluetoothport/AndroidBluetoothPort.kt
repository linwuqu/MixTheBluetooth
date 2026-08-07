package com.biosensor.migratedev.port.adapter.bluetoothport

import android.app.Application
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
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

        override fun onDataReceived(data: ByteArray) {
            // 设备推送字节块:路由到当前连接会话的事件通道(架构 07 §1.4 单一通道)
            val session = synchronized(lock) { connectionSession }
            session?.output?.trySend(BluetoothEvent.DataReceived(data))
        }

        override fun onDataSent(bytesSent: Int) {
            // readNumber 无设备参数(SDK 限制)→ 只能路由给当前连接会话,串行会话天然满足
            val session = synchronized(lock) { connectionSession }
            session?.output?.trySend(BluetoothEvent.DataSent(bytesSent))
        }

        override fun onMtuChanged(mtu: Int) {
            val session = synchronized(lock) { connectionSession }
            session?.output?.trySend(BluetoothEvent.MtuChanged(mtu))
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
        is BluetoothEffect.SendData -> sendData(effect.data)
        is BluetoothEffect.RequestMtu -> requestMtu(effect.mtu)
    }

    /**
     * 发送指令数据:转发当前会话事件通道给调用方,直到命令结束。
     * 消费权转移(架构 07 §1.7/§3.3):新命令启动时 cancel 旧收集器——旧 flow 收不到事件
     * 自然失效,不会出现双命令并发收集,通道时分复用得以维持。
     */
    private fun sendData(data: ByteArray): Flow<BluetoothEvent> = callbackFlow {
        val session = synchronized(lock) {
            connectionSession?.takeIf { it.connected }
        } ?: run {
            trySend(BluetoothEvent.ConnectFailed("未连接,无法发送"))
            close()
            return@callbackFlow
        }
        val sent = runCatching { client.sendData(session.deviceId, data) }
        if (sent.isFailure || !sent.getOrDefault(false)) {
            trySend(BluetoothEvent.ConnectFailed("发送失败"))
            close()
            return@callbackFlow
        }

        synchronized(lock) {
            session.dataCollector?.cancel()   // 消费权转移:夺走通道收集权
        }
        val collector = launch {
            synchronized(lock) {
                session.dataCollector = coroutineContext[Job]
            }
            session.output.receiveAsFlow().collect { event ->
                when (event) {
                    // 命令期事件:全部转发(类型即身份,不靠顺序猜)
                    is BluetoothEvent.DataReceived,
                    is BluetoothEvent.DataSent,
                    is BluetoothEvent.MtuChanged,
                    is BluetoothEvent.Disconnected -> trySend(event)
                    // 连接期事件在命令期不会出现,丢弃是安全兜底
                    is BluetoothEvent.Connected,
                    is BluetoothEvent.ConnectFailed,
                    BluetoothEvent.ConnectTimeout -> Unit
                }
            }
        }
        awaitClose {
            collector.cancel()
            synchronized(lock) {
                if (session.dataCollector === coroutineContext[Job]) {
                    session.dataCollector = null
                }
            }
        }
    }

    private fun requestMtu(mtu: Int): Flow<BluetoothEvent> = flow {
        val deviceId = synchronized(lock) {
            connectionSession?.deviceId
        } ?: return@flow
        runCatching { client.requestMtu(deviceId, mtu) }
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

        // ProducerScope.channel 声明为 SendChannel,运行时即 Channel(唯一通道,命令 flow 也用它)
        // 统一事件通道(架构 07 §1.4):独立于本 flow 的 channel,由 BUFFERED(容量 64)创建。
        // 连接期由下方 collector 消费;命令期由 sendData flow 消费;互不竞争。
        val session = ConnectionSession(deviceId = deviceId, output = Channel())
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

        // 连接期收集者(架构 07 §1.4):本 flow 的 channel 独立于 session.output,
        // 不会与命令 flow 竞争。只转发连接期事件;终态事件发出即 close——连接 flow
        // 让出消费者地位,命令 flow 启动后独占 session.output。
        val collector = launch {
            session.output.receiveAsFlow().collect { event ->
                when (event) {
                    is BluetoothEvent.Connected,
                    is BluetoothEvent.ConnectFailed,
                    BluetoothEvent.ConnectTimeout,
                    is BluetoothEvent.Disconnected -> {
                        if (trySend(event).isSuccess) close()
                    }
                    // 命令期事件在连接期不会出现,丢弃是安全兜底
                    is BluetoothEvent.DataReceived,
                    is BluetoothEvent.DataSent,
                    is BluetoothEvent.MtuChanged -> Unit
                }
            }
        }

        awaitClose {
            timeout.cancel()
            collector.cancel()
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
        // Channel 兼具 Send/Receive 两侧:监听方 trySend 投递,命令方 receiveAsFlow 消费
        val output: Channel<BluetoothEvent>,
        var connected: Boolean = false,
        var dataCollector: Job? = null
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
