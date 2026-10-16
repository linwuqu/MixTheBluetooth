package com.biosensor.migratedev.decisioncore.patch

/** 短号长度:界面上靠它分辨是哪一枚贴片。 */
private const val SHORT_ID_LENGTH = 8

/**
 * 一条贴片绑定:某个账号在某台设备上绑过某枚贴片。
 *
 * 字段与库表 `patchBinding` 一一对应,但**不引用生成的库类型**——决策核不认数据库,
 * 映射在端口里做(与 [com.biosensor.migratedev.port.connection.BindingSnapshot] 同一规矩)。
 */
data class PatchBinding(
    val patchId: String,
    val modelId: Long?,
    val lotNo: String?,
    val manufacturedOn: String?,
    val expiresOn: String?,
    val maxUseMinutes: Long?,
    val calibrationRef: String?,
    /** 扫码原文:不认识的扩展字段靠它不丢。 */
    val rawCode: String,
    val boundAtMillis: Long,
) {
    /** 编号前 8 位。样例三枚 A/B/C 的前 8 位互不相同,一眼能分辨。 */
    val shortId: String get() = patchId.take(SHORT_ID_LENGTH)
}

enum class PatchPhase {
    /** 扫码子页关着。 */
    IDLE,

    /** 子页开着,等一个能解析的码。 */
    SCANNING,

    /** 码解析过了,在写库。写库期间不收新码。 */
    BINDING;

    /** 子页是否占着屏幕——写库时也还开着,所以不只是 SCANNING。 */
    val scanning: Boolean get() = this != IDLE
}

/**
 * 贴片绑定会话的全部状态。
 *
 * 状态机不做 IO:相机在界面,数据库在端口,这里只记「扫到没、解析过没、落库成没」。
 */
data class PatchState(
    val phase: PatchPhase = PatchPhase.IDLE,
    val userId: String = "",
    val deviceId: String = "",
    /** 这台设备当前绑定的贴片,最近绑的排前。 */
    val bindings: List<PatchBinding> = emptyList(),
    /** 已经读过一次库。没读过时不下「还没绑过」的结论。 */
    val loaded: Boolean = false,
    /** 库里一条绑定都没有 → 进页面自动开扫码页。开过一次就清掉,不反复弹。 */
    val autoScan: Boolean = false,
    /** 出问题的原因:解析不过 / 写库失败 / 读绑定失败。没有就是 null。 */
    val message: String? = null,
)

sealed interface PatchEvent {
    /** 会话建立:读这台设备已有的绑定(翻译层 init 时发)。 */
    data class SessionRequested(val userId: String, val deviceId: String) : PatchEvent

    data class BindingsLoaded(val bindings: List<PatchBinding>) : PatchEvent

    data class LoadFailed(val reason: String) : PatchEvent

    /** UI:开扫码子页(库里没有绑定自动开 / 点标题行按钮)。 */
    data object ScanRequested : PatchEvent

    /**
     * UI:关扫码子页(返回键 / 跳过)。[reason] 非空时留在 CGM 页上说明为什么没扫成
     * (如相机权限被拒);用户主动跳过就是 null。
     */
    data class ScanDismissed(val reason: String? = null) : PatchEvent

    /** 相机回调:扫到一段内容。文本为空时用原始字节兜底(载荷将来会是二进制)。 */
    data class CodeScanned(val text: String?, val rawBytes: ByteArray?) : PatchEvent

    data object Bound : PatchEvent

    data class BindFailed(val reason: String) : PatchEvent
}

sealed interface PatchEffect {
    /** 读这台设备当前绑定的贴片,最近绑的排前。 */
    data class LoadBindings(val userId: String, val deviceId: String) : PatchEffect

    /** 落一条绑定;同 `(userId, deviceId, patchId)` 只刷新时间,不增行。 */
    data class Bind(val userId: String, val deviceId: String, val code: PatchCode) : PatchEffect
}
