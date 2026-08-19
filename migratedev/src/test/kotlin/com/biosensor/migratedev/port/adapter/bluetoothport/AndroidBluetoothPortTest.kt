package com.biosensor.migratedev.port.adapter.bluetoothport

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桥接层测试:注入 FakeEngine(纯 Kotlin),验证回调→流分发的全部语义。
 * 覆盖:扫描启停/启动失败(异常信号)/连接终态让位/连接期失败/连接未知设备/超时兜底/
 * 并发广播(替代消费权转移)/会话期断线(精确路由)/未连接发送/取消后回调不达。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidBluetoothPortTest {

    @Test
    fun `scan starts once and cancellation stops once`() = runTest {
        val engine = FakeEngine()
        val port = AndroidBluetoothPort(engine)
        val results = mutableListOf<BluetoothDeviceInfo>()

        val collection = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            port.scanDevices().toList(results)
        }
        engine.deviceFound(info("AA:01"))
        runCurrent()

        assertEquals(1, engine.calls.count { it == "startScan" })
        assertEquals(listOf(info("AA:01")), results)

        collection.cancelAndJoin()
        assertEquals(1, engine.calls.count { it == "stopScan" })
    }

    @Test
    fun `scan start failure closes with business message`() = runTest {
        val engine = FakeEngine(startScanError = BluetoothScanException("启动蓝牙扫描失败"))
        val port = AndroidBluetoothPort(engine)
        var failure: Throwable? = null

        val collection = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            failure = runCatching { port.scanDevices().toList() }.exceptionOrNull()
        }
        collection.join()

        assertTrue(failure is BluetoothScanException)
        assertEquals("启动蓝牙扫描失败", failure?.message)
        assertEquals(0, engine.calls.count { it == "stopScan" })
    }

    @Test
    fun `scan permission failure closes with permission message`() = runTest {
        val engine = FakeEngine(startScanError = SecurityException("缺少蓝牙扫描权限"))
        val port = AndroidBluetoothPort(engine)
        var failure: Throwable? = null

        val collection = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            failure = runCatching { port.scanDevices().toList() }.exceptionOrNull()
        }
        collection.join()

        assertTrue(failure is BluetoothScanException)
        assertEquals("缺少蓝牙扫描或连接权限", failure?.message)
    }

    @Test
    fun `connect ends on terminal event and keeps session`() = runTest {
        val engine = FakeEngine()
        val port = AndroidBluetoothPort(engine)
        val results = mutableListOf<BluetoothEvent>()

        val connection = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            port.execute(
                BluetoothEffect.Connect("AA:02", timeoutMillis = 5_000)
            ).toList(results)
        }
        runCurrent()
        engine.connected(info("AA:02"))
        runCurrent()

        assertEquals(listOf(BluetoothEvent.Connected(info("AA:02"))), results)
        // 终态发出即 close:连接 flow 结束
        connection.join()
        // 连接成功让位:会话保留、不主动断开(故障分析 12 §3.1 教训)
        assertEquals(0, engine.calls.count { it == "disconnect" })

        // 会话保持:命令 flow 仍可走通(DataSent 转发)
        val sendResults = mutableListOf<BluetoothEvent>()
        val send = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            port.execute(
                BluetoothEffect.SendData("ALL\n\r".encodeToByteArray())
            ).toList(sendResults)
        }
        runCurrent()
        engine.dataSent(5)
        runCurrent()
        assertEquals(listOf(BluetoothEvent.DataSent(5)), sendResults)
        send.cancelAndJoin()

        // 断开仍由 Disconnect effect 驱动:显式断开才触发会话清理
        val disconnectResults = port.execute(BluetoothEffect.Disconnect).toList()
        assertEquals(listOf(BluetoothEvent.Disconnected), disconnectResults)
        assertEquals(1, engine.calls.count { it == "disconnect" })
    }

    @Test
    fun `connect failure during connecting maps to ConnectFailed`() = runTest {
        val engine = FakeEngine()
        val port = AndroidBluetoothPort(engine)
        val results = mutableListOf<BluetoothEvent>()

        val connection = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            port.execute(
                BluetoothEffect.Connect("AA:02", timeoutMillis = 5_000)
            ).toList(results)
        }
        runCurrent()
        engine.connectionFailed("AA:02")
        runCurrent()

        assertEquals(listOf(BluetoothEvent.ConnectFailed("蓝牙连接失败")), results)
        connection.join()
    }

    @Test
    fun `connect unknown device emits ConnectFailed`() = runTest {
        val engine = FakeEngine(unknownDeviceIds = setOf("AA:99"))
        val port = AndroidBluetoothPort(engine)

        val results = port.execute(
            BluetoothEffect.Connect("AA:99", timeoutMillis = 5_000)
        ).toList()

        assertEquals(listOf(BluetoothEvent.ConnectFailed("找不到待连接的蓝牙设备")), results)
    }

    @Test
    fun `connect timeout disconnects target`() = runTest {
        val engine = FakeEngine()
        val port = AndroidBluetoothPort(engine)
        val results = mutableListOf<BluetoothEvent>()

        val connection = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            port.execute(
                BluetoothEffect.Connect("AA:03", timeoutMillis = 1_000)
            ).toList(results)
        }
        advanceTimeBy(1_000)
        runCurrent()
        connection.join()

        assertEquals(listOf(BluetoothEvent.ConnectTimeout), results)
        assertEquals(1, engine.calls.count { it == "disconnect" })
    }

    @Test
    fun `concurrent command flows both receive broadcast`() = runTest {
        // 消费权转移的替代验证:两个命令 flow 并发订阅,事件广播给两者,互不抢道
        val engine = FakeEngine()
        val port = AndroidBluetoothPort(engine)
        engine.connected(info("AA:01"))   // 预置已连接(引擎内部态)

        val a = mutableListOf<BluetoothEvent>()
        val b = mutableListOf<BluetoothEvent>()
        val jobA = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            port.execute(BluetoothEffect.SendData("A".encodeToByteArray())).toList(a)
        }
        val jobB = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            port.execute(BluetoothEffect.SendData("B".encodeToByteArray())).toList(b)
        }
        runCurrent()
        engine.dataSent(3)
        engine.dataReceived("X".encodeToByteArray())
        runCurrent()

        val expected = listOf(
            BluetoothEvent.DataSent(3),
            BluetoothEvent.DataReceived("X".encodeToByteArray())
        )
        // data class 对 ByteArray 字段用引用相等,需逐元素比较
        assertEventsEqual(expected, a)
        assertEventsEqual(expected, b)
        jobA.cancelAndJoin()
        jobB.cancelAndJoin()
    }

    @Test
    fun `connection lost broadcasts Disconnected to session subscribers`() = runTest {
        val engine = FakeEngine()
        val port = AndroidBluetoothPort(engine)
        engine.connected(info("AA:01"))

        val results = mutableListOf<BluetoothEvent>()
        val send = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            port.execute(
                BluetoothEffect.SendData("ALL".encodeToByteArray())
            ).toList(results)
        }
        runCurrent()
        engine.disconnected("AA:01")
        runCurrent()

        assertEquals(listOf(BluetoothEvent.Disconnected), results)
        send.cancelAndJoin()
    }

    @Test
    fun `sendData when not connected emits ConnectFailed`() = runTest {
        val engine = FakeEngine()   // 未连接
        val port = AndroidBluetoothPort(engine)

        val results = port.execute(
            BluetoothEffect.SendData("X".encodeToByteArray())
        ).toList()

        assertEquals(listOf(BluetoothEvent.ConnectFailed("未连接,无法发送")), results)
    }

    @Test
    fun `late callback after cancellation is ignored`() = runTest {
        val engine = FakeEngine()
        val port = AndroidBluetoothPort(engine)
        val results = mutableListOf<BluetoothDeviceInfo>()

        val collection = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            port.scanDevices().toList(results)
        }
        collection.cancelAndJoin()
        engine.deviceFound(info("AA:LATE"))
        runCurrent()

        assertEquals(emptyList<BluetoothDeviceInfo>(), results)
    }

    /** DataReceived 的 ByteArray 按内容比较,其余事件走 data class equals。 */
    private fun assertEventsEqual(expected: List<BluetoothEvent>, actual: List<BluetoothEvent>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) ->
            if (e is BluetoothEvent.DataReceived && a is BluetoothEvent.DataReceived) {
                assertEquals(e.data.toList(), a.data.toList())
            } else {
                assertEquals(e, a)
            }
        }
    }

    private fun info(
        id: String, name: String? = "BT24-S", rssi: Int = -30
    ) = BluetoothDeviceInfo(id = id, name = name, isBle = true, rssi = rssi)

    private class FakeEngine(
        private val startScanError: Throwable? = null,
        private val unknownDeviceIds: Set<String> = emptySet(),
    ) : BluetoothEngine {
        val calls = mutableListOf<String>()
        var engineListener: BluetoothEngineListener? = null
        var connected = false

        override fun setListener(listener: BluetoothEngineListener) {
            engineListener = listener
        }

        override fun startScan() {
            calls += "startScan"
            startScanError?.let { throw it }
        }

        override fun stopScan() {
            calls += "stopScan"
        }

        override fun connect(deviceId: String) {
            calls += "connect:$deviceId"
            if (deviceId in unknownDeviceIds) {
                throw IllegalArgumentException("找不到待连接的蓝牙设备")
            }
        }

        override fun disconnect() {
            calls += "disconnect"
            connected = false
        }

        override fun sendData(data: ByteArray) {
            calls += "sendData"
            if (!connected) throw IllegalStateException("未连接,无法发送")
        }

        override fun requestMtu(mtu: Int) {
            calls += "requestMtu"
        }

        fun deviceFound(info: BluetoothDeviceInfo) {
            engineListener?.onDeviceFound(info)
        }

        fun connected(info: BluetoothDeviceInfo) {
            connected = true
            engineListener?.onConnected(info)
        }

        fun connectionFailed(deviceId: String?) {
            engineListener?.onConnectionFailed(deviceId)
        }

        fun disconnected(deviceId: String?) {
            connected = false
            engineListener?.onDisconnected(deviceId)
        }

        fun dataSent(bytesSent: Int) {
            engineListener?.onDataSent(bytesSent)
        }

        fun dataReceived(data: ByteArray) {
            engineListener?.onDataReceived(data)
        }
    }
}
