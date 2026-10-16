package com.biosensor.migratedev.port.cgm

import com.biosensor.migratedev.port.adapter.localport.FileSpace
import com.biosensor.migratedev.port.adapter.localport.FileStore
import com.biosensor.migratedev.decisioncore.cgm.CacheStructureValidator
import com.biosensor.migratedev.decisioncore.cgm.CacheValidators
import com.biosensor.migratedev.decisioncore.cgm.CgmCommandPurpose
import com.biosensor.migratedev.decisioncore.cgm.CgmReadEffect
import com.biosensor.migratedev.decisioncore.cgm.CgmReadEvent
import com.biosensor.migratedev.decisioncore.cgm.CgmRecord
import com.biosensor.migratedev.decisioncore.cgm.CgmSession
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEffect
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEvent
import com.biosensor.migratedev.decisioncore.cgm.MarkerKind
import com.biosensor.migratedev.orchestrator.cgm.CgmParsePipeline
import com.biosensor.migratedev.orchestrator.cgm.RecordParser
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.timeout
import okio.buffer
import okio.use
import timber.log.Timber
import java.io.FileNotFoundException
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds
import java.util.concurrent.ConcurrentHashMap

/**
 * 业务适配器:cgm 业务协议的全部内容。
 * 下行:effect → 蓝牙指令 / 文件落盘;上行:蓝牙事件/落盘结果 → 领域事件。
 * 解析管道在适配器内持有,生命周期 = 命令 flow 生命周期。
 *
 * 数据主线在这里走完:收字节 → 解码 → 拼行 → 解析 → 收集校验,
 * 状态机只接管"重读还是收工"的决策。
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
        // 发起读 / 重读:同一条命令流(重读 = 再发 ALL,全量重放)
        is CgmReadEffect.StartRead -> readFlow(effect.session)
        is CgmReadEffect.RetryRead -> readFlow(effect.session)
        is CgmReadEffect.WriteFile -> writeFileFlow(effect.session, effect.records)
        is CgmReadEffect.StopRuntime -> stopRuntimeFlow(effect.session)
    }

    override fun execute(effect: CgmShortEffect): Flow<CgmShortEvent> = when (effect) {
        is CgmShortEffect.SendCommand -> shortFlow(effect.session, effect.purpose)
    }

    // ── 读流程:发 ALL → 收数据 → 解析 → 收集校验 ──

    private fun readFlow(session: CgmSession): Flow<CgmReadEvent> = sessionFlow(session) {
        val pipeline = pipelineFactory()   // 每次读取/重试独占解码器与半行尾巴
        val payload = encode(CgmCommandPurpose.READ_ALL).encodeToByteArray()
        val received = mutableListOf<CgmRecord>()   // 本次收到的全部记录:收集校验的输入
        val buffered = mutableListOf<CgmRecord>()   // 确认前缓冲:设备回放可能快于 GATT 写确认
        var sentBytes = 0
        var accepted = false
        var settled = false   // 已发终止事件(校验结论或断开),不再打兜底超时
        try {
            bluetooth.execute(BluetoothEffect.SendData(payload))
                .timeout(commandIdleTimeoutMillis.milliseconds)   // 空闲超时:距上次事件 60s 无数据才判超时
                .collect { event ->
                    when (event) {
                        is BluetoothEvent.DataSentAck -> {
                            sentBytes += event.bytesSentAck
                            if (!accepted && sentBytes >= payload.size) {
                                accepted = true
                                emit(CgmReadEvent.CommandAccepted(session.id))   // 命令完整送达
                                if (buffered.isNotEmpty()) {
                                    // 确认前已到的数据:确认后立即补发,顺序保持
                                    val batch = buffered.toList()
                                    buffered.clear()
                                    if (emitBatch(session, batch, received)) {
                                        settled = true
                                        throw SessionEnded
                                    }
                                }
                            }
                        }

                        is BluetoothEvent.DataReceived -> {
                            val records = pipeline.parse(event.data)
                            if (records.isEmpty()) return@collect
                            if (!accepted) {
                                buffered += records   // 命令未确认:先缓冲,不丢缓存开头
                            } else if (emitBatch(session, records, received)) {
                                settled = true
                                throw SessionEnded
                            }
                        }

                        // 断开 / 未连接 / 发送失败:会话不可用,立即收尾。
                        // 蓝牙事件流在断线后仍保持(命令期消费者),不结束会占用消费权使新命令 flow 饿死。
                        is BluetoothEvent.Disconnected, is BluetoothEvent.ConnectFailed -> {
                            settled = true
                            Timber.tag(CGM_TAG).w("读指令 session=%s 会话终止:%s", session.id, event)
                            emit(CgmReadEvent.DeviceDisconnected(session.id))
                            throw SessionEnded
                        }

                        else -> Unit   // Connected/MtuChanged 等非会话事件
                    }
                }
        } catch (_: SessionEnded) {
            // 会话终止:已 emit 终止事件,正常收尾
        } catch (_: TimeoutCancellationException) {
            // 空闲超时:落入下面的兜底
        }
        // 空闲超时,或流提前结束却零事件(静默失败)→ 兜底超时,状态机不会死挂
        if (!settled) {
            Timber.tag(CGM_TAG)
                .w("读指令 session=%s 空闲超时或零事件,兜底超时(已发%s字节)", session.id, sentBytes)
            emit(CgmReadEvent.CommandTimeout(session.id))
        }
    }

    /**
     * 上报一批新记录。见到 END 时顺带做收集校验,结论挂在同一批上送回状态机。
     * @return 本批是否含 END——调用方据此收尾
     */
    private suspend fun FlowCollector<CgmReadEvent>.emitBatch(
        session: CgmSession,
        batch: List<CgmRecord>,
        received: MutableList<CgmRecord>,
    ): Boolean {
        received += batch
        val end = batch.any { it is CgmRecord.Marker && it.kind == MarkerKind.END }
        emit(CgmReadEvent.RecordsProduced(
            session.id, batch, if (end) CacheValidators.validate(received) else null))
        return end
    }

    // ── 短命令流:发 TIME/DELETE → 等确认 ──

    private fun shortFlow(
        session: CgmSession, purpose: CgmCommandPurpose
    ): Flow<CgmShortEvent> = sessionFlow(session) {
        val payload = encode(purpose).encodeToByteArray()
        val response = StringBuilder()
        var sentBytes = 0
        var accepted = false
        var settled = false
        var ackPending = false
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
                                    settled = true
                                    emit(CgmShortEvent.AckReceived(session.id, purpose))
                                    throw SessionEnded
                                }
                            }
                        }

                        is BluetoothEvent.DataReceived -> {
                            response.append(event.data.decodeToString())
                            // 删除必须等设备明确回复 Log Cleared(写出 ACK 不算);对时不校验回包内容
                            val confirmed = purpose != CgmCommandPurpose.DELETE ||
                                response.contains(DELETE_ACK)
                            if (!confirmed) return@collect
                            if (!accepted) {
                                ackPending = true   // 回包比写确认先到:先记下,确认时再上报
                            } else {
                                settled = true
                                emit(CgmShortEvent.AckReceived(session.id, purpose))
                                throw SessionEnded
                            }
                        }

                        is BluetoothEvent.Disconnected, is BluetoothEvent.ConnectFailed -> {
                            settled = true
                            emit(CgmShortEvent.DeviceDisconnected(session.id))
                            throw SessionEnded   // 立即终止,释放命令期消费权(见 readFlow)
                        }

                        else -> Unit   // Connected/MtuChanged 等非会话事件
                    }
                }
        } catch (_: SessionEnded) {
            // 会话终止:已 emit 终止事件,正常收尾
        } catch (_: TimeoutCancellationException) {
            // 空闲超时:落入下面的兜底
        }
        if (!settled) emit(CgmShortEvent.CommandTimeout(session.id))
    }

    // ── 入库校验 + 追加写:与设备已有的累计文件比对,整段重复的丢掉 ──

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
                // 旧文件必须能完整解析:读失败或内容残缺时停止保存,不能把它当作空文件
                val parser = RecordParser()
                val existingRecords = existing.lineSequence()
                    .filter { it.isNotBlank() }
                    .map { line ->
                        requireNotNull(parser.parse(line)) { "已有文件含无法识别的记录: $line" }
                    }
                    .toList()
                if (existingRecords.isNotEmpty()) {
                    val validation = CacheStructureValidator.validate(existingRecords)
                    require(validation.ok) { "已有文件校验失败: ${validation.message}" }
                }
                val plan = CgmFileAppend.plan(
                    path, existingRecords, records,
                    appendLeadingNewline = existing.isNotEmpty() && !existing.endsWith('\n'))
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

    /** 会话终止信号:断开/不可用时抛出以结束命令 flow 收集(蓝牙事件流在断线后仍保持)。 */
    private object SessionEnded : Exception()

    private companion object {
        const val CGM_TAG = "Cgm.Port"
        const val COMMAND_IDLE_TIMEOUT_MILLIS = 60_000L   // 空闲超时:距上次事件 60s 无数据才判超时
        const val DELETE_ACK = "Log Cleared"
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy,MM,dd,HH,mm,ss")
    }
}
