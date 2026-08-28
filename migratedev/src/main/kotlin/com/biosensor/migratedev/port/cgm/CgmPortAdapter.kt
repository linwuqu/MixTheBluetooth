package com.biosensor.migratedev.port.cgm

import com.biosensor.migratedev.port.adapter.localport.FileSpace
import com.biosensor.migratedev.port.adapter.localport.FileStore
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.timeout
import okio.buffer
import okio.use
import timber.log.Timber
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds

/**
 * 业务适配器:cgm 业务协议的全部内容。
 * 下行:effect → 蓝牙指令 / 文件落盘;上行:蓝牙事件/落盘结果 → 领域事件。
 * 解析管道在适配器内持有,生命周期 = 命令 flow 生命周期。
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class CgmPortAdapter(
    private val bluetooth: BluetoothPort,
    private val files: FileStore,
    private val pipeline: CgmParsePipeline = SequentialCgmParsePipeline(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val commandIdleTimeoutMillis: Long = COMMAND_IDLE_TIMEOUT_MILLIS,
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
    // 超时语义 = 空闲超时(距上次事件超过 commandTimeoutMillis 无数据),非命令总时长——
    // 大缓存回放可远超 60s,数据流进行中不超时(故障分析 12 §4.10)
    private fun readFlow(session: CgmSession): Flow<CgmReadEvent> = flow {
        val payload = "ALL\n\r".encodeToByteArray()
        var sentBytes = 0
        var accepted = false
        var produced = false   // 是否发出过任何业务事件(确认/记录/断开)
        var idleTimeout = false
        val pending = mutableListOf<CgmRecord>()   // 确认前缓冲:设备回放可能快于 GATT 写确认,先确认后数据,不丢缓存开头
        try {
            bluetooth.execute(BluetoothEffect.SendData(payload))
                .timeout(commandIdleTimeoutMillis.milliseconds)   // 空闲超时:超时抛 TimeoutCancellationException
                .collect { event ->
                    when (event) {
                        is BluetoothEvent.DataSentAck -> {
                            sentBytes += event.bytesSentAck
                            if (!accepted && sentBytes >= payload.size) {
                                accepted = true
                                produced = true
                                emit(CgmReadEvent.CommandAccepted(session.id))   // 命令完整送达
                                if (pending.isNotEmpty()) {
                                    // 确认前已到的数据:确认后立即补发,顺序保持
                                    emit(CgmReadEvent.RecordsProduced(session.id, pending.toList()))
                                    pending.clear()
                                }
                            }
                        }

                        is BluetoothEvent.DataReceived -> {
                            val records = pipeline.parse(event.data)
                            if (records.isEmpty()) return@collect
                            if (accepted) {
                                produced = true
                                emit(CgmReadEvent.RecordsProduced(session.id, records))
                            } else {
                                pending += records   // 命令未确认:先缓冲
                            }
                        }

                        is BluetoothEvent.Disconnected -> {
                            produced = true
                            Timber.tag(CGM_TAG).w("读指令 session=%s 连接断开", session.id)
                            emit(CgmReadEvent.DeviceDisconnected(session.id))
                            throw SessionEnded   // 立即终止:蓝牙事件流在断线后仍保持(命令期消费者),
                            // 不结束会占用消费权(新命令 flow 饿死)+ 60s 后打兜底超时(实测 2026-08-07 17:27)
                        }
                        // 未连接/发送失败:会话不可用,映射为断开事件——状态机得以结束而非死挂
                        is BluetoothEvent.ConnectFailed -> {
                            produced = true
                            Timber.tag(CGM_TAG)
                                .w("读指令 session=%s 连接不可用:%s", session.id, event.msg)
                            emit(CgmReadEvent.DeviceDisconnected(session.id))
                            throw SessionEnded   // 同上:会话不可用,命令流立即收尾
                        }

                        else -> Unit   // Connected/MtuChanged 等非会话事件
                    }
                }
        } catch (_: SessionEnded) {
            // 会话终止(断开/不可用):已 emit 终止事件,正常收尾
        } catch (_: TimeoutCancellationException) {
            idleTimeout = true
        }
        // 空闲超时或 flow 提前结束却零事件(静默失败)→ 兜底超时,状态机不会死挂
        if (idleTimeout || !produced) {
            Timber.tag(CGM_TAG)
                .w("读指令 session=%s 空闲超时或零事件,兜底超时(已发%s字节)", session.id, sentBytes)
            emit(CgmReadEvent.CommandTimeout(session.id))
        }
    }

    // ── 短命令流:发 TIME/DELETE → 等确认 ──
    private fun shortFlow(
        session: CgmSession, purpose: CgmCommandPurpose
    ): Flow<CgmShortEvent> = flow {
        val payload = encode(purpose).encodeToByteArray()
        var sentBytes = 0
        var produced = false   // 是否发出过任何业务事件(确认/断开)
        var idleTimeout = false
        try {
            bluetooth.execute(BluetoothEffect.SendData(payload))
                .timeout(commandIdleTimeoutMillis.milliseconds).collect { event ->
                    when (event) {
                        is BluetoothEvent.DataSentAck -> {
                            sentBytes += event.bytesSentAck
                            if (!produced && sentBytes >= payload.size) {
                                produced = true
                                emit(CgmShortEvent.CommandAccepted(session.id))
                            }
                        }

                        is BluetoothEvent.DataReceived -> {
                            // 设备确认文本(形态待实测,TODO);当前以收到数据为确认
                            produced = true
                            emit(CgmShortEvent.AckReceived(session.id, purpose))
                        }

                        is BluetoothEvent.Disconnected -> {
                            produced = true
                            emit(CgmShortEvent.DeviceDisconnected(session.id))
                            throw SessionEnded   // 立即终止,释放命令期消费权(见 readFlow)
                        }
                        // 未连接/发送失败:会话不可用,映射为断开事件——状态机得以结束而非死挂
                        is BluetoothEvent.ConnectFailed -> {
                            produced = true
                            emit(CgmShortEvent.DeviceDisconnected(session.id))
                            throw SessionEnded   // 同上:会话不可用,命令流立即收尾
                        }

                        else -> Unit   // Connected/MtuChanged 等非会话事件
                    }
                }
        } catch (_: SessionEnded) {
            // 会话终止(断开/不可用):已 emit 终止事件,正常收尾
        } catch (_: TimeoutCancellationException) {
            idleTimeout = true
        }
        // 空闲超时或 flow 提前结束却零事件(静默失败)→ 兜底超时,状态机不会死挂
        if (idleTimeout || !produced) {
            emit(CgmShortEvent.CommandTimeout(session.id))
        }
    }

    // ── 读流程的自动删除:确认 = 发送完成(DELETE 后设备不再回放) ──
    private fun deleteFlow(session: CgmSession): Flow<CgmReadEvent> = flow {
        val payload = "DELETE\n\r".encodeToByteArray()
        var sentBytes = 0
        var produced = false   // 是否发出过任何业务事件(确认/断开)
        var idleTimeout = false
        try {
            bluetooth.execute(BluetoothEffect.SendData(payload))
                .timeout(commandIdleTimeoutMillis.milliseconds).collect { event ->
                    when (event) {
                        is BluetoothEvent.DataSentAck -> {
                            sentBytes += event.bytesSentAck
                            if (!produced && sentBytes >= payload.size) {
                                produced = true
                                emit(CgmReadEvent.AckReceived(session.id, CgmCommandPurpose.DELETE))
                            }
                        }

                        is BluetoothEvent.Disconnected -> {
                            produced = true
                            emit(CgmReadEvent.DeviceDisconnected(session.id))
                            throw SessionEnded   // 立即终止,释放命令期消费权(见 readFlow)
                        }
                        // 未连接/发送失败:会话不可用,映射为断开事件——状态机得以结束而非死挂
                        is BluetoothEvent.ConnectFailed -> {
                            produced = true
                            emit(CgmReadEvent.DeviceDisconnected(session.id))
                            throw SessionEnded   // 同上:会话不可用,命令流立即收尾
                        }

                        else -> Unit   // Connected/MtuChanged 等非会话事件
                    }
                }
        } catch (_: SessionEnded) {
            // 会话终止(断开/不可用):已 emit 终止事件,正常收尾
        } catch (_: TimeoutCancellationException) {
            idleTimeout = true
        }
        // 空闲超时或 flow 提前结束却零事件(静默失败)→ 兜底超时,状态机不会死挂
        if (idleTimeout || !produced) {
            emit(CgmReadEvent.CommandTimeout(session.id))
        }
    }

    // ── 落盘:文件边界 = Start Playback + 记录行 + Playback all done ──
    private fun writeFileFlow(
        session: CgmSession, records: List<CgmRecord>
    ): Flow<CgmReadEvent> = flow {
        val path = "cgm/${session.id}.txt"
        val ok = runCatching {
            files.write(FileSpace.RECEIVED, path, append = false).buffer().use { sink ->
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

    /** 会话终止信号:断开/不可用时抛出以结束命令 flow 收集(蓝牙事件流在断线后仍保持)。 */
    private object SessionEnded : Exception()

    private companion object {
        const val CGM_TAG = "Cgm.Port"
        const val COMMAND_IDLE_TIMEOUT_MILLIS = 60_000L   // 空闲超时:距上次事件 60s 无数据才判超时
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy,MM,dd,HH,mm,ss")
    }
}
