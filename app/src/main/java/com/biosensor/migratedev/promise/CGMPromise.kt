package com.biosensor.migratedev.promise

import kotlinx.coroutines.flow.StateFlow

interface CGMPromise {
    val connection: CgmConnection
    val sync: CgmSync
    val jobs: CgmJobs
}

interface CgmConnection {
    val state: StateFlow<CgmConnectionState>

    suspend fun scanIdentity(): CgmDeviceIdentity
    suspend fun connect(identity: CgmDeviceIdentity, bluetoothDevice: BluetoothDevice): CgmConnectionOutput
    suspend fun disconnect(): CgmConnectionOutput
}

interface CgmSync {
    val state: StateFlow<CgmSyncState>

    suspend fun readCache(): CgmWorkflowOutput
    suspend fun verifyCache(text: String): CgmWorkflowOutput
    suspend fun deleteCache(): CgmWorkflowOutput
    // reset 表示放弃当前同步会话，清空缓存拼接/重试计数/等待状态，并回到 Idle。
    suspend fun reset(): CgmWorkflowOutput
}

interface CgmJobs {
    suspend fun uploadCache(file: CgmCacheFile): CgmJobId
    suspend fun pollResult(jobId: CgmJobId): CgmJobResult
}

data class CgmConnectionOutput(
    val state: CgmConnectionState,
    val effects: List<CgmConnectionEffect> = emptyList()
)

sealed interface CgmConnectionState {
    data object Idle : CgmConnectionState
    data object ScanningIdentity : CgmConnectionState
    data class IdentityReady(val identity: CgmDeviceIdentity) : CgmConnectionState
    data class Connecting(val identity: CgmDeviceIdentity, val bluetoothDevice: BluetoothDevice) : CgmConnectionState
    data class Connected(val device: CgmConnectedDevice) : CgmConnectionState
    data class Failed(val reason: String) : CgmConnectionState
}

sealed interface CgmConnectionEffect {
    data class ShowMessage(val message: String) : CgmConnectionEffect
    data class RequestBluetoothConnect(
        val identity: CgmDeviceIdentity,
        val bluetoothDevice: BluetoothDevice
    ) : CgmConnectionEffect
}

data class CgmWorkflowOutput(
    val state: CgmSyncState,
    val effects: List<CgmWorkflowEffect> = emptyList()
)

sealed interface CgmSyncState {
    data object Idle : CgmSyncState
    data object ReadingCache : CgmSyncState
    data class ReceivingCache(val lineCount: Int) : CgmSyncState
    data object ValidatingCache : CgmSyncState
    data class RetryingRead(val attempt: Int, val reason: String) : CgmSyncState
    data class CacheReady(val file: CgmCacheFile) : CgmSyncState
    data object Uploading : CgmSyncState
    data class PollingJob(val jobId: Long, val attempt: Int) : CgmSyncState
    data class ResultReady(val result: CgmJobResult) : CgmSyncState
    data object WaitingDeleteConfirm : CgmSyncState
    data object Done : CgmSyncState
    data class Failed(val reason: String) : CgmSyncState
}

sealed interface CgmWorkflowEffect {
    data class SendCommand(val command: CgmDeviceCommand) : CgmWorkflowEffect
    data class ShowMessage(val message: String) : CgmWorkflowEffect
    data class PublishResult(val result: CgmJobResult) : CgmWorkflowEffect
    data class RequestDeleteConfirm(val command: CgmDeviceCommand) : CgmWorkflowEffect
}

enum class CgmDeviceCommandPurpose {
    READ_CACHE,
    READ_CACHE_RETRY,
    DELETE_CACHE,
    SYNC_TIME
}

data class CgmDeviceCommand(
    // purpose 区分业务意图，text 是真正发给设备的命令内容。
    val purpose: CgmDeviceCommandPurpose,
    val text: String
)

data class CgmDeviceIdentity(
    val rawText: String,
    val patchNo: String? = null,
    val deviceName: String? = null,
    // 设备可能重命名，连接匹配优先使用 mac。
    val deviceMac: String? = null
)

data class CgmConnectedDevice(
    val identity: CgmDeviceIdentity,
    val bluetoothDevice: BluetoothDevice,
    val connectedAtMillis: Long
)

data class CgmCacheFile(
    val file: StoredFile,
    val lineCount: Int
)

data class CgmJobId(
    val value: Long
)

// 一个 job 可以产出多个 artifact；primary 是当前主展示结果。
data class CgmJobResult(
    val jobId: Long,
    val status: String,
    val primary: CgmResultArtifact? = null,
    val artifacts: List<CgmResultArtifact> = emptyList()
)

sealed interface CgmResultArtifact {
    val resultId: Long?
    val title: String

    data class GlucosePrediction(
        override val resultId: Long?,
        override val title: String,
        val pointCount: Int,
        val unitCount: Int,
        val units: List<CgmResultUnit>,
        val stats: CgmPredictionStats? = null
    ) : CgmResultArtifact

    data class FileReport(
        override val resultId: Long?,
        override val title: String,
        val file: StoredFile
    ) : CgmResultArtifact

    data class TextReport(
        override val resultId: Long?,
        override val title: String,
        val text: String
    ) : CgmResultArtifact
}

data class CgmPredictionStats(
    val min: Double? = null,
    val max: Double? = null,
    val mean: Double? = null,
    val std: Double? = null
)

data class CgmResultUnit(
    val unit: Int,
    val title: String?,
    val pointCount: Int,
    val points: List<CgmResultPoint>
)

data class CgmResultPoint(
    val index: Int,
    val time: Int,
    val rawTime: String?,
    val predicted: Double,
    val actual: Double? = null
)
