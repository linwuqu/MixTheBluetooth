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
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothConnectionState
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothEvent
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileNotFoundException
import com.biosensor.migratedev.orchestrator.cgm.RecordParser
import com.biosensor.migratedev.decisioncore.cgm.CacheStructureValidator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.timeout
import okio.buffer
import okio.use
import timber.log.Timber
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds
import java.util.concurrent.ConcurrentHashMap

/**
 * 业务适配器:cgm 业务协议的全部内容。
 * 下行:effect → 蓝牙指令 / 文件落盘;上行:蓝牙事件/落盘结果 → 领域事件。
 * 解析管道在适配器内持有,生命周期 = 命令 flow 生命周期。
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class CgmPortAdapter(
    private val bluetooth: BluetoothPort,
    private val files: FileStore,
    private val pipelineFactory: () -> CgmParsePipeline = { SequentialCgmParsePipeline() },
    private val clock: Clock = Clock.systemDefaultZone(),
    private val commandIdleTimeoutMillis: Long = COMMAND_IDLE_TIMEOUT_MILLIS,
) : CgmPort {

    /** 同一 session 下可能同时存在读、落盘等 effect；StopRuntime 统一取消尚未结束的任务。 */
    private val fileWriteMutex = Mutex()
    private val sessionJobs = ConcurrentHashMap<String, MutableSet<Job>>()

    override fun observeConnectionState(): Flow<BluetoothConnectionState> =
        bluetooth.observeConnectionState()

    override fun execute(effect: CgmReadEffect): Flow<CgmReadEvent> = when (effect) {
        // 发起读 / 重读:同一条命令流(重读 = 再发 ALL,累积由状态机保留)
        // 逐分支处理:多分支并列时 smart cast 不成立
        is CgmReadEffect.StartRead -> readFlow(effect.session)
        is CgmReadEffect.RetryRead -> readFlow(effect.session)
        is CgmReadEffect.WriteFile -> writeFileFlow(effect.session, effect.records)
        is CgmReadEffect.SendDelete -> deleteFlow(effect.session)
        is CgmReadEffect.StopRuntime -> stopRuntimeFlow(effect.session)
    }

    override fun execute(effect: CgmShortEffect): Flow<CgmShortEvent> = when (effect) {
        is CgmShortEffect.SendCommand -> shortFlow(effect.session, effect.purpose)
    }

    private fun readFlow(session: CgmSession): Flow<CgmReadEvent> = sessionFlow(session) {
        val pipeline = pipelineFactory()   // 每次读取/重试独占解码器与半行尾巴
        val payload = encode(CgmCommandPurpose.READ_ALL).encodeToByteArray()
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
                                    val buffered = pending.toList()
                                    emit(CgmReadEvent.RecordsProduced(session.id, buffered))
                                    pending.clear()
                                    if (buffered.hasEndMarker()) throw SessionEnded
                                }
                            }
                        }

                        is BluetoothEvent.DataReceived -> {
                            val records = pipeline.parse(event.data)
                            if (records.isEmpty()) return@collect
                            if (accepted) {
                                produced = true
                                emit(CgmReadEvent.RecordsProduced(session.id, records))
                                if (records.hasEndMarker()) throw SessionEnded
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
    ): Flow<CgmShortEvent> = sessionFlow(session) {
        val payload = encode(purpose).encodeToByteArray()
        var sentBytes = 0
        var accepted = false
        var terminalProduced = false
        var ackPending = false
        var idleTimeout = false
        val response = StringBuilder()
        try {
            bluetooth.execute(BluetoothEffect.SendData(payload))
                .timeout(commandIdleTimeoutMillis.milliseconds).collect { event ->
                    when (event) {
                        is BluetoothEvent.DataSentAck -> {
                            sentBytes += event.bytesSentAck
                            if (!accepted && sentBytes >= payload.size) {
                                accepted = true
                                emit(CgmShortEvent.CommandAccepted(session.id))
                                if (ackPending) {
                                    terminalProduced = true
                                    emit(CgmShortEvent.AckReceived(session.id, purpose))
                                    throw SessionEnded
                                }
                            }
                        }

                        is BluetoothEvent.DataReceived -> {
                            response.append(event.data.decodeToString())
                            val confirmed = purpose != CgmCommandPurpose.DELETE ||
                                response.contains(DELETE_ACK)
                            if (confirmed) {
                                if (accepted) {
                                    terminalProduced = true
                                    emit(CgmShortEvent.AckReceived(session.id, purpose))
                                    throw SessionEnded
                                } else {
                                    ackPending = true
                                }
                            }
                        }

                        is BluetoothEvent.Disconnected -> {
                            terminalProduced = true
                            emit(CgmShortEvent.DeviceDisconnected(session.id))
                            throw SessionEnded   // 立即终止,释放命令期消费权(见 readFlow)
                        }
                        // 未连接/发送失败:会话不可用,映射为断开事件——状态机得以结束而非死挂
                        is BluetoothEvent.ConnectFailed -> {
                            terminalProduced = true
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
        if (idleTimeout || !terminalProduced) {
            emit(CgmShortEvent.CommandTimeout(session.id))
        }
    }

    // ── 读流程尾部删除复用同一条 DELETE 短命令；这里只翻译领域事件类型。 ──
    private fun deleteFlow(session: CgmSession): Flow<CgmReadEvent> = flow {
        shortFlow(session, CgmCommandPurpose.DELETE).collect { event ->
            when (event) {
                is CgmShortEvent.CommandAccepted -> Unit // 写出 ACK 不是删除成功
                is CgmShortEvent.AckReceived -> emit(
                    CgmReadEvent.AckReceived(session.id, CgmCommandPurpose.DELETE)
                )
                is CgmShortEvent.CommandTimeout -> emit(CgmReadEvent.CommandTimeout(session.id))
                is CgmShortEvent.DeviceDisconnected -> emit(
                    CgmReadEvent.DeviceDisconnected(session.id)
                )
                // ShortRequested 是状态机输入，不可能由 shortFlow 产出。
                is CgmShortEvent.ShortRequested -> Unit
            }
        }
    }

    // 校验通过后，按设备累计；与已有文件比较完整 LOG 段，只追加新段。
    private fun writeFileFlow(
        session: CgmSession, records: List<CgmRecord>
    ): Flow<CgmReadEvent> = flow {
        val path = "cgm/${session.deviceId.replace(':', '_')}.txt"
        try {
            val result = fileWriteMutex.withLock {
                val existing = try {
                    files.read(FileSpace.RECEIVED, path).buffer().use { it.readUtf8() }
                } catch (_: FileNotFoundException) {
                    ""
                }
                // 读失败或旧文件不完整时停止保存，不能把它当作空文件。
                if (existing.isNotBlank()) {
                    val parser = RecordParser()
                    val oldRecords = existing.lineSequence().filter { it.isNotBlank() }.map { line ->
                        requireNotNull(parser.parse(line)) { "已有文件含无法识别的记录: $line" }
                    }.toList()
                    val validation = CacheStructureValidator.validate(oldRecords)
                    require(validation.ok) { "已有文件校验失败: ${validation.message}" }
                }
                val plan = CgmFileAppend.plan(path, existing, records)
                if (plan.text.isNotEmpty()) {
                    files.write(FileSpace.RECEIVED, path, append = true).buffer().use { sink ->
                        sink.writeUtf8(plan.text)
                    }
                }
                plan.result
            }
            emit(CgmReadEvent.FileWritten(session.id, path, result))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Timber.tag(CGM_TAG).e(failure, "追加保存失败 path=%s", path)
            emit(CgmReadEvent.FileWriteFailed(session.id, "文件追加失败: ${failure.message}"))
        }
    }.flowOn(Dispatchers.IO)

    private fun encode(purpose: CgmCommandPurpose): String = when (purpose) {
        CgmCommandPurpose.SYNC_TIME -> "TIME,${LocalDateTime.now(clock).format(TIME_FORMAT)}\n\r"
        CgmCommandPurpose.READ_ALL -> "ALL\n\r"
        CgmCommandPurpose.DELETE -> "DELETE\n\r"
    }

    private fun stopRuntimeFlow(session: CgmSession?): Flow<CgmReadEvent> = flow {
        val sessionId = session?.id ?: return@flow
        val jobs = synchronized(sessionJobs) {
            sessionJobs.remove(sessionId)?.toList().orEmpty()
        }
        jobs.forEach { it.cancel(CancellationException("CGM session stopped: $sessionId")) }
    }

    private fun <T> sessionFlow(
        session: CgmSession,
        block: suspend FlowCollector<T>.() -> Unit
    ): Flow<T> = flow {
        val job = requireNotNull(currentCoroutineContext()[Job])
        synchronized(sessionJobs) {
            sessionJobs.getOrPut(session.id) { linkedSetOf() }.add(job)
        }
        try {
            block(this)
        } finally {
            synchronized(sessionJobs) {
                sessionJobs[session.id]?.let { jobs ->
                    jobs.remove(job)
                    if (jobs.isEmpty()) sessionJobs.remove(session.id)
                }
            }
        }
    }

    private fun List<CgmRecord>.hasEndMarker(): Boolean = any {
        it is CgmRecord.Marker && it.kind == com.biosensor.migratedev.decisioncore.cgm.MarkerKind.END
    }

    /** 会话终止信号:断开/不可用时抛出以结束命令 flow 收集(蓝牙事件流在断线后仍保持)。 */
    private object SessionEnded : Exception()

    private companion object {
        const val CGM_TAG = "Cgm.Port"
        const val COMMAND_IDLE_TIMEOUT_MILLIS = 60_000L   // 空闲超时:距上次事件 60s 无数据才判超时
        const val DELETE_ACK = "Log Cleared"
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy,MM,dd,HH,mm,ss")
    }
}
