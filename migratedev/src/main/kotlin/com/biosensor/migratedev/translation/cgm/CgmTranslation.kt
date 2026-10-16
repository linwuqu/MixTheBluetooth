package com.biosensor.migratedev.translation.cgm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.biosensor.migratedev.decisioncore.cgm.ValidationCheck
import com.biosensor.migratedev.decisioncore.cgm.CgmFileAppendResult
import com.biosensor.migratedev.decisioncore.cgm.CgmCommandPurpose
import com.biosensor.migratedev.decisioncore.cgm.CgmReadDecision
import com.biosensor.migratedev.decisioncore.cgm.CgmReadEvent
import com.biosensor.migratedev.decisioncore.cgm.CgmReadPhase
import com.biosensor.migratedev.decisioncore.cgm.CgmReadState
import com.biosensor.migratedev.decisioncore.cgm.CgmShortDecision
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEvent
import com.biosensor.migratedev.decisioncore.cgm.CgmShortState
import com.biosensor.migratedev.decisioncore.cgm.StopReason
import com.biosensor.migratedev.decisioncore.upload.CgmResult
import com.biosensor.migratedev.decisioncore.upload.UploadDecisionCore
import com.biosensor.migratedev.decisioncore.upload.UploadEvent
import com.biosensor.migratedev.decisioncore.upload.UploadPhase
import com.biosensor.migratedev.decisioncore.upload.UploadState
import com.biosensor.migratedev.orchestrator.WorkflowOrchestrator
import com.biosensor.migratedev.port.cgm.CgmPort
import com.biosensor.migratedev.port.cgm.cgmCachePath
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothConnectionState
import com.biosensor.migratedev.port.upload.UploadPort
import com.biosensor.migratedev.translation.Translation
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CgmIntent {
    data object ReadCache : CgmIntent
    data object SyncTime : CgmIntent
    data object DeleteCache : CgmIntent
    /** 把本地缓存文件推给后端,拿回分析结果。 */
    data object UploadDataset : CgmIntent
    /** UI 长按读缓存 3 秒：强制终止当前读取会话，但保持蓝牙连接。 */
    data object ForceStopRead : CgmIntent
    // ResetError 仍是隐式机制；ForceStopRead 仅用于 3 秒长按的超时逃生入口。
    /** 内部信号(非 UI):断线重连成功,Root 转发。 */
    data object DeviceReconnected : CgmIntent
    /** 内部信号(非 UI):断线重连失败,Root 转发。 */
    data object DeviceReconnectFailed : CgmIntent
}

enum class CgmConnectionStatus { Connected, Reconnecting, Disconnected }

data class CgmShortResult(
    val purpose: CgmCommandPurpose, val ok: Boolean, val message: String
)

data class CgmUiState(
    val readPhase: CgmReadPhase,
    val readMessage: String?,
    /** STOPPED 的原因:区分"读取完成"与"读取已停止"。 */
    val stopReason: StopReason? = null,
    val progressPoints: Int,          // 本次接收的 EIS + CA 点数
    val roundCount: Int,              // LOG 段数
    val dataLines: List<String>,      // 本次完整记录
    val shortResult: CgmShortResult?, // 最近一次短命令结果
    /** 冲突拒绝提示(§1.7 ②):按钮常亮可点,冲突由 submit 拒绝并展示,至下次操作消失。 */
    val hint: String? = null,
    val connectionStatus: CgmConnectionStatus = CgmConnectionStatus.Connected,
    val summary: CgmDataSummary = CgmDataSummary(),
    val validationChecks: List<ValidationCheck> = emptyList(),
    val storageResult: CgmFileAppendResult? = null,
    val sessionId: String? = null,
    val deviceId: String? = null,
    // ── 上传与分析结果:与读流程同一页,状态各自独立 ──
    val uploadPhase: UploadPhase = UploadPhase.IDLE,
    /** 上传失败的原因;进行中与成功都是 null。 */
    val uploadMessage: String? = null,
    /** 最近一次拿到的分析结果;没有就是 null。 */
    val result: CgmResult? = null
    // 无可用态字段:按钮常亮可点,冲突由 submit 拒绝并 toast(架构 07 §1.7 ①②)
)

sealed interface CgmOutput {
    data class ReadCompleted(val path: String) : CgmOutput
    data class ShortDone(val purpose: CgmCommandPurpose) : CgmOutput
    data class Failed(val message: String) : CgmOutput
    /** 互转冲突:命令被拒,toast 提示等待(架构 07 §1.7 ②)。 */
    data class Blocked(val hint: String) : CgmOutput
    /** 断线(读/短命令会话中):Root 据此驱动 Connection 域重连。 */
    data object ConnectionLost : CgmOutput
}

/**
 * CgmTranslation:读流程、短命令与上传各一个 orchestrator,互斥由 submit 检查。
 * ViewModel 生命周期 = 页面生命周期,旋转重建不销毁(架构 07 §11.4)。
 */
class CgmTranslation private constructor(
    private val deviceId: String,
    private val port: CgmPort,
    private val uploadPort: UploadPort,
    private val report: (CgmOutput) -> Unit
) : ViewModel(), Translation<CgmIntent, CgmUiState> {

    private val readOrchestrator = WorkflowOrchestrator(
        initialState = CgmReadState(),
        decisionCore = CgmReadDecision,
        effectExecutor = port::execute,
        scope = viewModelScope,
        logTag = "Cgm.Read",
        onTransition = ::onReadTransition
    )

    private val shortOrchestrator = WorkflowOrchestrator(
        initialState = CgmShortState.Idle,
        decisionCore = CgmShortDecision,
        effectExecutor = port::execute,
        scope = viewModelScope,
        logTag = "Cgm.Short",
        onTransition = ::onShortTransition
    )

    /** 上传不碰设备,也就不经 CgmPort:它是另一条链路(本地文件 → HTTP)。 */
    private val uploadOrchestrator = WorkflowOrchestrator(
        initialState = UploadState.Idle,
        decisionCore = UploadDecisionCore,
        effectExecutor = uploadPort::execute,
        scope = viewModelScope,
        logTag = "Cgm.Upload"
    )

    /** 冲突提示(§1.7 ②):拒绝时写入,下次命令开始清除——等价 toast 的一次性提示。 */
    private val hint = MutableStateFlow<String?>(null)
    /** 从发现掉线到连接域给出最终结果，导航栏始终显示“重连中”。 */
    private val reconnectInProgress = MutableStateFlow(false)

    private val connectionState = port.observeConnectionState().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        BluetoothConnectionState.CONNECTED
    )

    /** 物理连接 → 对外三态:正在重连时盖过物理状态(Root 那边还没给结论)。 */
    private val connectionStatus: StateFlow<CgmConnectionStatus> = combine(
        connectionState, reconnectInProgress
    ) { connection, reconnecting ->
        if (reconnecting) CgmConnectionStatus.Reconnecting else connection.toUiStatus()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CgmConnectionStatus.Connected)

    private var previousConnectionState = BluetoothConnectionState.CONNECTED
    private var reconnectReported = false

    /** 页面存活期间持续观察物理连接；空闲时掉线也能触发 Root 重连。 */
    private val connectionCollection = viewModelScope.launch {
        connectionState.collect { current ->
            if (previousConnectionState == BluetoothConnectionState.CONNECTED &&
                current == BluetoothConnectionState.DISCONNECTED
            ) {
                reportConnectionLostOnce()
            }
            if (current == BluetoothConnectionState.CONNECTED) {
                reconnectReported = false
                reconnectInProgress.value = false
            }
            previousConnectionState = current
        }
    }

    override val uiState: StateFlow<CgmUiState> =
        combine(
            readOrchestrator.state, shortOrchestrator.state, uploadOrchestrator.state,
            hint, connectionStatus
        ) { read, short, upload, hint, status ->
            read.toUi(short, upload, hint, status)
        }.stateIn(
            viewModelScope, SharingStarted.Eagerly,
            CgmReadState().toUi(
                CgmShortState.Idle, UploadState.Idle, null, CgmConnectionStatus.Connected)
        )

    // ── 互转策略(§1.7 ②):submit 是通往 orchestrator 的唯一入口 ──
    // 冲突(任一侧活跃)→ 拒绝执行 + report Blocked(toast 提示等待),不隐式打断;
    // Error 态 → 自动复位后执行(复位无感,用户透明)。
    override fun submit(intent: CgmIntent) {
        when (intent) {
            CgmIntent.ReadCache -> startRead()
            CgmIntent.SyncTime -> startShort(CgmCommandPurpose.SYNC_TIME)
            CgmIntent.DeleteCache -> startShort(CgmCommandPurpose.DELETE)
            CgmIntent.UploadDataset -> startUpload()
            CgmIntent.ForceStopRead -> forceStopRead()
            // 内部信号:断线重连结果(Root 转发,UI 不触发)
            CgmIntent.DeviceReconnected -> {
                reconnectInProgress.value = false
                reconnectingSession()?.let {
                    readOrchestrator.dispatch(CgmReadEvent.DeviceReconnected(it.id))
                }
            }
            CgmIntent.DeviceReconnectFailed -> {
                reconnectInProgress.value = false
                // 允许用户在设备重新上电后，通过任一命令按钮再次触发连接域重连。
                reconnectReported = false
                reconnectingSession()?.let {
                    readOrchestrator.dispatch(
                        CgmReadEvent.DeviceReconnectFailed(it.id, "重连失败"))
                }
            }
        }
    }

    /** 正在等重连的会话;不在重连态则为 null(迟到的重连结果忽略)。 */
    private fun reconnectingSession() = readOrchestrator.state.value
        .takeIf { it.phase == CgmReadPhase.RECONNECTING }
        ?.session

    private fun startRead() {
        val read = readOrchestrator.state.value
        val short = shortOrchestrator.state.value
        val upload = uploadOrchestrator.state.value
        connectionBlockHint()?.let { return blocked(it) }
        if (short.isActive()) return blocked(short.hint())      // 短命令进行中:拒绝 + toast
        if (upload.phase.running) return blocked(upload.hint()) // 上传中:文件在传,别同时写
        if (read.phase.running) return blocked(read.hint())     // 防重复点击读缓存
        hint.value = null                                       // 新命令开始,清除旧提示
        resetErrorIfNeeded()
        readOrchestrator.dispatch(CgmReadEvent.ReadRequested(newSessionId(), deviceId))
    }

    /**
     * 上传的是本地缓存文件,与蓝牙无关,所以不看连接状态。
     * 但读写同一份文件:读在写、或上一次上传还没回来,都拒绝。
     */
    private fun startUpload() {
        val read = readOrchestrator.state.value
        val short = shortOrchestrator.state.value
        val upload = uploadOrchestrator.state.value
        if (upload.phase.running) return blocked(upload.hint()) // 防连点
        if (read.phase.running) return blocked(read.hint())     // 读在追加写:内容还没定型
        if (short.isActive()) return blocked(short.hint())      // 短命令进行中:文件可能被删
        hint.value = null                                       // 新命令开始,清除旧提示
        uploadOrchestrator.dispatch(UploadEvent.UploadRequested(cgmCachePath(deviceId)))
    }

    private fun forceStopRead() {
        val read = readOrchestrator.state.value
        val session = read.session
        if (session == null || !read.phase.running) return blocked("当前没有进行中的读取")
        hint.value = "已强制停止本次读取"
        readOrchestrator.dispatch(CgmReadEvent.StopCurrent(session.deviceId))
    }

    private fun startShort(purpose: CgmCommandPurpose) {
        val read = readOrchestrator.state.value
        val short = shortOrchestrator.state.value
        val upload = uploadOrchestrator.state.value
        connectionBlockHint()?.let { return blocked(it) }
        if (short.isActive()) return blocked(short.hint())      // 自身活跃(防连点):拒绝 + toast
        if (upload.phase.running) return blocked(upload.hint()) // 上传中:删除会抽掉正在传的文件
        if (read.phase.running) return blocked(read.hint())     // 读活跃(昂贵操作):拒绝 + toast
        hint.value = null                                       // 新命令开始,清除旧提示
        resetErrorIfNeeded()
        shortOrchestrator.dispatch(
            CgmShortEvent.ShortRequested(newSessionId(), deviceId, purpose))
    }

    private fun blocked(hintText: String) {
        hint.value = hintText
        report(CgmOutput.Blocked(hintText))
    }

    /** Error 态自动复位(无 toast)。dispatch 同步入队单循环处理,先复位后开始。 */
    private fun resetErrorIfNeeded() {
        if (readOrchestrator.state.value.phase == CgmReadPhase.ERROR) {
            readOrchestrator.dispatch(CgmReadEvent.ResetError)
        }
    }

    override fun onCleared() {
        connectionCollection.cancel()
        readOrchestrator.close()
        shortOrchestrator.close()
        uploadOrchestrator.close()
    }

    // ── 上报(§10.1):只报结论,不报过程 ──
    // 幂等转移(previous == current,如 Error 状态收到迟到确认)不是新结论,不上报——
    // 否则 Error/终态会因迟到事件反复上报同一条 Failed。
    private fun onReadTransition(
        previous: CgmReadState, event: CgmReadEvent, current: CgmReadState
    ) {
        if (previous == current) return
        when {
            // 入库校验完成 → STOPPED:读流程收工
            event is CgmReadEvent.FileWritten && current.phase == CgmReadPhase.STOPPED ->
                report(CgmOutput.ReadCompleted(event.path))
            current.phase == CgmReadPhase.FAILED ->
                report(CgmOutput.Failed(current.message ?: "读取失败"))
            current.phase == CgmReadPhase.ERROR ->
                report(CgmOutput.Failed(current.message ?: "写入失败"))
            // 断线 → 上报 Root 驱动重连(无命令 effect,重连是连接域职责)
            current.phase == CgmReadPhase.RECONNECTING -> reportConnectionLostOnce()
            else -> Unit
        }
    }

    private fun connectionBlockHint(): String? = when {
        reconnectInProgress.value || connectionState.value == BluetoothConnectionState.CONNECTING ->
            "蓝牙正在重连中,请稍候"
        connectionState.value == BluetoothConnectionState.DISCONNECTED -> {
            reportConnectionLostOnce()
            "蓝牙已掉线,正在尝试重连"
        }
        else -> null
    }

    private fun reportConnectionLostOnce() {
        if (reconnectReported) return
        reconnectReported = true
        reconnectInProgress.value = true
        report(CgmOutput.ConnectionLost)
    }

    private fun onShortTransition(
        previous: CgmShortState, event: CgmShortEvent, current: CgmShortState
    ) {
        if (previous == current) return
        when (current) {
            is CgmShortState.Done -> report(CgmOutput.ShortDone(current.purpose))
            is CgmShortState.Failed -> report(CgmOutput.Failed(current.reason))
            else -> Unit
        }
    }

    private fun newSessionId(): String = UUID.randomUUID().toString()

    companion object {
        fun factory(
            deviceId: String,
            port: CgmPort,
            uploadPort: UploadPort,
            report: (CgmOutput) -> Unit = {}
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(CgmTranslation::class.java))
                return CgmTranslation(deviceId, port, uploadPort, report) as T
            }
        }
    }
}

private fun CgmReadState.toUi(
    short: CgmShortState,
    upload: UploadState,
    hint: String?,
    connectionStatus: CgmConnectionStatus
): CgmUiState {
    val shown = displayRecords
    val summary = summarizeRecords(shown)
    return CgmUiState(
        readPhase = phase,
        readMessage = readMessage(),
        stopReason = stopReason,
        progressPoints = summary.eisPoints + summary.caPoints,
        roundCount = summary.segments,
        dataLines = shown.map { it.text },
        shortResult = short.toResult(),
        hint = hint,
        connectionStatus = connectionStatus,
        summary = summary,
        validationChecks = report?.checks.orEmpty(),
        storageResult = report?.storage,
        sessionId = session?.id,
        deviceId = session?.deviceId,
        uploadPhase = upload.phase,
        uploadMessage = upload.message(),
        result = (upload as? UploadState.Done)?.result
    )
}

/**
 * 过程细节。阶段标签([CgmStatusArea])已说明状态,这里只补它没说的事:
 * 失败原因,以及"这是第几轮重读"。没有新信息就返回 null,界面不留重复行。
 */
private fun CgmReadState.readMessage(): String? = when (phase) {
    CgmReadPhase.IDLE, CgmReadPhase.SENDING, CgmReadPhase.WRITING,
    CgmReadPhase.RECONNECTING, CgmReadPhase.STOPPED -> null
    CgmReadPhase.RECEIVING -> if (retryCount == 0) null else "第 ${retryCount + 1} 次尝试"
    CgmReadPhase.FAILED, CgmReadPhase.ERROR -> message
}

/** 冲突拒绝时的提示文案(§1.7 ②)。 */
private fun CgmReadState.hint(): String = when (phase) {
    CgmReadPhase.RECEIVING -> "正在读取缓存中,请等待执行完毕"
    CgmReadPhase.RECONNECTING -> "连接中断,正在重连中,请稍候"
    else -> "正在执行读取中,请等待执行完毕"
}

private fun CgmShortState.isActive(): Boolean = when (this) {
    is CgmShortState.Sending, is CgmShortState.WaitingAck -> true
    else -> false
}

/** 上传过程中只有失败要说话:推进到哪一步由阶段标签说明。 */
private fun UploadState.message(): String? = (this as? UploadState.Failed)?.reason

/** 上传冲突拒绝时的提示文案;阶段不同,等的事不同。 */
private fun UploadState.hint(): String = when (phase) {
    UploadPhase.UPLOADING -> "正在上传数据中,请等待执行完毕"
    UploadPhase.POLLING -> "正在等待后端分析中,请稍候"
    UploadPhase.FETCHING -> "正在获取分析结果中,请稍候"
    else -> "正在上传中,请等待执行完毕"
}

private fun CgmShortState.hint(): String = "正在执行命令中,请等待执行完毕"

private fun CgmShortState.toResult(): CgmShortResult? = when (this) {
    is CgmShortState.Done -> CgmShortResult(
        purpose, ok = true,
        message = if (purpose == CgmCommandPurpose.SYNC_TIME) "对时成功" else "删除成功")
    is CgmShortState.Failed -> CgmShortResult(purpose, ok = false, message = reason)
    else -> null
}

private fun BluetoothConnectionState.toUiStatus(): CgmConnectionStatus = when (this) {
    BluetoothConnectionState.CONNECTED -> CgmConnectionStatus.Connected
    BluetoothConnectionState.CONNECTING -> CgmConnectionStatus.Reconnecting
    BluetoothConnectionState.DISCONNECTED -> CgmConnectionStatus.Disconnected
}
