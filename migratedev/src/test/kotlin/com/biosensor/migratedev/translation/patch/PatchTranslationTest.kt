package com.biosensor.migratedev.translation.patch

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.biosensor.migratedev.decisioncore.patch.PatchBinding
import com.biosensor.migratedev.decisioncore.patch.PatchCode
import com.biosensor.migratedev.decisioncore.patch.PatchEffect
import com.biosensor.migratedev.decisioncore.patch.PatchEvent
import com.biosensor.migratedev.port.patch.PatchPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 贴片翻译层:进页面读列表 → 扫码 → 落库 → 关子页,以及每一步出问题时的说法。 */
@OptIn(ExperimentalCoroutinesApi::class)
class PatchTranslationTest {

    private val sampleA = "CGPATCH/1|patchId=550e8400-e29b-41d4-a716-446655440000|lot=DEMO-260701"

    /**
     * 假端口:像真库一样记账——同 `patchId` 重扫只刷新时间,新的一直排最前。
     * 这样「扫完列表里就该有它」这条断言才真的在验链路,而不只是回放我喂进去的数据。
     */
    private class RecordingPatchPort : PatchPort {
        val effects = mutableListOf<PatchEffect>()
        var bindings: List<PatchBinding> = emptyList()
        var loadFailure: String? = null
        var bindFailure: String? = null
        private var clock = 0L

        override fun execute(effect: PatchEffect): Flow<PatchEvent> = flow {
            effects += effect
            when (effect) {
                is PatchEffect.LoadBindings ->
                    emit(loadFailure?.let { PatchEvent.LoadFailed(it) }
                        ?: PatchEvent.BindingsLoaded(bindings))

                is PatchEffect.Bind -> {
                    val failure = bindFailure
                    if (failure != null) {
                        emit(PatchEvent.BindFailed(failure))
                    } else {
                        val row = effect.code.toBinding(++clock)
                        bindings = listOf(row) + bindings.filterNot {
                            it.patchId == row.patchId
                        }
                        emit(PatchEvent.Bound)
                    }
                }
            }
        }
    }

    private fun binding(patchId: String, boundAt: Long = 1L) = PatchBinding(
        patchId = patchId, modelId = null, lotNo = null, manufacturedOn = null,
        expiresOn = null, maxUseMinutes = null, calibrationRef = null,
        rawCode = "raw-$patchId", boundAtMillis = boundAt
    )

    @Test
    fun `session start reads the bindings of this device`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val port = RecordingPatchPort()
        port.bindings = listOf(binding("patch-a"))
        try {
            val translation = newTranslation(store, port)
            advanceUntilIdle()

            assertEquals(
                listOf(PatchEffect.LoadBindings("user-1", "AA:01")),
                port.effects
            )
            assertEquals(
                listOf("patch-a"),
                translation.uiState.value.bindings.map { it.patchId }
            )
        } finally {
            store.clear()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `an unbound device opens the scanner on entry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val port = RecordingPatchPort()
        try {
            val translation = newTranslation(store, port)
            advanceUntilIdle()

            assertTrue(
                "库里一条都没有,进页面就该让人扫",
                translation.uiState.value.shouldScan
            )
        } finally {
            store.clear()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `a bound device opens straight to the cgm page`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val port = RecordingPatchPort()
        port.bindings = listOf(binding("patch-a"), binding("patch-b", boundAt = 2L))
        try {
            val translation = newTranslation(store, port)
            advanceUntilIdle()

            assertFalse(translation.uiState.value.shouldScan)
            assertEquals(
                listOf("patch-a", "patch-b"),
                translation.uiState.value.bindings.map { it.patchId }
            )
        } finally {
            store.clear()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `a failed read says so instead of pretending the device is unbound`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val port = RecordingPatchPort()
        port.loadFailure = "本地库打不开"
        try {
            val translation = newTranslation(store, port)
            advanceUntilIdle()

            assertEquals("本地库打不开", translation.uiState.value.message)
            assertFalse(
                "读不到不等于没绑过,别骗用户去绑一枚已经绑过的",
                translation.uiState.value.shouldScan
            )
        } finally {
            store.clear()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `a good code is written then the page closes with the list refreshed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val port = RecordingPatchPort()
        try {
            val translation = newTranslation(store, port)
            advanceUntilIdle()

            translation.submit(PatchIntent.OpenScanner)
            advanceUntilIdle()
            assertTrue(translation.uiState.value.scanning)

            translation.submit(PatchIntent.CodeScanned(sampleA, null))
            advanceUntilIdle()

            val bind = port.effects.filterIsInstance<PatchEffect.Bind>().single()
            assertEquals("user-1", bind.userId)
            assertEquals("AA:01", bind.deviceId)
            assertEquals(
                "550e8400-e29b-41d4-a716-446655440000",
                bind.code.patchId
            )

            assertFalse("写成功了页面自己关", translation.uiState.value.scanning)
            assertNull(translation.uiState.value.message)
            assertEquals(
                listOf("550e8400-e29b-41d4-a716-446655440000"),
                translation.uiState.value.bindings.map { it.patchId }
            )
        } finally {
            store.clear()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `raw bytes carry a code the decoder could not turn into text`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val port = RecordingPatchPort()
        try {
            val translation = newTranslation(store, port)
            advanceUntilIdle()
            translation.submit(PatchIntent.OpenScanner)
            advanceUntilIdle()

            translation.submit(
                PatchIntent.CodeScanned(null, sampleA.toByteArray(Charsets.UTF_8))
            )
            advanceUntilIdle()

            assertEquals(
                1, port.effects.filterIsInstance<PatchEffect.Bind>().size
            )
            assertFalse(translation.uiState.value.scanning)
        } finally {
            store.clear()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `an unreadable code stays on the scanner with the reason`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val port = RecordingPatchPort()
        try {
            val translation = newTranslation(store, port)
            advanceUntilIdle()
            translation.submit(PatchIntent.OpenScanner)
            advanceUntilIdle()

            translation.submit(PatchIntent.CodeScanned("https://example.com", null))
            advanceUntilIdle()

            assertTrue(translation.uiState.value.scanning)
            assertEquals("这不是贴片码", translation.uiState.value.message)
            assertEquals(
                "解析都没过,不该白写一次库",
                emptyList<PatchEffect>(),
                port.effects.filterNot {
                    it is PatchEffect.LoadBindings
                }
            )
        } finally {
            store.clear()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `a failed write stays on the scanner so the user can retry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val port = RecordingPatchPort()
        port.bindFailure = "磁盘已满"
        try {
            val translation = newTranslation(store, port)
            advanceUntilIdle()
            translation.submit(PatchIntent.OpenScanner)
            advanceUntilIdle()

            translation.submit(PatchIntent.CodeScanned(sampleA, null))
            advanceUntilIdle()

            assertTrue(translation.uiState.value.scanning)
            assertEquals("磁盘已满", translation.uiState.value.message)
        } finally {
            store.clear()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `dismissing with a reason leaves it on the cgm page`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val port = RecordingPatchPort()
        try {
            val translation = newTranslation(store, port)
            advanceUntilIdle()
            translation.submit(PatchIntent.OpenScanner)
            advanceUntilIdle()

            translation.submit(PatchIntent.CloseScanner("没有相机权限,无法扫码"))
            advanceUntilIdle()

            assertFalse(translation.uiState.value.scanning)
            assertEquals("没有相机权限,无法扫码", translation.uiState.value.message)

            translation.submit(PatchIntent.OpenScanner)
            advanceUntilIdle()

            assertNull(
                "下次开扫码页时上一次的原因要清掉",
                translation.uiState.value.message
            )
        } finally {
            store.clear()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `skipping without a reason leaves no message behind`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val port = RecordingPatchPort()
        try {
            val translation = newTranslation(store, port)
            advanceUntilIdle()
            translation.submit(PatchIntent.OpenScanner)
            advanceUntilIdle()

            translation.submit(PatchIntent.CloseScanner())
            advanceUntilIdle()

            assertFalse(translation.uiState.value.scanning)
            assertNull(translation.uiState.value.message)
            assertFalse(
                "主动跳过之后不能再被自动弹回来",
                translation.uiState.value.shouldScan
            )
        } finally {
            store.clear()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    private fun newTranslation(
        store: ViewModelStore,
        port: PatchPort,
    ): PatchTranslation = ViewModelProvider(
        store,
        PatchTranslation.factory(userId = "user-1", deviceId = "AA:01", port = port)
    )[PatchTranslation::class.java]
}

/** 顶层函数,好让嵌套的假端口也能用(嵌套类看不见外面类的成员)。 */
private fun PatchCode.toBinding(boundAt: Long) = PatchBinding(
    patchId = patchId,
    modelId = modelId?.toLong(),
    lotNo = lotNo,
    manufacturedOn = manufacturedOn,
    expiresOn = expiresOn,
    maxUseMinutes = maxUseMinutes?.toLong(),
    calibrationRef = calibrationRef,
    rawCode = raw,
    boundAtMillis = boundAt
)
