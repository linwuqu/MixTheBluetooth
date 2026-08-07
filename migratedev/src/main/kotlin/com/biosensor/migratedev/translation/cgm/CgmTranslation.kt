package com.biosensor.migratedev.translation.cgm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import com.biosensor.migratedev.translation.Translation
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface CgmIntent {
    data object ReadCache : CgmIntent
    data object SyncTime : CgmIntent
    data object DeleteCache : CgmIntent
    // 无 Stop / ResetError:停止与复位是隐式机制(架构 07 §1.7 ③),不向 UI 暴露——
    // 错误态自动复位,至多 toast;按钮常亮可点,冲突由 submit 拒绝
}

enum class CgmReadPhase { Idle, Sending, Receiving, Completed, Failed, Stopped, Error }

data class CgmShortResult(
    val purpose: CgmCommandPurpose, val ok: Boolean, val message: String
)

data class CgmUiState(
    val readPhase: CgmReadPhase,
    val readMessage: String?,
    val progressPoints: Int,          // 已接收 EIS 点数
    val roundCount: Int,              // 已接收完整轮数(近似)
    val recentLines: List<String>,    // 最近 10 条记录文本(数据看板)
    val shortResult: CgmShortResult?, // 最近一次短命令结果
    /** 冲突拒绝提示(§1.7 ②):按钮常亮可点,冲突由 submit 拒绝并展示,至下次操作消失。 */
    val hint: String? = null
    // 无可用态字段:按钮常亮可点,冲突由 submit 拒绝并 toast(架构 07 §1.7 ①②)
)

sealed interface CgmOutput {
    data class ReadCompleted(val path: String) : CgmOutput
    data class ShortDone(val purpose: CgmCommandPurpose) : CgmOutput
    data class Failed(val message: String) : CgmOutput
    /** 互转冲突:命令被拒,toast 提示等待(架构 07 §1.7 ②)。 */
    data class Blocked(val hint: String) : CgmOutput
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

    override val uiState: StateFlow<CgmUiState> =
        combine(readOrchestrator.state, shortOrchestrator.state, hint) { read, short, hint ->
            read.toUi(short, hint)
        }.stateIn(
            viewModelScope, SharingStarted.Eagerly,
            CgmReadState.Idle.toUi(CgmShortState.Idle, null)
        )

    // ── 互转策略(§1.7 ②):submit 是通往 orchestrator 的唯一入口 ──
    // 冲突(任一侧活跃)→ 拒绝执行 + report Blocked(toast 提示等待),不隐式打断;
    // Error 态 → 自动复位后执行(复位无感,用户透明)。
    override fun submit(intent: CgmIntent) {
        when (intent) {
            CgmIntent.ReadCache -> startRead()
            CgmIntent.SyncTime -> startShort(CgmCommandPurpose.SYNC_TIME)
            CgmIntent.DeleteCache -> startShort(CgmCommandPurpose.DELETE)
        }
    }

    private fun startRead() {
        val short = shortOrchestrator.state.value
        if (short.isActive()) return blocked(short.hint())      // 短命令进行中:拒绝 + toast
        hint.value = null                                       // 新命令开始,清除旧提示
        resetErrorIfNeeded()
        readOrchestrator.dispatch(CgmReadEvent.ReadRequested(newSessionId(), deviceId))
    }

    private fun startShort(purpose: CgmCommandPurpose) {
        val read = readOrchestrator.state.value
        val short = shortOrchestrator.state.value
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
            // 双闸门完成:Completed(带 filePath)经 FileWritten/AckReceived 第二闸 → Stopped
            previous is CgmReadState.Completed && current is CgmReadState.Stopped &&
                previous.filePath != null ->
                report(CgmOutput.ReadCompleted(previous.filePath))
            current is CgmReadState.Failed -> report(CgmOutput.Failed(current.reason))
            current is CgmReadState.Error -> report(CgmOutput.Failed(current.message))
            else -> Unit
        }
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

private fun CgmReadState.toUi(short: CgmShortState, hint: String?): CgmUiState {
    val (phase, message) = toPhase()
    val eis = accumulated?.filterIsInstance<CgmRecord.Eis>() ?: emptyList()
    return CgmUiState(
        readPhase = phase,
        readMessage = message,
        progressPoints = eis.size,
        roundCount = eis.maxOfOrNull { it.seq }?.let { (it - 1) / 95 + 1 } ?: 0,
        recentLines = accumulated.orEmpty().takeLast(10).map { it.text },
        shortResult = short.toResult(),
        hint = hint
    )
}

/** 活跃判定与 toast 文案(§1.7 ②:冲突拒绝时的提示)。 */
private fun CgmReadState.isActive(): Boolean = when (this) {
    is CgmReadState.Sending, is CgmReadState.Receiving, is CgmReadState.Completed -> true
    else -> false
}

private fun CgmShortState.isActive(): Boolean = when (this) {
    is CgmShortState.Sending, is CgmShortState.WaitingAck -> true
    else -> false
}

private fun CgmReadState.hint(): String = when (this) {
    is CgmReadState.Receiving -> "正在读取缓存中,请等待执行完毕"
    else -> "正在执行读取中,请等待执行完毕"
}

private fun CgmShortState.hint(): String =
    "正在执行命令中,请等待执行完毕"

private val CgmReadState.accumulated: List<CgmRecord>?
    get() = when (this) {
        is CgmReadState.Receiving -> accumulated
        is CgmReadState.Failed -> accumulated
        else -> null
    }

private fun CgmReadState.toPhase(): Pair<CgmReadPhase, String?> = when (this) {
    CgmReadState.Idle -> CgmReadPhase.Idle to null
    is CgmReadState.Sending -> CgmReadPhase.Sending to "命令发送中"
    is CgmReadState.Receiving -> CgmReadPhase.Receiving to "接收中(第 ${retryCount + 1} 次尝试)"
    is CgmReadState.Completed -> CgmReadPhase.Completed to "校验通过,落盘中"
    is CgmReadState.Failed -> CgmReadPhase.Failed to reason
    is CgmReadState.Error -> CgmReadPhase.Error to message
    is CgmReadState.Stopped -> CgmReadPhase.Stopped to null
}

private fun CgmShortState.toResult(): CgmShortResult? = when (this) {
    is CgmShortState.Done -> CgmShortResult(
        purpose, ok = true,
        message = if (purpose == CgmCommandPurpose.SYNC_TIME) "对时成功" else "删除成功")
    is CgmShortState.Failed -> CgmShortResult(CgmCommandPurpose.SYNC_TIME, ok = false, message = reason)
    else -> null
}
