package com.biosensor.migratedev.port.upload

import com.biosensor.migratedev.decisioncore.upload.UploadEffect
import com.biosensor.migratedev.decisioncore.upload.UploadEvent
import kotlinx.coroutines.flow.Flow

/**
 * 上传业务端口:把本地缓存文件推给后端、查任务、把结果取回来。
 *
 * 一次 effect 一次往返,不重试、不自行循环——查几次、要不要再查,是状态机的事。
 * 唯一留在端口里的时间是轮询间隔:那是传输节奏,不是决策。
 */
interface UploadPort {
    fun execute(effect: UploadEffect): Flow<UploadEvent>
}
