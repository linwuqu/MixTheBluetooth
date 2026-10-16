package com.biosensor.migratedev.translation.patch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.biosensor.migratedev.decisioncore.patch.PatchBinding
import com.biosensor.migratedev.decisioncore.patch.PatchDecisionCore
import com.biosensor.migratedev.decisioncore.patch.PatchEvent
import com.biosensor.migratedev.decisioncore.patch.PatchState
import com.biosensor.migratedev.orchestrator.WorkflowOrchestrator
import com.biosensor.migratedev.port.patch.PatchPort
import com.biosensor.migratedev.translation.Translation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface PatchIntent {
    /** UI:开扫码子页(库里没有绑定自动开 / 点标题行按钮)。 */
    data object OpenScanner : PatchIntent

    /** UI:关扫码子页(返回键 / 跳过 / 相机权限被拒)。 */
    data class CloseScanner(val reason: String? = null) : PatchIntent

    /** 相机回调:扫到一段内容。文本为空时用原始字节兜底。 */
    data class CodeScanned(val text: String?, val rawBytes: ByteArray?) : PatchIntent
}

/** 对界面:贴片域的全部可见状态。CGM 页只是它的一个读者,不并进 CgmUiState(架构 13 §D3)。 */
data class PatchUiState(
    /** 这台设备还没绑过任何贴片 → 进页面自动开扫码页。开过一次就变 false,不反复弹。 */
    val shouldScan: Boolean = false,
    /** 扫码子页占着屏幕(写库时也还开着)。 */
    val scanning: Boolean = false,
    /** 这台设备绑过的贴片,最近绑的排前。 */
    val bindings: List<PatchBinding> = emptyList(),
    /** 出问题的原因:解析不过 / 写库失败 / 读绑定失败。没有就是 null。 */
    val message: String? = null,
)

/**
 * 贴片翻译层:收扫码结果与页面动作,把绑定列表给界面。
 *
 * 与 Cgm/Connection 同款:一个 [WorkflowOrchestrator] + 一个 [PatchPort],进页面即开会话
 * (读这台设备已有的绑定)。**不向 Root 上报**——绑定不改导航、不改 Root 状态,
 * 与 ConnectionLost 那种需要驱动重连的结论不同,多一条恒为 Unit 的 report 分支只是死代码。
 */
class PatchTranslation private constructor(
    private val userId: String,
    private val deviceId: String,
    private val port: PatchPort,
) : ViewModel(), Translation<PatchIntent, PatchUiState> {

    private val orchestrator = WorkflowOrchestrator(
        initialState = PatchState(),
        decisionCore = PatchDecisionCore,
        effectExecutor = port::execute,
        scope = viewModelScope,
        logTag = "Patch.Binding"
    )

    override val uiState: StateFlow<PatchUiState> = orchestrator.state
        .map { it.toUi() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PatchState().toUi())

    init {
        orchestrator.dispatch(PatchEvent.SessionRequested(userId, deviceId))
    }

    override fun submit(intent: PatchIntent) {
        when (intent) {
            PatchIntent.OpenScanner -> orchestrator.dispatch(PatchEvent.ScanRequested)

            is PatchIntent.CloseScanner -> orchestrator.dispatch(
                PatchEvent.ScanDismissed(intent.reason)
            )

            is PatchIntent.CodeScanned -> orchestrator.dispatch(
                PatchEvent.CodeScanned(intent.text, intent.rawBytes)
            )
        }
    }

    override fun onCleared() {
        orchestrator.close()
    }

    companion object {
        fun factory(
            userId: String,
            deviceId: String,
            port: PatchPort,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(PatchTranslation::class.java))
                return PatchTranslation(userId, deviceId, port) as T
            }
        }
    }
}

private fun PatchState.toUi(): PatchUiState = PatchUiState(
    shouldScan = autoScan,
    scanning = phase.scanning,
    bindings = bindings,
    message = message,
)
