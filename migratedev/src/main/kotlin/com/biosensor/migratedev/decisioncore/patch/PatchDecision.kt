package com.biosensor.migratedev.decisioncore.patch

import com.biosensor.migratedev.decisioncore.DecisionCore
import com.biosensor.migratedev.decisioncore.Transition

/**
 * 贴片绑定状态机:读列表 → 扫一枚 → 写一行 → 重读列表。
 *
 * 与读状态机同一条规矩:不 sleep、不碰相机、不碰数据库,只判该不该继续。
 *
 * 两条容易踩的规则:
 * - **解析不过不关页**:扫错码是最常见的事,停在扫码页换个码接着扫,不把用户赶回 CGM 页;
 * - **写完库重读而不是本地拼接**:端口是唯一事实源,列表顺序(最近绑的排前)由 SQL 定,
 *   本地拼一条会让"到底落库没有"变得说不清。
 */
object PatchDecisionCore : DecisionCore<PatchState, PatchEvent, PatchEffect> {

    override fun reduce(
        currentState: PatchState, event: PatchEvent
    ): Transition<PatchState, PatchEffect> = when (event) {
        is PatchEvent.SessionRequested -> Transition(
            PatchState(userId = event.userId, deviceId = event.deviceId),
            listOf(PatchEffect.LoadBindings(event.userId, event.deviceId))
        )

        // 一条都没有 → 这台设备还没绑过,进页面直接把扫码页打开(架构 13 §D5)。
        // 已经在扫了就不再设:否则用户关掉扫码页会被立刻弹回来(列表还在飞的时候点过按钮)
        is PatchEvent.BindingsLoaded -> Transition(
            currentState.copy(
                bindings = event.bindings,
                loaded = true,
                autoScan = event.bindings.isEmpty() && currentState.phase == PatchPhase.IDLE,
            )
        )

        // 读不到不等于没绑过:只记原因,不自动开扫码页(否则会骗用户去绑一枚已经绑过的)
        is PatchEvent.LoadFailed -> Transition(
            currentState.copy(loaded = true, message = event.reason)
        )

        PatchEvent.ScanRequested -> when (currentState.phase) {
            PatchPhase.IDLE -> Transition(
                currentState.copy(phase = PatchPhase.SCANNING, autoScan = false, message = null)
            )
            // 已经在扫:忽略(自动开与点按钮可能撞在一起)
            else -> Transition(currentState)
        }

        is PatchEvent.ScanDismissed -> when (currentState.phase) {
            // 写库中不响应返回:写一下就完,让页面自己关,免得"页关了但绑定成了"
            PatchPhase.BINDING -> Transition(currentState)
            // 关页时把原因带上(权限被拒这类);下次开扫码页时再清掉
            else -> Transition(
                currentState.copy(phase = PatchPhase.IDLE, message = event.reason)
            )
        }

        is PatchEvent.CodeScanned -> when (currentState.phase) {
            PatchPhase.SCANNING ->
                when (val parsed = parsePatchCode(
                    patchCodeTextFrom(event.text, event.rawBytes)
                )) {
                    is PatchCodeResult.Ok -> Transition(
                        currentState.copy(phase = PatchPhase.BINDING, message = null),
                        listOf(
                            PatchEffect.Bind(
                                currentState.userId, currentState.deviceId, parsed.code)
                        )
                    )

                    is PatchCodeResult.Rejected ->
                        Transition(currentState.copy(message = parsed.reason))
                }

            // 迟到的回调:页面已关(退出子页后相机的最后几帧),或写库中
            else -> Transition(currentState)
        }

        PatchEvent.Bound -> Transition(
            currentState.copy(phase = PatchPhase.IDLE, message = null),
            listOf(PatchEffect.LoadBindings(currentState.userId, currentState.deviceId))
        )

        // 写库失败留在扫码页:再扫一次还是这条路
        is PatchEvent.BindFailed -> Transition(
            currentState.copy(phase = PatchPhase.SCANNING, message = event.reason)
        )
    }
}
