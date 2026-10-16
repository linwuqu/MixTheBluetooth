package com.biosensor.migratedev.decisioncore.upload

// ── 上传会话:推文件 → 查任务 → 拉结果 ──

/** 上传推进到哪一步。 */
enum class UploadPhase {
    IDLE, UPLOADING, POLLING, FETCHING, DONE, FAILED;

    /** 会话在手上:此时拒绝重入。DONE / FAILED 是终态,可以重来。 */
    val running: Boolean
        get() = this == UPLOADING || this == POLLING || this == FETCHING
}

/**
 * 上传会话的全部状态。
 * 状态机不做 IO,也不 sleep——等待与 HTTP 都在端口;这里只数次数、判该不该继续。
 */
sealed interface UploadState {
    val phase: UploadPhase

    data object Idle : UploadState {
        override val phase = UploadPhase.IDLE
    }

    /** 文件在途。 */
    data class Uploading(val filePath: String) : UploadState {
        override val phase = UploadPhase.UPLOADING
    }

    /** 任务在跑,已查 attempt 次。 */
    data class Polling(
        val filePath: String,
        val jobId: Long,
        val datasetId: Long,
        val attempt: Int,
    ) : UploadState {
        override val phase = UploadPhase.POLLING
    }

    /** 任务成功,结果在拉。 */
    data class Fetching(
        val filePath: String,
        val jobId: Long,
        val datasetId: Long,
        val resultId: Long,
    ) : UploadState {
        override val phase = UploadPhase.FETCHING
    }

    data class Done(val result: CgmResult) : UploadState {
        override val phase = UploadPhase.DONE
    }

    /** 终态。原因留给界面,重来 = 用户再点一次上传(本地文件不动)。 */
    data class Failed(val reason: String) : UploadState {
        override val phase = UploadPhase.FAILED
    }
}

sealed interface UploadEvent {
    /** UI 触发:把这个本地文件推给后端。 */
    data class UploadRequested(val filePath: String) : UploadEvent

    /** 文件已收下,拿到任务号(端口判据:HTTP 成功 + 业务码 ok + VALID + jobId 非空)。 */
    data class DatasetAccepted(val jobId: Long, val datasetId: Long) : UploadEvent

    /** 后端拒收(业务码不 ok、datasetStatus 非 VALID、或 data 为空)。 */
    data class DatasetRejected(val reason: String) : UploadEvent

    /** 任务还没跑完。 */
    data class JobPending(val status: String) : UploadEvent

    /** 任务完成,结果已生成。 */
    data class JobSucceeded(val resultId: Long) : UploadEvent

    /** 任务失败(后端明确回报 FAILED)。 */
    data class JobFailed(val reason: String) : UploadEvent

    /** 结果已拉回并合并。 */
    data class ResultFetched(val result: CgmResult) : UploadEvent

    /** 传输层失败(超时 / 不可达 / 非 200)。 */
    data class TransferFailed(val reason: String) : UploadEvent

    /** 本地还没有这个文件:端口一个请求都不发。 */
    data object FileMissing : UploadEvent
}

sealed interface UploadEffect {
    data class UploadDataset(val filePath: String) : UploadEffect
    data class PollJob(val jobId: Long) : UploadEffect
    data class FetchResult(val jobId: Long, val datasetId: Long, val resultId: Long) : UploadEffect
}

// ── 后端结果:后端给什么就留什么 ──
//
// 旧 app 的 CgmResult 同时存了扁平字段(predictionMin/Max/Mean/Std)和 summaryJson,
// 两者内容重复;这里只留结构化的 units,统计值由展示层从曲线点算。

data class CgmResult(
    val resultId: Long,
    val jobId: Long,
    val datasetId: Long,
    val unitCount: Int,
    val pointCount: Int,
    /** info 接口拿到;拉失败时为 null。 */
    val stats: PredictionStats? = null,
    /** curve 接口拿到;一条单位一条曲线。 */
    val units: List<ResultUnit> = emptyList(),
)

data class PredictionStats(val min: Double, val max: Double, val mean: Double, val std: Double)

data class ResultUnit(
    val unit: Int,
    val title: String?,
    val pointCount: Int,
    val mard: Double,
    val points: List<ResultPoint>,
)

data class ResultPoint(
    val index: Int,
    val time: Int,
    /** 后端给的时间字符串,形如 2026-07-21 12:10:45。 */
    val rawTime: String?,
    val predicted: Double,
    /** 实测值;后端可能不给。 */
    val actual: Double? = null,
)
