package com.biosensor.migratedev.port.cgm

import com.biosensor.migratedev.decisioncore.cgm.CgmCommandPurpose
import com.biosensor.migratedev.decisioncore.cgm.CgmReadEffect
import com.biosensor.migratedev.decisioncore.cgm.CgmReadEvent
import com.biosensor.migratedev.decisioncore.cgm.CgmRecord
import com.biosensor.migratedev.decisioncore.cgm.CgmSession
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEffect
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEvent
import com.biosensor.migratedev.orchestrator.cgm.CgmParsePipeline
import com.biosensor.migratedev.orchestrator.cgm.SequentialCgmParsePipeline
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothEffect
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothEvent
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothPort
import com.biosensor.migratedev.port.adapter.localport.FileSpace
import com.biosensor.migratedev.port.adapter.localport.LocalFileClient
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import okio.buffer
import okio.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * 业务适配器:cgm 业务协议的全部内容。
 * 下行:effect → 蓝牙指令 / 文件落盘;上行:蓝牙事件/落盘结果 → 领域事件。
 * 解析管道在适配器内持有,生命周期 = 命令 flow 生命周期。
 */
class CgmPortAdapter(
    private val bluetooth: BluetoothPort,
    private val fileClient: LocalFileClient,
    private val pipeline: CgmParsePipeline = SequentialCgmParsePipeline(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val commandTimeoutMillis: Long = COMMAND_TIMEOUT_MILLIS,
) : CgmPort {

    // ── 读契约 ──
    override fun execute(effect: CgmReadEffect): Flow<CgmReadEvent> = when (effect) {
        // 发起读 / 重读:同一条命令流(重读 = 再发 ALL,累积由状态机保留)
        // 逐分支处理:多分支并列时 smart cast 不成立
        is CgmReadEffect.StartRead -> readFlow(effect.session)
        is CgmReadEffect.RetryRead -> readFlow(effect.session)
        is CgmReadEffect.WriteFile -> writeFileFlow(effect.session, effect.records)
        is CgmReadEffect.SendDelete -> deleteFlow(effect.session)
        is CgmReadEffect.StopRuntime -> flowOf()   // 会话结束信号(协作式停止,见 1.6)
    }

    // ── 短命令契约 ──
    override fun execute(effect: CgmShortEffect): Flow<CgmShortEvent> = when (effect) {
        is CgmShortEffect.SendCommand -> shortFlow(effect.session, effect.purpose)
    }

    // ── 读命令流:发 ALL → 收集 → 解析 → 上报 ──
    private fun readFlow(session: CgmSession): Flow<CgmReadEvent> = flow {
        val payload = "ALL\n\r".encodeToByteArray()
        var sentBytes = 0
        var accepted = false
        val completed = withTimeoutOrNull(commandTimeoutMillis) {
            bluetooth.execute(BluetoothEffect.SendData(payload)).collect { event ->
                when (event) {
                    is BluetoothEvent.DataSent -> {
                        sentBytes += event.bytesSent
                        if (!accepted && sentBytes >= payload.size) {
                            accepted = true
                            emit(CgmReadEvent.CommandAccepted(session.id))   // 命令完整送达
                        }
                    }
                    is BluetoothEvent.DataReceived -> {
                        pipeline.parse(event.data)
                            .takeIf { it.isNotEmpty() }
                            ?.let { emit(CgmReadEvent.RecordsProduced(session.id, it)) }
                    }
                    is BluetoothEvent.Disconnected -> emit(CgmReadEvent.DeviceDisconnected(session.id))
                    else -> Unit   // Connected/MtuChanged 等非会话事件
                }
            }
        }
        if (completed == null) emit(CgmReadEvent.CommandTimeout(session.id))
    }

    // ── 短命令流:发 TIME/DELETE → 等确认 ──
    private fun shortFlow(
        session: CgmSession, purpose: CgmCommandPurpose
    ): Flow<CgmShortEvent> = flow {
        val payload = encode(purpose).encodeToByteArray()
        var sentBytes = 0
        var accepted = false
        val completed = withTimeoutOrNull(commandTimeoutMillis) {
            bluetooth.execute(BluetoothEffect.SendData(payload)).collect { event ->
                when (event) {
                    is BluetoothEvent.DataSent -> {
                        sentBytes += event.bytesSent
                        if (!accepted && sentBytes >= payload.size) {
                            accepted = true
                            emit(CgmShortEvent.CommandAccepted(session.id))
                        }
                    }
                    is BluetoothEvent.DataReceived -> {
                        // 设备确认文本(形态待实测,TODO);当前以收到数据为确认
                        emit(CgmShortEvent.AckReceived(session.id, purpose))
                    }
                    is BluetoothEvent.Disconnected -> emit(CgmShortEvent.DeviceDisconnected(session.id))
                    else -> Unit
                }
            }
        }
        if (completed == null) emit(CgmShortEvent.CommandTimeout(session.id))
    }

    // ── 读流程的自动删除:确认 = 发送完成(DELETE 后设备不再回放) ──
    private fun deleteFlow(session: CgmSession): Flow<CgmReadEvent> = flow {
        val payload = "DELETE\n\r".encodeToByteArray()
        var sentBytes = 0
        val completed = withTimeoutOrNull(commandTimeoutMillis) {
            bluetooth.execute(BluetoothEffect.SendData(payload)).collect { event ->
                when (event) {
                    is BluetoothEvent.DataSent -> {
                        sentBytes += event.bytesSent
                        if (sentBytes >= payload.size) {
                            emit(CgmReadEvent.AckReceived(session.id, CgmCommandPurpose.DELETE))
                        }
                    }
                    is BluetoothEvent.Disconnected -> emit(CgmReadEvent.DeviceDisconnected(session.id))
                    else -> Unit
                }
            }
        }
        if (completed == null) emit(CgmReadEvent.CommandTimeout(session.id))
    }

    // ── 落盘:文件边界 = Start Playback + 记录行 + Playback all done ──
    private fun writeFileFlow(
        session: CgmSession, records: List<CgmRecord>
    ): Flow<CgmReadEvent> = flow {
        val path = "cgm/${session.id}.txt"
        val ok = runCatching {
            fileClient.sink(FileSpace.RECEIVED, path, append = false).buffer().use { sink ->
                sink.writeUtf8(render(records))
            }
            true
        }.getOrDefault(false)
        Timber.tag(CGM_TAG).i("落盘 session=%s path=%s ok=%s", session.id, path, ok)
        emit(
            if (ok) CgmReadEvent.FileWritten(session.id, path)
            else CgmReadEvent.FileWriteFailed(session.id, "文件写入失败")
        )
    }.flowOn(Dispatchers.IO)

    private fun render(records: List<CgmRecord>): String = buildString {
        appendLine("Start Playback")
        records.forEach { appendLine(it.text) }
        appendLine("Playback all done")
    }

    private fun encode(purpose: CgmCommandPurpose): String = when (purpose) {
        CgmCommandPurpose.SYNC_TIME -> "TIME,${LocalDateTime.now(clock).format(TIME_FORMAT)}\n\r"
        CgmCommandPurpose.READ_ALL -> "ALL\n\r"
        CgmCommandPurpose.DELETE -> "DELETE\n\r"
    }

    private companion object {
        const val CGM_TAG = "Cgm.Port"
        const val COMMAND_TIMEOUT_MILLIS = 60_000L
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy,MM,dd,HH,mm,ss")
    }
}
