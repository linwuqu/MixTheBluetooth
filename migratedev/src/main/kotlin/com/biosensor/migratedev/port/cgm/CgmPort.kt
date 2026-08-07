package com.biosensor.migratedev.port.cgm

import com.biosensor.migratedev.decisioncore.cgm.CgmReadEffect
import com.biosensor.migratedev.decisioncore.cgm.CgmReadEvent
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEffect
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEvent
import kotlinx.coroutines.flow.Flow

/**
 * cgm 业务端口:读流程与短命令两条契约,各自执行领域副作用、翻译领域事件。
 * 协议编码(三条指令文本)、文件拼装(边界符号)在适配器内完成;业务解析在 CgmParsePipeline。
 */
interface CgmPort {
    fun execute(effect: CgmReadEffect): Flow<CgmReadEvent>

    fun execute(effect: CgmShortEffect): Flow<CgmShortEvent>
}
