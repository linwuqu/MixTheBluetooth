package com.biosensor.migratedev.port.cgm

import com.biosensor.migratedev.decisioncore.cgm.CgmReadEffect
import com.biosensor.migratedev.decisioncore.cgm.CgmReadEvent
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEffect
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothConnectionState

/**
 * cgm 业务端口:读流程与短命令两条契约,各自执行领域副作用、翻译领域事件。
 * 协议编码(三条指令文本)、文件拼装(边界符号)在适配器内完成;业务解析在 CgmParsePipeline。
 */
interface CgmPort {
    fun execute(effect: CgmReadEffect): Flow<CgmReadEvent>

    fun execute(effect: CgmShortEffect): Flow<CgmShortEvent>

    fun observeConnectionState(): Flow<BluetoothConnectionState> =
        flowOf(BluetoothConnectionState.CONNECTED)
}

/**
 * 缓存文件在 RECEIVED 分区里的相对路径。
 * 一个设备一个文件(设备号含冒号,不能直接做文件名),读流程写它、上传读它,两处必须一致。
 */
fun cgmCachePath(deviceId: String): String = "cgm/${deviceId.replace(':', '_')}.txt"
