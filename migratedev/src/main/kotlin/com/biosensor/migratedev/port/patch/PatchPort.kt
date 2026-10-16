package com.biosensor.migratedev.port.patch

import com.biosensor.migratedev.decisioncore.patch.PatchEffect
import com.biosensor.migratedev.decisioncore.patch.PatchEvent
import kotlinx.coroutines.flow.Flow

/**
 * 贴片业务端口:只跟数据库打交道——写一条绑定、读一台设备的绑定列表。
 *
 * 一次 effect 一次往返,不重试、不自行循环。相机不在这里:扫码是界面的事,
 * 端口收的是已经解析好的 [com.biosensor.migratedev.decisioncore.patch.PatchCode]。
 */
interface PatchPort {
    fun execute(effect: PatchEffect): Flow<PatchEvent>
}
