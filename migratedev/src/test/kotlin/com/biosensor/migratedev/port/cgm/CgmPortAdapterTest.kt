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
import java.io.FileNotFoundException
import com.biosensor.migratedev.decisioncore.cgm.CgmFileAppendResult
import org.junit.Assert.assertFalse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
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
    fun `start read sends only ALL and reports accepted then parsed records`() = runTest {
        bluetooth.results = flowOf(
            BluetoothEvent.DataSentAck("ALL\n\r".encodeToByteArray().size),
            BluetoothEvent.DataReceived(
                ("Start Playback\n$eisText\nPlayback all done\n").encodeToByteArray()
            )
        )

        val events = port.execute(CgmReadEffect.StartRead(session)).toList()

        assertEquals(listOf("ALL\n\r"), bluetooth.effects.map {
            (it as BluetoothEffect.SendData).data.decodeToString()
        })

        val sent = bluetooth.lastEffect as BluetoothEffect.SendData
        assertTrue(sent.data.contentEquals("ALL\n\r".encodeToByteArray()))
        assertEquals(2, events.size)
        assertEquals(CgmReadEvent.CommandAccepted(session.id), events[0])
        val produced = events[1] as CgmReadEvent.RecordsProduced
        assertEquals(
            listOf(
                CgmRecord.Marker(MarkerKind.START, "Start Playback"),
                CgmRecord.Eis(1, 60, "a", "b", "c", "d", eisText),
                CgmRecord.Marker(MarkerKind.END, "Playback all done")
            ),
            produced.records
        )
        assertTrue("收到 END 的这批要带收集校验结论", produced.conclusion != null)
    }

    @Test
    fun `accepted only after full payload sent in chunks`() = runTest {
        // 分两次 DataSent:3 + 2 = 5,第二次才触发 CommandAccepted
        bluetooth.results = flowOf(
            BluetoothEvent.DataSentAck(3),
            BluetoothEvent.DataSentAck(2)
        )

        val events = port.execute(CgmReadEffect.StartRead(session)).toList()

        assertEquals(CgmReadEvent.CommandAccepted(session.id), events.first())
        assertEquals(1, events.count { it is CgmReadEvent.CommandAccepted })
        // 没收到 END 流就结束了:兜底超时,状态机重读而不是死挂
        assertEquals(CgmReadEvent.CommandTimeout(session.id), events.last())
    }

    @Test
    fun `read flow maps disconnect to device disconnected`() = runTest {
        bluetooth.results = flowOf(
            BluetoothEvent.DataSentAck("ALL\n\r".encodeToByteArray().size),
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
            emit(BluetoothEvent.DataSentAck("ALL\n\r".encodeToByteArray().size))
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
    fun `read flow ends immediately after END while bluetooth flow stays open`() = runTest {
        // 真机蓝牙 hub 本身是长流；收到 END 后本次订阅必须立刻退出，避免第二次读时旧订阅
        // 同时收到并解析同一批通知，进而产生双份 RecordsProduced 和迟到超时。
        bluetooth.results = flow {
            emit(BluetoothEvent.DataSentAck("ALL\n\r".encodeToByteArray().size))
            emit(BluetoothEvent.DataReceived(
                "Start Playback\nPlayback all done\n".encodeToByteArray()
            ))
            awaitCancellation()
        }

        val events = port.execute(CgmReadEffect.StartRead(session)).toList()

        assertEquals(2, events.size)
        assertEquals(CgmReadEvent.CommandAccepted(session.id), events.first())
        assertTrue(events.last() is CgmReadEvent.RecordsProduced)
        assertTrue(events.none { it is CgmReadEvent.CommandTimeout })
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
            BluetoothEvent.DataSentAck("ALL\n\r".encodeToByteArray().size),
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
            BluetoothEvent.DataSentAck("ALL\n\r".encodeToByteArray().size),
            BluetoothEvent.DataReceived("Playback all done\n".encodeToByteArray())
        )

        val events = port.execute(CgmReadEffect.StartRead(session)).toList()

        assertEquals(3, events.size)
        assertEquals(CgmReadEvent.CommandAccepted(session.id), events[0])

        // 确认时补发缓冲(确认前已到的数据):缓存开头不丢
        val flushed = events[1] as CgmReadEvent.RecordsProduced
        assertEquals(
            listOf(
                CgmRecord.Marker(MarkerKind.START, "Start Playback"),
                CgmRecord.Eis(1, 60, "a", "b", "c", "d", eisText)
            ),
            flushed.records
        )
        assertEquals("还没收到 END,不收尾", null, flushed.conclusion)

        // 确认后继续按块上报
        val last = events[2] as CgmReadEvent.RecordsProduced
        assertEquals(listOf(CgmRecord.Marker(MarkerKind.END, "Playback all done")), last.records)
        assertTrue(last.conclusion != null)
    }

    @Test
    fun `retry read sends ALL again with same command flow`() = runTest {
        bluetooth.results = flowOf(BluetoothEvent.DataSentAck(5))

        port.execute(CgmReadEffect.StartRead(session)).toList()
        val events = port.execute(CgmReadEffect.RetryRead(session, "接收超时")).toList()

        assertEquals(listOf("ALL\n\r", "ALL\n\r"), bluetooth.effects.map {
            (it as BluetoothEffect.SendData).data.decodeToString()
        })

        val sent = bluetooth.lastEffect as BluetoothEffect.SendData
        assertTrue(sent.data.contentEquals("ALL\n\r".encodeToByteArray()))
        assertEquals(CgmReadEvent.CommandAccepted(session.id), events.first())
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
            emit(BluetoothEvent.DataSentAck("ALL\n\r".encodeToByteArray().size))
            repeat(5) {
                delay(30)
                emit(BluetoothEvent.DataReceived("$eisText\n".encodeToByteArray()))
            }
        }

        val events = port.execute(CgmReadEffect.StartRead(session)).toList()

        assertEquals(5, events.filterIsInstance<CgmReadEvent.RecordsProduced>().size)
        assertEquals(CgmReadEvent.CommandAccepted(session.id), events.first())
        // 唯一一次超时来自流结束(全程没收到 END),不是空闲超时
        assertEquals(CgmReadEvent.CommandTimeout(session.id), events.last())
    }

    @Test
    fun `read flow times out when payload only half sent`() = runTest {
        // 只发出 3/5 字节:命令未完整送达,无 CommandAccepted;随后静默超时
        bluetooth.results = flow {
            emit(BluetoothEvent.DataSentAck(3))
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
            BluetoothEvent.DataSentAck(payload.size),
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

    // ── 删除确认(短命令)：设备收到就清空，一个字都不回 ──

    /** 2026-10-16 实机实测:发完 DELETE 后 60s 内设备零回包,缓存确实已清空。 */
    @Test
    fun `manual delete completes on write ack without waiting for a reply`() = runTest {
        val payloadSize = "DELETE\n\r".encodeToByteArray().size
        bluetooth.results = flow {
            emit(BluetoothEvent.DataSentAck(payloadSize))
            awaitCancellation()   // 设备不回话:流不结束,只能靠写确认收尾
        }

        val events = port.execute(
            CgmShortEffect.SendCommand(session, CgmCommandPurpose.DELETE)
        ).toList()

        assertEquals(
            listOf(
                CgmShortEvent.CommandAccepted(session.id),
                CgmShortEvent.AckReceived(session.id, CgmCommandPurpose.DELETE)
            ),
            events
        )
    }

    /** 回包比写确认先到:先上报 CommandAccepted，再上报业务 ACK。 */
    @Test
    fun `short command preserves event order when reply precedes write ack`() = runTest {
        val payloadSize = "DELETE\n\r".encodeToByteArray().size
        bluetooth.results = flowOf(
            BluetoothEvent.DataReceived("ack\r\n".encodeToByteArray()),
            BluetoothEvent.DataSentAck(payloadSize)
        )

        val events = port.execute(
            CgmShortEffect.SendCommand(session, CgmCommandPurpose.DELETE)
        ).toList()

        assertEquals(
            listOf(
                CgmShortEvent.CommandAccepted(session.id),
                CgmShortEvent.AckReceived(session.id, CgmCommandPurpose.DELETE)
            ),
            events
        )
    }

    // ── 落盘 ──

    @Test
    fun `write file appends data without playback boundaries`() = runTest {
        val records = segment()

        val events = port.execute(CgmReadEffect.WriteFile(session, records)).toList()

        assertEquals(listOf(CgmReadEvent.FileWritten(session.id, "cgm/AA_01.txt",
            CgmFileAppendResult("cgm/AA_01.txt", 1, 0, 1, 0, 1))), events)
        assertEquals(FileSpace.RECEIVED, files.space)
        assertEquals("cgm/AA_01.txt", files.path)
        assertEquals(true, files.append)
        assertEquals("LOG:1,1,1,2026-07-21 12:10:45\n$eisText\n", files.written.clone().readUtf8())
    }

    @Test
    fun `write file failure emits FileWriteFailed`() = runTest {
        files.failNext = true

        val events = port.execute(
            CgmReadEffect.WriteFile(session, segment())
        ).toList()

        assertEquals(listOf(CgmReadEvent.FileWriteFailed(session.id, "文件追加失败: 磁盘满")), events)
    }

    @Test
    fun `second session appends only segments missing from the device file`() = runTest {
        val first = segment()
        val second = segment(number = 2, time = "2026-07-21 12:20:45")
        port.execute(CgmReadEffect.WriteFile(session, first)).toList()
        val events = port.execute(CgmReadEffect.WriteFile(session.copy(id = "s2"), first + second)).toList()
        val saved = (events.single() as CgmReadEvent.FileWritten).result!!
        assertEquals(1, saved.addedSegments)
        assertEquals(1, saved.duplicateSegments)
        assertEquals(1, saved.addedPoints)
        assertEquals(2, saved.totalPoints)
        assertEquals(2, files.writes)
        val text = files.written.clone().readUtf8()
        assertEquals(2, text.lineSequence().count { it.startsWith("LOG:") })
        assertEquals(2, text.lineSequence().count { it.startsWith("EIS:") })
        assertFalse(text.contains("Playback"))
    }

    @Test
    fun `reading the same cache again does not append or change the file`() = runTest {
        val records = segment()
        port.execute(CgmReadEffect.WriteFile(session, records)).toList()
        val original = files.written.clone().readUtf8()
        val events = port.execute(CgmReadEffect.WriteFile(session.copy(id = "s2"), records)).toList()
        val saved = (events.single() as CgmReadEvent.FileWritten).result!!
        assertEquals(0, saved.addedPoints)
        assertEquals(1, saved.duplicatePoints)
        assertEquals(1, files.writes)
        assertEquals(original, files.written.clone().readUtf8())
    }

    @Test
    fun `same point values at a different LOG time are preserved`() = runTest {
        port.execute(CgmReadEffect.WriteFile(session, segment())).toList()
        val events = port.execute(CgmReadEffect.WriteFile(
            session, segment(time = "2026-07-21 12:20:45")
        )).toList()
        assertEquals(1, (events.single() as CgmReadEvent.FileWritten).result!!.addedPoints)
        assertEquals(2, files.written.clone().readUtf8().lineSequence().count { it.startsWith("EIS:") })
    }

    @Test
    fun `devices use different cumulative files`() = runTest {
        port.execute(CgmReadEffect.WriteFile(session, segment())).toList()
        val other = session.copy(id = "s2", deviceId = "BB:02")
        val event = port.execute(CgmReadEffect.WriteFile(other, segment())).toList().single() as CgmReadEvent.FileWritten
        assertEquals("cgm/BB_02.txt", event.path)
        assertEquals(0, event.result!!.duplicatePoints)
        assertEquals(2, files.list(FileSpace.RECEIVED).size)
    }

    @Test
    fun `file read failure is not treated as an empty file`() = runTest {
        port.execute(CgmReadEffect.WriteFile(session, segment())).toList()
        val original = files.written.clone().readUtf8()
        files.failRead = true
        val events = port.execute(CgmReadEffect.WriteFile(session, segment(number = 2))).toList()
        assertTrue(events.single() is CgmReadEvent.FileWriteFailed)
        assertEquals(1, files.writes)
        assertEquals(original, files.written.clone().readUtf8())
    }

    @Test
    fun `an incomplete stored segment prevents further append`() = runTest {
        files.write(FileSpace.RECEIVED, "cgm/AA_01.txt", false).use {
            (it as Buffer).writeUtf8("LOG:1,1,95,2026-07-21 12:10:45\n$eisText\n")
        }
        val events = port.execute(CgmReadEffect.WriteFile(session, segment(number = 2))).toList()
        val failure = events.single() as CgmReadEvent.FileWriteFailed
        assertTrue(failure.message.contains("已有文件校验失败"))
        assertEquals(1, files.writes)
    }

    private fun segment(number: Int = 1, time: String = "2026-07-21 12:10:45"): List<CgmRecord> =
        listOf(CgmRecord.Log("LOG:$number,1,1,$time"), CgmRecord.Eis(1, 60, "a", "b", "c", "d", eisText))

    // ── 会话结束信号 ──

    @Test
    fun `stop runtime emits no events`() = runTest {
        assertEquals(
            emptyList<CgmReadEvent>(),
            port.execute(CgmReadEffect.StopRuntime(session, StopReason.COMPLETED)).toList()
        )
        assertEquals(null, bluetooth.lastEffect)
    }

    @Test
    fun `stop runtime cancels an active read immediately`() = runTest {
        bluetooth.results = flow {
            emit(BluetoothEvent.DataSentAck("ALL\n\r".encodeToByteArray().size))
            awaitCancellation()
        }
        val events = mutableListOf<CgmReadEvent>()
        val readJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            port.execute(CgmReadEffect.StartRead(session)).toList(events)
        }
        runCurrent()
        assertEquals(listOf(CgmReadEvent.CommandAccepted(session.id)), events)

        port.execute(CgmReadEffect.StopRuntime(session, StopReason.USER_CANCEL)).toList()
        runCurrent()

        assertTrue(readJob.isCancelled)
        assertTrue(events.none { it is CgmReadEvent.CommandTimeout })
    }

    // ── 测试替身 ──

    private class FakeBluetoothPort : BluetoothPort {
        var lastEffect: BluetoothEffect? = null

        /** 全部下发 effect：每次读取或重读只发 ALL。 */
        val effects = mutableListOf<BluetoothEffect>()
        var results: Flow<BluetoothEvent> = flowOf()

        override fun execute(effect: BluetoothEffect): Flow<BluetoothEvent> {
            lastEffect = effect
            effects += effect
            return results
        }

        override fun scanDevices(): Flow<BluetoothDeviceInfo> = flowOf()
    }

    private class FakeFileStore : FileStore {
        private val contents = mutableMapOf<String, Buffer>()
        val written: Buffer get() = contents.getValue(requireNotNull(path))
        var space: FileSpace? = null
        var path: String? = null
        var append: Boolean? = null
        var writes = 0
        var failNext = false
        var failRead = false

        override fun write(space: FileSpace, relativePath: String, append: Boolean): Sink {
            this.space = space
            this.path = relativePath
            this.append = append
            if (failNext) throw IOException("磁盘满")
            writes++
            if (!append) contents[relativePath] = Buffer()
            return contents.getOrPut(relativePath) { Buffer() }
        }

        override fun read(space: FileSpace, relativePath: String): Source {
            if (failRead) throw IOException("文件读取失败")
            return contents[relativePath]?.clone() ?: throw FileNotFoundException(relativePath)
        }

        override fun list(space: FileSpace, relativePath: String): List<FileEntry> =
            contents.map { (path, buffer) -> FileEntry(path, buffer.size, null) }

        override fun delete(space: FileSpace, relativePath: String): FileDeleteResult =
            if (contents.remove(relativePath) != null) FileDeleteResult.Done else FileDeleteResult.None
    }
}
