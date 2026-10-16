package com.biosensor.migratedev.translation.cgm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.biosensor.migratedev.decisioncore.cgm.ValidationCheck
import com.biosensor.migratedev.decisioncore.cgm.CgmFileAppendResult
import com.biosensor.migratedev.decisioncore.cgm.CgmCommandPurpose
import com.biosensor.migratedev.decisioncore.cgm.CgmReadDecision
import com.biosensor.migratedev.decisioncore.cgm.CgmReadEvent
import com.biosensor.migratedev.decisioncore.cgm.CgmReadState
import com.biosensor.migratedev.decisioncore.cgm.CgmRecord
import com.biosensor.migratedev.decisioncore.cgm.CgmShortDecision
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEvent
import com.biosensor.migratedev.decisioncore.cgm.CgmShortState
import com.biosensor.migratedev.orchestrator.WorkflowOrchestrator
import com.biosensor.migratedev.port.cgm.CgmPort
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothConnectionState
import com.biosensor.migratedev.translation.Translation
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CgmIntent {
    data object ReadCache : CgmIntent
    data object SyncTime : CgmIntent
    data object DeleteCache : CgmIntent
    /** UI 长按读缓存 3 秒：强制终止当前读取会话，但保持蓝牙连接。 */
    data object ForceStopRead : CgmIntent
    // ResetError 仍是隐式机制；ForceStopRead 仅用于 3 秒长按的超时逃生入口。
    /** 内部信号(非 UI):断线重连成功,Root 转发。 */
    data object DeviceReconnected : CgmIntent
    /** 内部信号(非 UI):断线重连失败,Root 转发。 */
    data object DeviceReconnectFailed : CgmIntent
}

enum class CgmReadPhase { Idle, Sending, Receiving, Completed, Failed, Reconnecting, Stopped, Error }

enum class CgmConnectionStatus { Connected, Reconnecting, Disconnected }

data class CgmShortResult(
    val purpose: CgmCommandPurpose, val ok: Boolean, val message: String
)

data class CgmUiState(
    val readPhase: CgmReadPhase,
    val readMessage: String?,
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
    val deviceId: String? = null
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
 * CgmTranslation:读流程与短命令各一个 orchestrator,互斥由 submit 检查。
 * ViewModel 生命周期 = 页面生命周期,旋转重建不销毁(架构 07 §11.4)。
 */
class CgmTranslation private constructor(
    private val deviceId: String,
    private val port: CgmPort,
    private val report: (CgmOutput) -> Unit
) : ViewModel(), Translation<CgmIntent, CgmUiState> {

    private val readOrchestrator = WorkflowOrchestrator(
        initialState = CgmReadState.Idle,
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

    /** 冲突提示(§1.7 ②):拒绝时写入,下次命令开始清除——等价 toast 的一次性提示。 */
    private val hint = MutableStateFlow<String?>(null)
    /** 从发现掉线到连接域给出最终结果，导航栏始终显示“重连中”。 */
    private val reconnectInProgress = MutableStateFlow(false)

    private val connectionState = port.observeConnectionState().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        BluetoothConnectionState.CONNECTED
    )

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
            readOrchestrator.state, shortOrchestrator.state, hint, connectionState,
            reconnectInProgress
        ) { read, short, hint, connection, reconnecting ->
            val status = if (reconnecting) CgmConnectionStatus.Reconnecting
            else connection.toUiStatus()
            read.toUi(short, hint, status)
        }.stateIn(
            viewModelScope, SharingStarted.Eagerly,
            CgmReadState.Idle.toUi(
                CgmShortState.Idle, null, CgmConnectionStatus.Connected)
        )

    // ── 互转策略(§1.7 ②):submit 是通往 orchestrator 的唯一入口 ──
    // 冲突(任一侧活跃)→ 拒绝执行 + report Blocked(toast 提示等待),不隐式打断;
    // Error 态 → 自动复位后执行(复位无感,用户透明)。
    override fun submit(intent: CgmIntent) {
        when (intent) {
            CgmIntent.ReadCache -> startRead()
            CgmIntent.SyncTime -> startShort(CgmCommandPurpose.SYNC_TIME)
            CgmIntent.DeleteCache -> startShort(CgmCommandPurpose.DELETE)
            CgmIntent.ForceStopRead -> forceStopRead()
            // 内部信号:断线重连结果(Root 转发,UI 不触发)
            CgmIntent.DeviceReconnected -> {
                reconnectInProgress.value = false
                val reconnecting = readOrchestrator.state.value
                if (reconnecting is CgmReadState.Reconnecting) {
                    readOrchestrator.dispatch(
                        CgmReadEvent.DeviceReconnected(reconnecting.session.id))
                }
            }
            CgmIntent.DeviceReconnectFailed -> {
                reconnectInProgress.value = false
                // 允许用户在设备重新上电后，通过任一命令按钮再次触发连接域重连。
                reconnectReported = false
                val reconnecting = readOrchestrator.state.value
                if (reconnecting is CgmReadState.Reconnecting) {
                    readOrchestrator.dispatch(
                        CgmReadEvent.DeviceReconnectFailed(reconnecting.session.id, "重连失败"))
                }
            }
        }
    }

    private fun startRead() {
        val read = readOrchestrator.state.value
        val short = shortOrchestrator.state.value
        connectionBlockHint()?.let { return blocked(it) }
        if (short.isActive()) return blocked(short.hint())      // 短命令进行中:拒绝 + toast
        if (read.isActive()) return blocked(read.hint())        // 防重复点击读缓存
        hint.value = null                                       // 新命令开始,清除旧提示
        resetErrorIfNeeded()
        readOrchestrator.dispatch(CgmReadEvent.ReadRequested(newSessionId(), deviceId))
    }

    private fun forceStopRead() {
        val read = readOrchestrator.state.value
        val session = read.sessionOrNull ?: return blocked("当前没有进行中的读取")
        if (!read.isActive()) return blocked("当前没有进行中的读取")
        hint.value = "已强制停止本次读取"
        readOrchestrator.dispatch(CgmReadEvent.StopCurrent(session.deviceId))
    }

    private fun startShort(purpose: CgmCommandPurpose) {
        val read = readOrchestrator.state.value
        val short = shortOrchestrator.state.value
        connectionBlockHint()?.let { return blocked(it) }
        if (short.isActive()) return blocked(short.hint())      // 自身活跃(防连点):拒绝 + toast
        if (read.isActive()) return blocked(read.hint())        // 读活跃(昂贵操作):拒绝 + toast
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
        val read = readOrchestrator.state.value
        if (read is CgmReadState.Error) {
            readOrchestrator.dispatch(CgmReadEvent.ResetError)
        }
    }

    override fun onCleared() {
        connectionCollection.cancel()
        readOrchestrator.close()
        shortOrchestrator.close()
    }

    // ── 上报(§10.1):只报结论,不报过程 ──
    // 幂等转移(previous == current,如 Error 状态收到迟到确认)不是新结论,不上报——
    // 否则 Error/终态会因迟到事件反复上报同一条 Failed。
    private fun onReadTransition(
        previous: CgmReadState, event: CgmReadEvent, current: CgmReadState
    ) {
        if (previous == current) return
        when {
            // 测试阶段关闭自动删除后：FileWritten 直接跨过已满足的删除闸门进入 Stopped。
            event is CgmReadEvent.FileWritten && current is CgmReadState.Stopped ->
                report(CgmOutput.ReadCompleted(event.path))
            // 双闸门完成:Completed(带 filePath)经 FileWritten/AckReceived 第二闸 → Stopped
            previous is CgmReadState.Completed && current is CgmReadState.Stopped &&
                previous.filePath != null ->
                report(CgmOutput.ReadCompleted(previous.filePath))
            current is CgmReadState.Failed -> report(CgmOutput.Failed(current.reason))
            current is CgmReadState.Error -> report(CgmOutput.Failed(current.message))
            // 断线 → 上报 Root 驱动重连(无命令 effect,重连是连接域职责)
            current is CgmReadState.Reconnecting -> reportConnectionLostOnce()
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
            deviceId: String, port: CgmPort, report: (CgmOutput) -> Unit = {}
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(CgmTranslation::class.java))
                return CgmTranslation(deviceId, port, report) as T
            }
        }
    }
}

private fun CgmReadState.toUi(
    short: CgmShortState,
    hint: String?,
    connectionStatus: CgmConnectionStatus
): CgmUiState {
    val (phase, message) = toPhase()
    val records = displayRecords()
    val summary = summarizeRecords(records)
    val session = displaySession()
    return CgmUiState(
        readPhase = phase,
        readMessage = message,
        progressPoints = summary.eisPoints + summary.caPoints,
        roundCount = summary.segments,
        dataLines = records.map { it.text },
        shortResult = short.toResult(),
        hint = hint,
        connectionStatus = connectionStatus,
        summary = summary,
        validationChecks = report?.checks.orEmpty(),
        storageResult = report?.storage,
        sessionId = session?.id,
        deviceId = session?.deviceId
    )
}

/** 活跃判定与 toast 文案(§1.7 ②:冲突拒绝时的提示)。 */
private fun CgmReadState.isActive(): Boolean = when (this) {
    // Reconnecting 也活跃:重连后自动重读继续,期间拒绝新命令避免会话冲突
    is CgmReadState.Sending, is CgmReadState.Receiving, is CgmReadState.Completed,
    is CgmReadState.Reconnecting -> true
    else -> false
}

private fun CgmShortState.isActive(): Boolean = when (this) {
    is CgmShortState.Sending, is CgmShortState.WaitingAck -> true
    else -> false
}

private fun CgmReadState.hint(): String = when (this) {
    is CgmReadState.Receiving -> "正在读取缓存中,请等待执行完毕"
    is CgmReadState.Reconnecting -> "连接中断,正在重连中,请稍候"
    else -> "正在执行读取中,请等待执行完毕"
}

private fun CgmShortState.hint(): String =
    "正在执行命令中,请等待执行完毕"

private fun CgmReadState.toPhase(): Pair<CgmReadPhase, String?> = when (this) {
    CgmReadState.Idle -> CgmReadPhase.Idle to null
    is CgmReadState.Sending -> CgmReadPhase.Sending to "命令发送中"
    is CgmReadState.Receiving -> CgmReadPhase.Receiving to "接收中(第 ${retryCount + 1} 次尝试)"
    is CgmReadState.Completed -> CgmReadPhase.Completed to "校验通过，正在追加保存"
    is CgmReadState.Failed -> CgmReadPhase.Failed to reason
    is CgmReadState.Error -> CgmReadPhase.Error to message
    is CgmReadState.Reconnecting -> CgmReadPhase.Reconnecting to "连接中断,正在重连…"
    is CgmReadState.Stopped -> CgmReadPhase.Stopped to when (reason) {
        com.biosensor.migratedev.decisioncore.cgm.StopReason.USER_CANCEL -> "读取已强制停止"
        else -> "读取完成(会话已关闭,数据保留)"
    }
}

private fun CgmShortState.toResult(): CgmShortResult? = when (this) {
    is CgmShortState.Done -> CgmShortResult(
        purpose, ok = true,
        message = if (purpose == CgmCommandPurpose.SYNC_TIME) "对时成功" else "删除成功")
    is CgmShortState.Failed -> CgmShortResult(purpose, ok = false, message = reason)
    else -> null
}

private val CgmReadState.sessionOrNull
    get() = when (this) {
        is CgmReadState.Sending -> session
        is CgmReadState.Receiving -> session
        is CgmReadState.Completed -> session
        is CgmReadState.Failed -> session
        is CgmReadState.Reconnecting -> session
        is CgmReadState.Stopped -> session
        else -> null
    }

private fun BluetoothConnectionState.toUiStatus(): CgmConnectionStatus = when (this) {
    BluetoothConnectionState.CONNECTED -> CgmConnectionStatus.Connected
    BluetoothConnectionState.CONNECTING -> CgmConnectionStatus.Reconnecting
    BluetoothConnectionState.DISCONNECTED -> CgmConnectionStatus.Disconnected
}
