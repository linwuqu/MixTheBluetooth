package com.biosensor.migratedev.port.cgm

import com.biosensor.migratedev.decisioncore.cgm.CgmCommandPurpose
import com.biosensor.migratedev.decisioncore.cgm.CgmReadEffect
import com.biosensor.migratedev.decisioncore.cgm.CgmReadEvent
import com.biosensor.migratedev.decisioncore.cgm.CgmRecord
import com.biosensor.migratedev.decisioncore.cgm.CgmSession
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEffect
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEvent
import com.biosensor.migratedev.decisioncore.cgm.MarkerKind
import com.biosensor.migratedev.decisioncore.cgm.StopReason
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothEffect
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothEvent
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothPort
import com.biosensor.migratedev.port.adapter.localport.FileDeleteResult
import com.biosensor.migratedev.port.adapter.localport.FileEntry
import com.biosensor.migratedev.port.adapter.localport.FileSpace
import com.biosensor.migratedev.port.adapter.localport.FileStore
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Sink
import okio.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CgmPortAdapterTest {

    private val session = CgmSession(id = "s1", deviceId = "AA:01")
    private val clock = Clock.fixed(
        Instant.parse("2026-07-21T04:00:00Z"), ZoneOffset.UTC
    )

    private lateinit var bluetooth: FakeBluetoothPort
    private lateinit var files: FakeFileStore
    private lateinit var port: CgmPortAdapter

    @Before
    fun setUp() {
        bluetooth = FakeBluetoothPort()
        files = FakeFileStore()
        port = CgmPortAdapter(
            bluetooth = bluetooth,
            files = files,
            clock = clock,
            commandIdleTimeoutMillis = 100L
        )
    }

    private val eisText = "EIS:1,60,a,b,c,d"

    // ── 读契约:StartRead / RetryRead ──

    @Test
    fun `start read sends ALL and reports accepted then parsed records`() = runTest {
        bluetooth.results = flowOf(
            BluetoothEvent.DataSent("ALL\n\r".encodeToByteArray().size),
            BluetoothEvent.DataReceived(
                ("Start Playback\n$eisText\nPlayback all done\n").encodeToByteArray()
            )
        )

        val events = port.execute(CgmReadEffect.StartRead(session)).toList()

        val sent = bluetooth.lastEffect as BluetoothEffect.SendData
        assertTrue(sent.data.contentEquals("ALL\n\r".encodeToByteArray()))
        assertEquals(
            listOf(
                CgmReadEvent.CommandAccepted(session.id),
                CgmReadEvent.RecordsProduced(
                    session.id,
                    listOf(
                        CgmRecord.Marker(MarkerKind.START, "Start Playback"),
                        CgmRecord.Eis(1, 60, "a", "b", "c", "d", eisText),
                        CgmRecord.Marker(MarkerKind.END, "Playback all done")
                    )
                )
            ),
            events
        )
    }

    @Test
    fun `accepted only after full payload sent in chunks`() = runTest {
        // 分两次 DataSent:3 + 2 = 5,第二次才触发 CommandAccepted
        bluetooth.results = flowOf(
            BluetoothEvent.DataSent(3),
            BluetoothEvent.DataSent(2)
        )

        val events = port.execute(CgmReadEffect.StartRead(session)).toList()

        assertEquals(listOf(CgmReadEvent.CommandAccepted(session.id)), events)
    }

    @Test
    fun `read flow maps disconnect to device disconnected`() = runTest {
        bluetooth.results = flowOf(
            BluetoothEvent.DataSent("ALL\n\r".encodeToByteArray().size),
            BluetoothEvent.Disconnected
        )

        val events = port.execute(CgmReadEffect.StartRead(session)).toList()

        assertEquals(
            listOf(
                CgmReadEvent.CommandAccepted(session.id),
                CgmReadEvent.DeviceDisconnected(session.id)
            ),
            events
        )
    }

    @Test
    fun `read flow ends immediately after disconnect while bluetooth flow stays open`() = runTest {
        // 真机行为(实测 2026-08-07 17:27):蓝牙事件流在断线后仍保持(命令期消费者挂住)→
        // readFlow 必须自行终止,否则占用消费权(新命令 flow 饿死)+ 60s 后打兜底超时日志
        bluetooth.results = flow {
            emit(BluetoothEvent.DataSent("ALL\n\r".encodeToByteArray().size))
            emit(BluetoothEvent.Disconnected)
            awaitCancellation()   // 蓝牙流保持不结束
        }

        val events = port.execute(CgmReadEffect.StartRead(session)).toList()

        assertEquals(
            listOf(
                CgmReadEvent.CommandAccepted(session.id),
                CgmReadEvent.DeviceDisconnected(session.id)
            ),
            events
        )
    }

    @Test
    fun `read flow maps connect failed to device disconnected`() = runTest {
        // 未连接/发送失败:会话不可用 → 映射断开事件,状态机结束而非死挂(故障分析 12 §3.2)
        bluetooth.results = flowOf(BluetoothEvent.ConnectFailed("未连接,无法发送"))

        val events = port.execute(CgmReadEffect.StartRead(session)).toList()

        assertEquals(listOf(CgmReadEvent.DeviceDisconnected(session.id)), events)
    }

    @Test
    fun `read flow falls back to timeout when flow ends with zero events`() = runTest {
        // 空事件流立即结束:命令未送达且无终止事件 → 兜底超时,杜绝静默死挂
        bluetooth.results = flowOf()

        val events = port.execute(CgmReadEffect.StartRead(session)).toList()

        assertEquals(listOf(CgmReadEvent.CommandTimeout(session.id)), events)
    }

    @Test
    fun `short command maps connect failed to device disconnected`() = runTest {
        bluetooth.results = flowOf(BluetoothEvent.ConnectFailed("未连接,无法发送"))

        val events = port.execute(
            CgmShortEffect.SendCommand(session, CgmCommandPurpose.SYNC_TIME)
        ).toList()

        assertEquals(listOf(CgmShortEvent.DeviceDisconnected(session.id)), events)
    }

    @Test
    fun `read flow feeds pipeline per chunk and accumulates across chunks`() = runTest {
        // 命令确认后,缓存被切成两个字节块:第一块只到 EIS 行,END 行在下块
        bluetooth.results = flowOf(
            BluetoothEvent.DataSent("ALL\n\r".encodeToByteArray().size),
            BluetoothEvent.DataReceived(
                "Start Playback\n$eisText\n".encodeToByteArray()
            ),
            BluetoothEvent.DataReceived("Playback all done\n".encodeToByteArray())
        )

        val events = port.execute(CgmReadEffect.StartRead(session)).toList()

        assertEquals(3, events.size)
        assertEquals(CgmReadEvent.CommandAccepted(session.id), events[0])
        val first = events[1] as CgmReadEvent.RecordsProduced
        assertEquals(
            listOf(
                CgmRecord.Marker(MarkerKind.START, "Start Playback"),
                CgmRecord.Eis(1, 60, "a", "b", "c", "d", eisText)
            ),
            first.records
        )
        val second = events[2] as CgmReadEvent.RecordsProduced
        assertEquals(listOf(CgmRecord.Marker(MarkerKind.END, "Playback all done")), second.records)
    }

    @Test
    fun `read flow buffers records arriving before command accepted`() = runTest {
        // 实测竞态:设备回放可能快于 GATT 写确认(RecordsProduced 先于 CommandAccepted 到达)。
        // 确认前缓冲,确认后先补发缓冲再继续——不丢缓存开头,顺序保持。
        bluetooth.results = flowOf(
            BluetoothEvent.DataReceived(
                "Start Playback\n$eisText\n".encodeToByteArray()
            ),
            BluetoothEvent.DataSent("ALL\n\r".encodeToByteArray().size),
            BluetoothEvent.DataReceived("Playback all done\n".encodeToByteArray())
        )

        val events = port.execute(CgmReadEffect.StartRead(session)).toList()

        assertEquals(
            listOf(
                CgmReadEvent.CommandAccepted(session.id),
                // 确认时补发缓冲(确认前已到的数据)
                CgmReadEvent.RecordsProduced(
                    session.id,
                    listOf(
                        CgmRecord.Marker(MarkerKind.START, "Start Playback"),
                        CgmRecord.Eis(1, 60, "a", "b", "c", "d", eisText)
                    )
                ),
                // 确认后继续按块上报
                CgmReadEvent.RecordsProduced(
                    session.id,
                    listOf(CgmRecord.Marker(MarkerKind.END, "Playback all done"))
                )
            ),
            events
        )
    }

    @Test
    fun `retry read sends ALL again with same command flow`() = runTest {
        bluetooth.results = flowOf(BluetoothEvent.DataSent(5))

        val events = port.execute(CgmReadEffect.RetryRead(session, "接收超时")).toList()

        val sent = bluetooth.lastEffect as BluetoothEffect.SendData
        assertTrue(sent.data.contentEquals("ALL\n\r".encodeToByteArray()))
        assertEquals(listOf(CgmReadEvent.CommandAccepted(session.id)), events)
    }

    @Test
    fun `read flow times out when device silent`() = runTest {
        bluetooth.results = flow { awaitCancellation() }   // 设备沉默:永不发事件

        val events = port.execute(CgmReadEffect.StartRead(session)).toList()

        assertEquals(listOf(CgmReadEvent.CommandTimeout(session.id)), events)
    }

    @Test
    fun `read flow does not time out while data keeps flowing`() = runTest {
        // 空闲超时语义:数据流进行中不超时——大缓存回放可远超总时长窗(故障分析 12 §4.9)。
        // 5 块 × 30ms 间隔 = 150ms 虚拟时长 > 100ms 空闲阈值;若按总时长算早已超时。
        bluetooth.results = flow {
            emit(BluetoothEvent.DataSent("ALL\n\r".encodeToByteArray().size))
            repeat(5) {
                delay(30)
                emit(BluetoothEvent.DataReceived("$eisText\n".encodeToByteArray()))
            }
        }

        val events = port.execute(CgmReadEffect.StartRead(session)).toList()

        assertEquals(6, events.size)   // CommandAccepted + 5 块 RecordsProduced
        assertEquals(CgmReadEvent.CommandAccepted(session.id), events.first())
        assertTrue(events.none { it == CgmReadEvent.CommandTimeout(session.id) })
        assertTrue(events.last() is CgmReadEvent.RecordsProduced)
    }

    @Test
    fun `read flow times out when payload only half sent`() = runTest {
        // 只发出 3/5 字节:命令未完整送达,无 CommandAccepted;随后静默超时
        bluetooth.results = flow {
            emit(BluetoothEvent.DataSent(3))
            awaitCancellation()
        }

        val events = port.execute(CgmReadEffect.StartRead(session)).toList()

        assertEquals(listOf(CgmReadEvent.CommandTimeout(session.id)), events)
    }

    // ── 短命令契约 ──

    @Test
    fun `send sync time waits for device data as ack`() = runTest {
        val payload = "TIME,2026,07,21,04,00,00\n\r".encodeToByteArray()
        bluetooth.results = flowOf(
            BluetoothEvent.DataSent(payload.size),
            BluetoothEvent.DataReceived(byteArrayOf(0x01))   // 设备确认文本(形态待实测)
        )

        val events = port.execute(
            CgmShortEffect.SendCommand(session, CgmCommandPurpose.SYNC_TIME)
        ).toList()

        val sent = bluetooth.lastEffect as BluetoothEffect.SendData
        assertTrue("指令应为 TIME 文本", sent.data.contentEquals(payload))
        assertEquals(
            listOf(
                CgmShortEvent.CommandAccepted(session.id),
                CgmShortEvent.AckReceived(session.id, CgmCommandPurpose.SYNC_TIME)
            ),
            events
        )
    }

    @Test
    fun `short command times out when device silent`() = runTest {
        bluetooth.results = flow { awaitCancellation() }

        val events = port.execute(
            CgmShortEffect.SendCommand(session, CgmCommandPurpose.DELETE)
        ).toList()

        assertEquals(listOf(CgmShortEvent.CommandTimeout(session.id)), events)
    }

    // ── 自动删除:确认 = 发送完成 ──

    @Test
    fun `delete flow confirms on full send`() = runTest {
        bluetooth.results = flowOf(
            BluetoothEvent.DataSent("DELETE\n\r".encodeToByteArray().size)
        )

        val events = port.execute(CgmReadEffect.SendDelete(session)).toList()

        val sent = bluetooth.lastEffect as BluetoothEffect.SendData
        assertTrue(sent.data.contentEquals("DELETE\n\r".encodeToByteArray()))
        assertEquals(
            listOf(CgmReadEvent.AckReceived(session.id, CgmCommandPurpose.DELETE)),
            events
        )
    }

    // ── 落盘 ──

    @Test
    fun `write file wraps records with playback boundary`() = runTest {
        val records = listOf(CgmRecord.Eis(1, 60, "a", "b", "c", "d", eisText))

        val events = port.execute(CgmReadEffect.WriteFile(session, records)).toList()

        assertEquals(listOf(CgmReadEvent.FileWritten(session.id, "cgm/s1.txt")), events)
        assertEquals(FileSpace.RECEIVED, files.space)
        assertEquals("cgm/s1.txt", files.path)
        assertEquals("Start Playback\n$eisText\nPlayback all done\n", files.written.readUtf8())
    }

    @Test
    fun `write file failure emits FileWriteFailed`() = runTest {
        files.failNext = true

        val events = port.execute(
            CgmReadEffect.WriteFile(session, listOf(CgmRecord.Eis(1, 60, "a", "b", "c", "d", eisText)))
        ).toList()

        assertEquals(listOf(CgmReadEvent.FileWriteFailed(session.id, "文件写入失败")), events)
    }

    // ── 会话结束信号 ──

    @Test
    fun `stop runtime emits no events`() = runTest {
        assertEquals(
            emptyList<CgmReadEvent>(),
            port.execute(CgmReadEffect.StopRuntime(session, StopReason.COMPLETED)).toList()
        )
        assertEquals(null, bluetooth.lastEffect)
    }

    // ── 测试替身 ──

    private class FakeBluetoothPort : BluetoothPort {
        var lastEffect: BluetoothEffect? = null
        var results: Flow<BluetoothEvent> = flowOf()

        override fun execute(effect: BluetoothEffect): Flow<BluetoothEvent> {
            lastEffect = effect
            return results
        }

        override fun scanDevices(): Flow<BluetoothDeviceInfo> = flowOf()
    }

    private class FakeFileStore : FileStore {
        val written = Buffer()
        var space: FileSpace? = null
        var path: String? = null
        var failNext: Boolean = false

        override fun write(
            space: FileSpace, relativePath: String, append: Boolean
        ): Sink {
            this.space = space
            this.path = relativePath
            if (failNext) throw IOException("磁盘满")
            return written
        }

        override fun read(space: FileSpace, relativePath: String): Source =
            error("未实现")

        override fun list(space: FileSpace, relativePath: String): List<FileEntry> =
            error("未实现")

        override fun delete(space: FileSpace, relativePath: String): FileDeleteResult =
            error("未实现")
    }
}
