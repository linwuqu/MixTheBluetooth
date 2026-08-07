package com.biosensor.migratedev.translation.cgm

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.biosensor.migratedev.decisioncore.cgm.CgmCommandPurpose
import com.biosensor.migratedev.decisioncore.cgm.CgmReadEffect
import com.biosensor.migratedev.decisioncore.cgm.CgmReadEvent
import com.biosensor.migratedev.decisioncore.cgm.CgmRecord
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEffect
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEvent
import com.biosensor.migratedev.decisioncore.cgm.MarkerKind
import com.biosensor.migratedev.port.cgm.CgmPort
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

        override fun execute(effect: CgmReadEffect): Flow<CgmReadEvent> = readResults(effect)

        override fun execute(effect: CgmShortEffect): Flow<CgmShortEvent> = shortResults(effect)
    }

    /** 完整单段缓存:EIS 段 95 点,校验必过。 */
    private fun fullCache(): List<CgmRecord> = buildList {
        add(CgmRecord.Marker(MarkerKind.START, "Start Playback"))
        add(CgmRecord.Log("LOG:1,1,95,2026-07-21 12:10:45"))
        for (i in 1..95) {
            add(CgmRecord.Eis(i, 60 + (i - 1) * 10, "a", "b", "c", "d",
                "EIS:$i,${60 + (i - 1) * 10},a,b,c,d"))
        }
        add(CgmRecord.Marker(MarkerKind.END, "Playback all done"))
    }

    /** 声明 95 点只到 1 点:校验必不过,触发重读。 */
    private fun incompleteCache(): List<CgmRecord> = listOf(
        CgmRecord.Marker(MarkerKind.START, "Start Playback"),
        CgmRecord.Log("LOG:1,1,95,2026-07-21 12:10:45"),
        CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d"),
        CgmRecord.Marker(MarkerKind.END, "Playback all done")
    )

    /** 读全流程 fake:StartRead/RetryRead 回完整缓存,写盘/删除都成功 → 双闸门完成。 */
    private fun FakeCgmPort.completeReadFlow() {
        readResults = { effect ->
            when (effect) {
                is CgmReadEffect.StartRead -> flowOf(
                    CgmReadEvent.CommandAccepted(effect.session.id),
                    CgmReadEvent.RecordsProduced(effect.session.id, fullCache())
                )
                is CgmReadEffect.RetryRead -> flowOf(
                    CgmReadEvent.CommandAccepted(effect.session.id),
                    CgmReadEvent.RecordsProduced(effect.session.id, fullCache())
                )
                is CgmReadEffect.WriteFile -> flowOf(
                    CgmReadEvent.FileWritten(effect.session.id, "cgm/s1.txt"))
                is CgmReadEffect.SendDelete -> flowOf(
                    CgmReadEvent.AckReceived(effect.session.id, CgmCommandPurpose.DELETE))
                is CgmReadEffect.StopRuntime -> flowOf()
            }
        }
    }

    private fun newTranslation(
        store: ViewModelStore,
        port: FakeCgmPort,
        report: (CgmOutput) -> Unit
    ): CgmTranslation = ViewModelProvider(
        store,
        CgmTranslation.factory(deviceId, port, report)
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

            // 双闸门完成 → Stopped → 上报 ReadCompleted(仅一次)
            assertEquals(listOf<CgmOutput>(CgmOutput.ReadCompleted("cgm/s1.txt")), outputs)
            assertEquals(CgmReadPhase.Stopped, translation.uiState.value.readPhase)
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
            assertEquals(CgmReadPhase.Idle, translation.uiState.value.readPhase)
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
            assertEquals(CgmReadPhase.Sending, translation.uiState.value.readPhase)
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
                is CgmReadEffect.StartRead -> flowOf(
                    CgmReadEvent.CommandAccepted(effect.session.id),
                    CgmReadEvent.RecordsProduced(effect.session.id, fullCache())
                )
                is CgmReadEffect.RetryRead -> flowOf(
                    CgmReadEvent.CommandAccepted(effect.session.id),
                    CgmReadEvent.RecordsProduced(effect.session.id, fullCache())
                )
                is CgmReadEffect.WriteFile -> {
                    writeFileCount++
                    if (writeFileCount == 1) {
                        flowOf(CgmReadEvent.FileWriteFailed(effect.session.id, "磁盘满"))
                    } else {
                        flowOf(CgmReadEvent.FileWritten(effect.session.id, "cgm/s1.txt"))
                    }
                }
                is CgmReadEffect.SendDelete -> flowOf(
                    CgmReadEvent.AckReceived(effect.session.id, CgmCommandPurpose.DELETE))
                is CgmReadEffect.StopRuntime -> flowOf()
            }
        }
        try {
            val translation = newTranslation(store, port, outputs::add)

            translation.submit(CgmIntent.ReadCache)
            advanceUntilIdle()
            assertEquals(CgmReadPhase.Error, translation.uiState.value.readPhase)

            // 第二次读:Error 自动复位(无 Blocked),完整跑完 → ReadCompleted
            translation.submit(CgmIntent.ReadCache)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    CgmOutput.Failed("磁盘满"),
                    CgmOutput.ReadCompleted("cgm/s1.txt")
                ),
                outputs)
            assertEquals(CgmReadPhase.Stopped, translation.uiState.value.readPhase)
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
                    is CgmReadEffect.StartRead -> flowOf(
                        CgmReadEvent.CommandAccepted(effect.session.id),
                        CgmReadEvent.RecordsProduced(effect.session.id, incompleteCache())
                    )
                    is CgmReadEffect.RetryRead -> flowOf(
                        CgmReadEvent.CommandAccepted(effect.session.id),
                        CgmReadEvent.RecordsProduced(effect.session.id, incompleteCache())
                    )
                    else -> flowOf()
                }
            }
        }
        try {
            val translation = newTranslation(store, port, outputs::add)

            translation.submit(CgmIntent.ReadCache)
            advanceUntilIdle()

            // 4 次超时/校验失败:每次 Failed 转移都上报;最后一次 StopRuntime(RETRY_EXHAUSTED)
            assertTrue(
                "应上报 4 次 Failed,实际 $outputs",
                outputs.size == 4 && outputs.all { it is CgmOutput.Failed }
            )
            assertEquals(CgmReadPhase.Failed, translation.uiState.value.readPhase)
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
            assertEquals(CgmReadPhase.Receiving, ui.readPhase)
            assertTrue(ui.readMessage!!.startsWith("接收中"))
            assertEquals(10, ui.progressPoints)
            assertEquals(1, ui.roundCount)
            assertEquals(10, ui.recentLines.size)   // 最近 10 条(含 START/LOG)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
