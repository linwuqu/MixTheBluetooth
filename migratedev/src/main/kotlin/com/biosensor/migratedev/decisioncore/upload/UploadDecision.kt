package com.biosensor.migratedev.decisioncore.upload

import com.biosensor.migratedev.decisioncore.DecisionCore
import com.biosensor.migratedev.decisioncore.Transition

/**
 * 上传状态机:推文件 → 查任务 → 拉结果。
 *
 * 只做两件事:数轮询次数、判该继续还是收工。等待(端口先等再查)与 HTTP 都在端口,
 * 这里不 sleep 也不发请求——与读状态机同一条规矩。
 *
 * 重试策略沿用旧 app:推文件失败不重试(用户再点一次),查任务失败与"还没跑完"共用同一份次数预算。
 */
object UploadDecisionCore : DecisionCore<UploadState, UploadEvent, UploadEffect> {
    /** 查任务的最大次数。端口每次调用前先等 1500ms,即最长等 4.5 秒。 */
    const val MAX_POLL_ATTEMPTS = 3

    override fun reduce(
        currentState: UploadState, event: UploadEvent
    ): Transition<UploadState, UploadEffect> = when (event) {
        is UploadEvent.UploadRequested -> when (currentState) {
            // 终态与 Idle 都可开新一轮;运行中忽略(正常路径由 Translation 先拒绝)
            UploadState.Idle, is UploadState.Done, is UploadState.Failed -> Transition(
                UploadState.Uploading(event.filePath),
                listOf(UploadEffect.UploadDataset(event.filePath)))

            else -> Transition(currentState)
        }

        is UploadEvent.DatasetAccepted -> when (currentState) {
            is UploadState.Uploading -> Transition(
                UploadState.Polling(
                    currentState.filePath, event.jobId, event.datasetId, attempt = 1),
                listOf(UploadEffect.PollJob(event.jobId)))

            else -> Transition(currentState)
        }

        is UploadEvent.DatasetRejected -> when (currentState) {
            is UploadState.Uploading -> Transition(UploadState.Failed(event.reason))
            else -> Transition(currentState)
        }

        is UploadEvent.JobPending -> when (currentState) {
            is UploadState.Polling -> currentState.retryOrFail(
                "任务未在 $MAX_POLL_ATTEMPTS 次轮询内完成(status=${event.status})",
                listOf(UploadEffect.PollJob(currentState.jobId)))
            else -> Transition(currentState)
        }

        is UploadEvent.JobSucceeded -> when (currentState) {
            is UploadState.Polling -> Transition(
                UploadState.Fetching(
                    currentState.filePath, currentState.jobId,
                    currentState.datasetId, event.resultId),
                listOf(
                    UploadEffect.FetchResult(
                        currentState.jobId, currentState.datasetId, event.resultId)))

            else -> Transition(currentState)
        }

        is UploadEvent.JobFailed -> when (currentState) {
            is UploadState.Polling -> Transition(UploadState.Failed(event.reason))
            else -> Transition(currentState)
        }

        is UploadEvent.ResultFetched -> when (currentState) {
            is UploadState.Fetching -> Transition(UploadState.Done(event.result))
            else -> Transition(currentState)
        }

        // 推文件失败:不重试(重传 = 用户再点一次,本地文件不动)
        // 查任务失败:算进同一份次数预算,还能再查就再查
        // 拉结果失败:端口已保证"两条都失败才算失败",到这里就是真的拉不回来
        is UploadEvent.TransferFailed -> when (currentState) {
            is UploadState.Uploading -> Transition(UploadState.Failed(event.reason))
            is UploadState.Polling -> currentState.retryOrFail(
                event.reason, listOf(UploadEffect.PollJob(currentState.jobId)))
            is UploadState.Fetching -> Transition(UploadState.Failed(event.reason))
            else -> Transition(currentState)
        }

        UploadEvent.FileMissing -> when (currentState) {
            is UploadState.Uploading ->
                Transition(UploadState.Failed("尚无本地缓存文件,请先读缓存"))

            else -> Transition(currentState)
        }
    }

    /** 还能再查一次就再排一次,次数用完就收工。 */
    private fun UploadState.Polling.retryOrFail(
        reason: String, effects: List<UploadEffect>
    ): Transition<UploadState, UploadEffect> =
        if (attempt >= MAX_POLL_ATTEMPTS) {
            Transition(UploadState.Failed(reason))
        } else {
            Transition(copy(attempt = attempt + 1), effects)
        }
}
