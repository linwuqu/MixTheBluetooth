package com.biosensor.migratedev.translation.cgm

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.biosensor.migratedev.decisioncore.cgm.CacheValidators
import com.biosensor.migratedev.decisioncore.cgm.CgmCommandPurpose
import com.biosensor.migratedev.decisioncore.cgm.CgmReadEffect
import com.biosensor.migratedev.decisioncore.cgm.CgmReadEvent
import com.biosensor.migratedev.decisioncore.cgm.CgmReadPhase
import com.biosensor.migratedev.decisioncore.cgm.CgmRecord
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEffect
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEvent
import com.biosensor.migratedev.decisioncore.cgm.MarkerKind
import com.biosensor.migratedev.decisioncore.cgm.StopReason
import com.biosensor.migratedev.decisioncore.upload.CgmResult
import com.biosensor.migratedev.decisioncore.upload.PredictionStats
import com.biosensor.migratedev.decisioncore.upload.ResultPoint
import com.biosensor.migratedev.decisioncore.upload.ResultUnit
import com.biosensor.migratedev.decisioncore.upload.UploadEffect
import com.biosensor.migratedev.decisioncore.upload.UploadEvent
import com.biosensor.migratedev.decisioncore.upload.UploadPhase
import com.biosensor.migratedev.port.cgm.CgmPort
import com.biosensor.migratedev.port.upload.UploadPort
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CgmTranslationTest {

    private val deviceId = "AA:01"

    private class FakeCgmPort : CgmPort {
        var readResults: (CgmReadEffect) -> Flow<CgmReadEvent> = { flowOf() }
        var shortResults: (CgmShortEffect) -> Flow<CgmShortEvent> = { flowOf() }
        val readEffects = mutableListOf<CgmReadEffect>()
        val connection = kotlinx.coroutines.flow.MutableStateFlow(
            BluetoothConnectionState.CONNECTED
        )

        override fun execute(effect: CgmReadEffect): Flow<CgmReadEvent> {
            readEffects += effect
            return readResults(effect)
        }

        override fun execute(effect: CgmShortEffect): Flow<CgmShortEvent> = shortResults(effect)

        override fun observeConnectionState(): Flow<BluetoothConnectionState> = connection
    }

    /** 完整单段缓存:EIS 段 95 点,收集校验必过。 */
    private fun fullCache(): List<CgmRecord> = buildList {
        add(CgmRecord.Marker(MarkerKind.START, "Start Playback"))
        add(CgmRecord.Log("LOG:1,1,95,2026-07-21 12:10:45"))
        for (i in 1..95) {
            add(CgmRecord.Eis(i, 60 + (i - 1) * 10, "a", "b", "c", "d",
                "EIS:$i,${60 + (i - 1) * 10},a,b,c,d"))
        }
        add(CgmRecord.Marker(MarkerKind.END, "Playback all done"))
    }

    /** 声明 95 点只到 1 点:收集校验必不过,触发重读。 */
    private fun incompleteCache(): List<CgmRecord> = listOf(
        CgmRecord.Marker(MarkerKind.START, "Start Playback"),
        CgmRecord.Log("LOG:1,1,95,2026-07-21 12:10:45"),
        CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d"),
        CgmRecord.Marker(MarkerKind.END, "Playback all done")
    )

    /** 端口收到 END 时做收集校验,结论随这批记录送回——fake 照做。 */
    private fun delivered(sessionId: String, records: List<CgmRecord>) = flowOf(
        CgmReadEvent.CommandAccepted(sessionId),
        CgmReadEvent.RecordsProduced(sessionId, records, CacheValidators.validate(records))
    )

    /** 读全流程 fake:StartRead/RetryRead 回完整缓存(带结论),写盘成功 → 收工。 */
    private fun FakeCgmPort.completeReadFlow() {
        readResults = { effect ->
            when (effect) {
                is CgmReadEffect.StartRead -> delivered(effect.session.id, fullCache())
                is CgmReadEffect.RetryRead -> delivered(effect.session.id, fullCache())
                is CgmReadEffect.WriteFile -> flowOf(
                    CgmReadEvent.FileWritten(effect.session.id, "cgm/s1.txt"))
                is CgmReadEffect.StopRuntime -> flowOf()
            }
        }
    }

    private class FakeUploadPort : UploadPort {
        var results: (UploadEffect) -> Flow<UploadEvent> = { flowOf() }
        val effects = mutableListOf<UploadEffect>()

        override fun execute(effect: UploadEffect): Flow<UploadEvent> {
            effects += effect
            return results(effect)
        }
    }

    /** 全链路 fake:推文件 → 查任务成功 → 拉结果完成。 */
    private fun FakeUploadPort.completeUploadFlow() {
        results = { effect ->
            when (effect) {
                is UploadEffect.UploadDataset -> flowOf(
                    UploadEvent.DatasetAccepted(jobId = 42L, datasetId = 7L)
                )
                is UploadEffect.PollJob -> flowOf(UploadEvent.JobSucceeded(resultId = 99L))
                is UploadEffect.FetchResult -> flowOf(UploadEvent.ResultFetched(sampleResult))
            }
        }
    }

    private fun newTranslation(
        store: ViewModelStore,
        port: FakeCgmPort,
        report: (CgmOutput) -> Unit,
        uploadPort: FakeUploadPort = FakeUploadPort()
    ): CgmTranslation = ViewModelProvider(
        store,
        CgmTranslation.factory(deviceId, port, uploadPort, report)
    )[CgmTranslation::class.java]

    @Test
    fun `read from idle completes and reports ReadCompleted once`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        val outputs = mutableListOf<CgmOutput>()
        val port = FakeCgmPort().apply { completeReadFlow() }
        try {
            val translation = newTranslation(store, port, outputs::add)

            translation.submit(CgmIntent.ReadCache)
            advanceUntilIdle()

            // 收集校验过 → 入库 → Stopped → 上报 ReadCompleted(仅一次)
            assertEquals(listOf<CgmOutput>(CgmOutput.ReadCompleted("cgm/s1.txt")), outputs)
            assertEquals(CgmReadPhase.STOPPED, translation.uiState.value.readPhase)
            assertEquals(95, translation.uiState.value.progressPoints)
            assertTrue(translation.uiState.value.dataLines.isNotEmpty())
            assertEquals(fullCache().size, translation.uiState.value.dataLines.size)
            assertEquals("Start Playback", translation.uiState.value.dataLines.first())
            assertEquals("Playback all done", translation.uiState.value.dataLines.last())
            assertTrue(
                translation.uiState.value.validationChecks.any { it.name == "缓存结构" && it.passed }
            )
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `double read tap keeps one session and reports hint`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val outputs = mutableListOf<CgmOutput>()
        val port = FakeCgmPort().apply {
            readResults = { flow { awaitCancellation() } }
        }
        try {
            val translation = newTranslation(store, port, outputs::add)

            translation.submit(CgmIntent.ReadCache)
            advanceUntilIdle()
            translation.submit(CgmIntent.ReadCache)
            advanceUntilIdle()

            assertEquals(1, port.readEffects.count { it is CgmReadEffect.StartRead })
            assertEquals(
                listOf(CgmOutput.Blocked("正在执行读取中,请等待执行完毕")),
                outputs
            )
            assertEquals("正在执行读取中,请等待执行完毕", translation.uiState.value.hint)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `force stop closes active read and preserves a usable terminal state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val port = FakeCgmPort().apply {
            readResults = { effect ->
                if (effect is CgmReadEffect.StartRead) flowOf(
                    CgmReadEvent.CommandAccepted(effect.session.id)
                ) else flowOf()
            }
        }
        try {
            val translation = newTranslation(store, port, {})
            translation.submit(CgmIntent.ReadCache)
            advanceUntilIdle()

            translation.submit(CgmIntent.ForceStopRead)
            advanceUntilIdle()

            assertEquals(CgmReadPhase.STOPPED, translation.uiState.value.readPhase)
            // 阶段标签由 stopReason 决定文案("读取已停止"),过程行为空不留重复行
            assertEquals(StopReason.USER_CANCEL, translation.uiState.value.stopReason)
            assertEquals(null, translation.uiState.value.readMessage)
            assertEquals("已强制停止本次读取", translation.uiState.value.hint)
            assertTrue(port.readEffects.any { it is CgmReadEffect.StopRuntime })
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `connection indicator covers reconnecting and disconnected states`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val outputs = mutableListOf<CgmOutput>()
        val port = FakeCgmPort()
        try {
            val translation = newTranslation(store, port, outputs::add)
            advanceUntilIdle()
            assertEquals(CgmConnectionStatus.Connected, translation.uiState.value.connectionStatus)

            port.connection.value = BluetoothConnectionState.DISCONNECTED
            advanceUntilIdle()
            assertEquals(CgmConnectionStatus.Reconnecting, translation.uiState.value.connectionStatus)
            assertEquals(listOf(CgmOutput.ConnectionLost), outputs)

            translation.submit(CgmIntent.ReadCache)
            translation.submit(CgmIntent.SyncTime)
            translation.submit(CgmIntent.DeleteCache)
            advanceUntilIdle()
            assertEquals(0, port.readEffects.count { it is CgmReadEffect.StartRead })
            assertEquals(3, outputs.count { it is CgmOutput.Blocked })

            translation.submit(CgmIntent.DeviceReconnectFailed)
            advanceUntilIdle()
            assertEquals(CgmConnectionStatus.Disconnected, translation.uiState.value.connectionStatus)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `read while short command active is blocked`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        val outputs = mutableListOf<CgmOutput>()
        val port = FakeCgmPort().apply {
            shortResults = { flow { awaitCancellation() } }   // 短命令挂起中
        }
        try {
            val translation = newTranslation(store, port, outputs::add)

            translation.submit(CgmIntent.SyncTime)
            advanceUntilIdle()
            translation.submit(CgmIntent.ReadCache)
            advanceUntilIdle()

            assertEquals(
                listOf(CgmOutput.Blocked("正在执行命令中,请等待执行完毕")), outputs)
            assertEquals(CgmReadPhase.IDLE, translation.uiState.value.readPhase)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `short command while read active is blocked`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        val outputs = mutableListOf<CgmOutput>()
        val port = FakeCgmPort().apply {
            readResults = { flow { awaitCancellation() } }   // 读挂起中
        }
        try {
            val translation = newTranslation(store, port, outputs::add)

            translation.submit(CgmIntent.ReadCache)
            advanceUntilIdle()
            translation.submit(CgmIntent.SyncTime)
            advanceUntilIdle()

            assertEquals(
                listOf(CgmOutput.Blocked("正在执行读取中,请等待执行完毕")), outputs)
            assertEquals(CgmReadPhase.SENDING, translation.uiState.value.readPhase)
            assertEquals(null, translation.uiState.value.shortResult)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `double short tap is blocked against itself`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        val outputs = mutableListOf<CgmOutput>()
        val port = FakeCgmPort().apply {
            shortResults = { flow { awaitCancellation() } }   // 短命令挂起中
        }
        try {
            val translation = newTranslation(store, port, outputs::add)

            translation.submit(CgmIntent.SyncTime)
            advanceUntilIdle()
            translation.submit(CgmIntent.SyncTime)   // 防连点:自身活跃也拒绝
            advanceUntilIdle()

            assertEquals(
                listOf(CgmOutput.Blocked("正在执行命令中,请等待执行完毕")), outputs)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `short done reports ShortDone and projects result`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        val outputs = mutableListOf<CgmOutput>()
        val port = FakeCgmPort().apply {
            shortResults = { effect ->
                when (effect) {
                    is CgmShortEffect.SendCommand -> flowOf(
                        CgmShortEvent.CommandAccepted(effect.session.id),
                        CgmShortEvent.AckReceived(effect.session.id, effect.purpose)
                    )
                }
            }
        }
        try {
            val translation = newTranslation(store, port, outputs::add)

            translation.submit(CgmIntent.SyncTime)
            advanceUntilIdle()

            assertEquals(
                listOf(CgmOutput.ShortDone(CgmCommandPurpose.SYNC_TIME)), outputs)
            assertEquals(
                CgmShortResult(CgmCommandPurpose.SYNC_TIME, ok = true, message = "对时成功"),
                translation.uiState.value.shortResult)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `error auto resets then next read runs full flow`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        val outputs = mutableListOf<CgmOutput>()
        val port = FakeCgmPort()
        var writeFileCount = 0
        port.readResults = { effect ->
            when (effect) {
                is CgmReadEffect.StartRead -> delivered(effect.session.id, fullCache())
                is CgmReadEffect.RetryRead -> delivered(effect.session.id, fullCache())
                is CgmReadEffect.WriteFile -> {
                    writeFileCount++
                    if (writeFileCount == 1) {
                        flowOf(CgmReadEvent.FileWriteFailed(effect.session.id, "磁盘满"))
                    } else {
                        flowOf(CgmReadEvent.FileWritten(effect.session.id, "cgm/s1.txt"))
                    }
                }
                is CgmReadEffect.StopRuntime -> flowOf()
            }
        }
        try {
            val translation = newTranslation(store, port, outputs::add)

            translation.submit(CgmIntent.ReadCache)
            advanceUntilIdle()
            assertEquals(CgmReadPhase.ERROR, translation.uiState.value.readPhase)

            // 第二次读:Error 自动复位(无 Blocked),完整跑完 → ReadCompleted
            translation.submit(CgmIntent.ReadCache)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    CgmOutput.Failed("磁盘满"),
                    CgmOutput.ReadCompleted("cgm/s1.txt")
                ),
                outputs)
            assertEquals(CgmReadPhase.STOPPED, translation.uiState.value.readPhase)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `read failure reports Failed after retries exhausted`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        val outputs = mutableListOf<CgmOutput>()
        val port = FakeCgmPort().apply {
            readResults = { effect ->
                when (effect) {
                    is CgmReadEffect.StartRead -> delivered(effect.session.id, incompleteCache())
                    is CgmReadEffect.RetryRead -> delivered(effect.session.id, incompleteCache())
                    else -> flowOf()
                }
            }
        }
        try {
            val translation = newTranslation(store, port, outputs::add)

            translation.submit(CgmIntent.ReadCache)
            advanceUntilIdle()

            // 4 次校验失败:每次 Failed 转移都上报;最后一次 StopRuntime(RETRY_EXHAUSTED)
            assertTrue(
                "应上报 4 次 Failed,实际 $outputs",
                outputs.size == 4 && outputs.all { it is CgmOutput.Failed }
            )
            assertEquals(CgmReadPhase.FAILED, translation.uiState.value.readPhase)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `uiState projects receiving phase and progress`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        val outputs = mutableListOf<CgmOutput>()
        val port = FakeCgmPort().apply {
            readResults = { effect ->
                when (effect) {
                    is CgmReadEffect.StartRead -> flowOf(
                        CgmReadEvent.CommandAccepted(effect.session.id),
                        CgmReadEvent.RecordsProduced(
                            effect.session.id, fullCache().take(12)   // START + LOG + 10 EIS,无 END
                        )
                    )
                    else -> flowOf()   // 本测试无重读路径
                }
            }
        }
        try {
            val translation = newTranslation(store, port, outputs::add)

            translation.submit(CgmIntent.ReadCache)
            advanceUntilIdle()

            val ui = translation.uiState.value
            assertEquals(CgmReadPhase.RECEIVING, ui.readPhase)
            // 首轮无细节可补:阶段标签已说明在接收,不为它再留一行重复文案
            assertEquals(null, ui.readMessage)
            assertEquals(10, ui.progressPoints)
            assertEquals(1, ui.roundCount)
            assertEquals(12, ui.dataLines.size)   // 全部记录：START + LOG + 10 EIS
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    // ── 上传:第三条链路,与读/短命令互斥 ──

    @Test
    fun `upload runs the whole chain and projects the result`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val outputs = mutableListOf<CgmOutput>()
        val uploadPort = FakeUploadPort().apply { completeUploadFlow() }
        try {
            val translation = newTranslation(store, FakeCgmPort(), outputs::add, uploadPort)

            translation.submit(CgmIntent.UploadDataset)
            advanceUntilIdle()

            // 一条链路三步:推文件 → 查任务 → 拉结果。
            // 路径由 cgmCachePath 统一:上传读的正是读流程写下的那一份。
            assertEquals(
                listOf(
                    UploadEffect.UploadDataset("cgm/AA_01.txt"),
                    UploadEffect.PollJob(42L),
                    UploadEffect.FetchResult(42L, 7L, 99L)
                ),
                uploadPort.effects
            )
            assertEquals(UploadPhase.DONE, translation.uiState.value.uploadPhase)
            assertEquals(sampleResult, translation.uiState.value.result)
            assertEquals(null, translation.uiState.value.uploadMessage)
            assertTrue("上传不上报 Root", outputs.isEmpty())
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `upload failure projects the reason`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val uploadPort = FakeUploadPort().apply {
            results = { flowOf(UploadEvent.TransferFailed("请求超时")) }
        }
        try {
            val translation = newTranslation(store, FakeCgmPort(), {}, uploadPort)

            translation.submit(CgmIntent.UploadDataset)
            advanceUntilIdle()

            assertEquals(UploadPhase.FAILED, translation.uiState.value.uploadPhase)
            assertEquals("请求超时", translation.uiState.value.uploadMessage)
            assertEquals(null, translation.uiState.value.result)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `upload while uploading is blocked`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val outputs = mutableListOf<CgmOutput>()
        val uploadPort = FakeUploadPort().apply {
            results = { flow { awaitCancellation() } }   // 上传挂起中
        }
        try {
            val translation = newTranslation(store, FakeCgmPort(), outputs::add, uploadPort)

            translation.submit(CgmIntent.UploadDataset)
            advanceUntilIdle()
            translation.submit(CgmIntent.UploadDataset)
            advanceUntilIdle()

            assertEquals(1, uploadPort.effects.size)
            assertEquals(listOf(CgmOutput.Blocked("正在上传数据中,请等待执行完毕")), outputs)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `upload while read active is blocked`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val outputs = mutableListOf<CgmOutput>()
        val uploadPort = FakeUploadPort()
        val port = FakeCgmPort().apply {
            readResults = { flow { awaitCancellation() } }   // 读挂起中
        }
        try {
            val translation = newTranslation(store, port, outputs::add, uploadPort)

            translation.submit(CgmIntent.ReadCache)
            advanceUntilIdle()
            translation.submit(CgmIntent.UploadDataset)
            advanceUntilIdle()

            assertEquals(emptyList<UploadEffect>(), uploadPort.effects)
            assertEquals(listOf(CgmOutput.Blocked("正在执行读取中,请等待执行完毕")), outputs)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `delete while upload active is blocked`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val outputs = mutableListOf<CgmOutput>()
        val uploadPort = FakeUploadPort().apply {
            results = { flow { awaitCancellation() } }
        }
        try {
            val translation = newTranslation(store, FakeCgmPort(), outputs::add, uploadPort)

            translation.submit(CgmIntent.UploadDataset)
            advanceUntilIdle()
            translation.submit(CgmIntent.DeleteCache)
            advanceUntilIdle()

            assertEquals(listOf(CgmOutput.Blocked("正在上传数据中,请等待执行完毕")), outputs)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private companion object {
        val sampleResult = CgmResult(
            resultId = 99L, jobId = 42L, datasetId = 7L,
            unitCount = 1, pointCount = 2,
            stats = PredictionStats(3.0, 9.0, 6.0, 1.0),
            units = listOf(
                ResultUnit(
                    unit = 0, title = "第一单元", pointCount = 2, mard = 0.1,
                    points = listOf(
                        ResultPoint(1, 60, "2026-07-21 12:10:45", 5.5, 5.0),
                        ResultPoint(2, 70, "2026-07-21 12:10:55", 6.5, null)
                    )
                )
            )
        )
    }
}
