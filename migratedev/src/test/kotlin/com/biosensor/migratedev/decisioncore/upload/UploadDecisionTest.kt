package com.biosensor.migratedev.decisioncore.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadDecisionTest {

    private val path = "cgm/AA_01.txt"
    private val jobId = 42L
    private val datasetId = 7L
    private val resultId = 99L

    private fun reduce(state: UploadState, event: UploadEvent) =
        UploadDecisionCore.reduce(state, event)

    private fun uploading() = UploadState.Uploading(path)

    private fun polling(attempt: Int) =
        UploadState.Polling(path, jobId, datasetId, attempt)

    private fun fetching() = UploadState.Fetching(path, jobId, datasetId, resultId)

    // ── 发起上传:Idle/终态可发起,运行中忽略 ──

    @Test
    fun `upload from idle pushes the file`() {
        val result = reduce(UploadState.Idle, UploadEvent.UploadRequested(path))
        assertEquals(uploading(), result.newState)
        assertEquals(listOf(UploadEffect.UploadDataset(path)), result.effects)
    }

    @Test
    fun `upload from a terminal state restarts`() {
        for (terminal in listOf<UploadState>(
            UploadState.Done(result(unitCount = 1)),
            UploadState.Failed("网络不通")
        )) {
            val result = reduce(terminal, UploadEvent.UploadRequested(path))
            assertEquals(uploading(), result.newState)
        }
    }

    @Test
    fun `upload while running is ignored`() {
        for (running in listOf(uploading(), polling(1), fetching())) {
            val result = reduce(running, UploadEvent.UploadRequested(path))
            assertEquals(running, result.newState)
            assertEquals(emptyList<UploadEffect>(), result.effects)
        }
    }

    // ── 正常路径:推文件 → 查任务 → 拉结果 → Done ──

    @Test
    fun `accepted dataset starts polling from attempt one`() {
        val result = reduce(uploading(), UploadEvent.DatasetAccepted(jobId, datasetId))
        assertEquals(polling(1), result.newState)
        assertEquals(listOf(UploadEffect.PollJob(jobId)), result.effects)
    }

    @Test
    fun `pending job polls again until the budget runs out`() {
        // 第 1、2 次未完成 → 还能再查;第 3 次用完预算 → 收工
        var state: UploadState = polling(1)
        var polls = 1
        while (true) {
            val result = reduce(state, UploadEvent.JobPending("RUNNING"))
            val stopped = result.newState
            if (stopped is UploadState.Failed) {
                assertEquals(polling(UploadDecisionCore.MAX_POLL_ATTEMPTS), state)
                assertTrue(
                    "失败原因应说明查了几次,实际 ${stopped.reason}",
                    stopped.reason.contains("3 次")
                )
                assertEquals(emptyList<UploadEffect>(), result.effects)
                break
            }
            assertEquals(listOf<UploadEffect>(UploadEffect.PollJob(jobId)), result.effects)
            polls += 1
            state = result.newState
        }
        // 三次查询:第一次是 DatasetAccepted 排的,这里再排两次
        assertEquals(UploadDecisionCore.MAX_POLL_ATTEMPTS, polls)
    }

    @Test
    fun `succeeded job fetches the result`() {
        val result = reduce(polling(2), UploadEvent.JobSucceeded(resultId))
        assertEquals(fetching(), result.newState)
        assertEquals(
            listOf(UploadEffect.FetchResult(jobId, datasetId, resultId)), result.effects
        )
    }

    @Test
    fun `fetched result completes the session`() {
        val value = result(unitCount = 2)
        val result = reduce(fetching(), UploadEvent.ResultFetched(value))
        assertEquals(UploadState.Done(value), result.newState)
        assertEquals(emptyList<UploadEffect>(), result.effects)
    }

    // ── 失败路径 ──

    @Test
    fun `rejected dataset fails without retry`() {
        val result = reduce(uploading(), UploadEvent.DatasetRejected("datasetStatus=INVALID"))
        assertEquals(UploadState.Failed("datasetStatus=INVALID"), result.newState)
        assertEquals(emptyList<UploadEffect>(), result.effects)
    }

    @Test
    fun `upload transfer failure does not retry`() {
        val result = reduce(uploading(), UploadEvent.TransferFailed("上传超时"))
        assertEquals(UploadState.Failed("上传超时"), result.newState)
        assertEquals(emptyList<UploadEffect>(), result.effects)
    }

    @Test
    fun `poll transfer failure reuses the same attempt budget`() {
        // 传输失败与"还没跑完"共用次数:前两次再查,第三次收工
        val again = reduce(polling(1), UploadEvent.TransferFailed("查询超时"))
        assertEquals(polling(2), again.newState)
        assertEquals(listOf<UploadEffect>(UploadEffect.PollJob(jobId)), again.effects)

        val last = reduce(
            polling(UploadDecisionCore.MAX_POLL_ATTEMPTS), UploadEvent.TransferFailed("查询超时")
        )
        assertEquals(UploadState.Failed("查询超时"), last.newState)
        assertEquals(emptyList<UploadEffect>(), last.effects)
    }

    @Test
    fun `job failed stops immediately`() {
        val result = reduce(polling(1), UploadEvent.JobFailed("后端解析失败"))
        assertEquals(UploadState.Failed("后端解析失败"), result.newState)
        assertEquals(emptyList<UploadEffect>(), result.effects)
    }

    @Test
    fun `fetch transfer failure stops`() {
        val result = reduce(fetching(), UploadEvent.TransferFailed("结果拉取失败"))
        assertEquals(UploadState.Failed("结果拉取失败"), result.newState)
    }

    @Test
    fun `missing local file fails before any request`() {
        val result = reduce(uploading(), UploadEvent.FileMissing)
        assertEquals(UploadState.Failed("尚无本地缓存文件,请先读缓存"), result.newState)
        assertEquals(emptyList<UploadEffect>(), result.effects)
    }

    // ── 迟到事件:不在对应阶段一律忽略 ──

    @Test
    fun `late events outside their phase are ignored`() {
        val stray = listOf<Pair<UploadState, UploadEvent>>(
            UploadState.Idle to UploadEvent.DatasetAccepted(jobId, datasetId),
            UploadState.Idle to UploadEvent.JobPending("RUNNING"),
            UploadState.Idle to UploadEvent.JobSucceeded(resultId),
            UploadState.Idle to UploadEvent.ResultFetched(result(1)),
            UploadState.Idle to UploadEvent.FileMissing,
            uploading() to UploadEvent.JobPending("RUNNING"),
            uploading() to UploadEvent.ResultFetched(result(1)),
            polling(1) to UploadEvent.DatasetAccepted(jobId, datasetId),
            polling(1) to UploadEvent.ResultFetched(result(1)),
            fetching() to UploadEvent.JobPending("RUNNING"),
            UploadState.Done(result(1)) to UploadEvent.JobPending("RUNNING"),
            UploadState.Failed("x") to UploadEvent.JobSucceeded(resultId)
        )
        for ((state, event) in stray) {
            val result = reduce(state, event)
            assertEquals("$state + $event 应忽略", state, result.newState)
            assertEquals(emptyList<UploadEffect>(), result.effects)
        }
    }

    // ── 转移表完备性 ──

    @Test
    fun `every state event pair reduces without exception`() {
        val states = listOf<UploadState>(
            UploadState.Idle, uploading(), polling(1),
            polling(UploadDecisionCore.MAX_POLL_ATTEMPTS), fetching(),
            UploadState.Done(result(1)), UploadState.Failed("x")
        )
        val events = listOf<UploadEvent>(
            UploadEvent.UploadRequested(path),
            UploadEvent.DatasetAccepted(jobId, datasetId),
            UploadEvent.DatasetRejected("x"),
            UploadEvent.JobPending("RUNNING"),
            UploadEvent.JobSucceeded(resultId),
            UploadEvent.JobFailed("x"),
            UploadEvent.ResultFetched(result(1)),
            UploadEvent.TransferFailed("x"),
            UploadEvent.FileMissing
        )
        for (state in states) {
            for (event in events) {
                // 任何 (状态, 事件) 都必须是合法转移(可能幂等,但不得抛异常)
                UploadDecisionCore.reduce(state, event)
            }
        }
    }

    private fun result(unitCount: Int) = CgmResult(
        resultId = resultId, jobId = jobId, datasetId = datasetId,
        unitCount = unitCount, pointCount = unitCount * 2,
        stats = PredictionStats(3.0, 9.0, 6.0, 1.0),
        units = List(unitCount) { index ->
            ResultUnit(
                unit = index, title = "Unit $index", pointCount = 2, mard = 0.1,
                points = listOf(
                    ResultPoint(index = 1, time = 60, rawTime = "2026-07-21 12:10:45", predicted = 5.5),
                    ResultPoint(index = 2, time = 70, rawTime = "2026-07-21 12:10:55", predicted = 6.5)
                )
            )
        }
    )
}
