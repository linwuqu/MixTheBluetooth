package com.biosensor.migratedev.decisioncore.patch

import com.biosensor.migratedev.decisioncore.Transition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 绑定状态机:扫到没、解析过没、落库成没。 */
class PatchDecisionTest {

    private val sampleA = "CGPATCH/1|patchId=550e8400-e29b-41d4-a716-446655440000|lot=DEMO-260701"

    private fun row(patchId: String, boundAt: Long = 0L) = PatchBinding(
        patchId = patchId, modelId = null, lotNo = null, manufacturedOn = null,
        expiresOn = null, maxUseMinutes = null, calibrationRef = null,
        rawCode = "", boundAtMillis = boundAt
    )

    private fun reduce(
        state: PatchState, event: PatchEvent
    ): Transition<PatchState, PatchEffect> = PatchDecisionCore.reduce(state, event)

    /** 会话已建立、列表已读回的状态。 */
    private fun session(bindings: List<PatchBinding> = emptyList()) =
        reduce(PatchState(), PatchEvent.SessionRequested("user-1", "AA:01"))
            .newState
            .let { reduce(it, PatchEvent.BindingsLoaded(bindings)).newState }

    @Test
    fun `session start loads the existing bindings`() {
        val transition = reduce(PatchState(), PatchEvent.SessionRequested("user-1", "AA:01"))

        assertEquals("user-1", transition.newState.userId)
        assertEquals("AA:01", transition.newState.deviceId)
        assertEquals(
            listOf(PatchEffect.LoadBindings("user-1", "AA:01")),
            transition.effects
        )
    }

    @Test
    fun `an empty list asks for the scanner on entry`() {
        assertTrue(session().autoScan)
        assertFalse(session(listOf(row("a"))).autoScan)
    }

    @Test
    fun `a failed load does not claim the device is unbound`() {
        val loaded = reduce(
            reduce(PatchState(), PatchEvent.SessionRequested("user-1", "AA:01")).newState,
            PatchEvent.LoadFailed("数据库打不开")
        ).newState

        assertTrue(loaded.loaded)
        assertFalse("读不到不等于没绑过,别骗用户去绑一枚已经绑过的", loaded.autoScan)
        assertEquals("数据库打不开", loaded.message)
    }

    @Test
    fun `opening the scanner clears the one shot auto flag`() {
        val opened = reduce(session(), PatchEvent.ScanRequested).newState

        assertEquals(PatchPhase.SCANNING, opened.phase)
        assertFalse("开过一次就不再自动弹", opened.autoScan)

        val closed = reduce(opened, PatchEvent.ScanDismissed()).newState
        assertFalse("用户跳过之后不能再被弹回来", closed.autoScan)
        assertEquals(PatchPhase.IDLE, closed.phase)
    }

    @Test
    fun `a late bindings list does not reopen the scanner`() {
        val scanning = reduce(session(), PatchEvent.ScanRequested).newState

        val late = reduce(scanning, PatchEvent.BindingsLoaded(emptyList())).newState

        assertFalse("列表还在飞的时候点过按钮:不能把用户按在扫码页里出不去", late.autoScan)
        assertEquals(PatchPhase.SCANNING, late.phase)
    }

    @Test
    fun `a well formed code goes to the database`() {
        val scanning = reduce(session(), PatchEvent.ScanRequested).newState

        val transition = reduce(scanning, PatchEvent.CodeScanned(sampleA, null))

        assertEquals(PatchPhase.BINDING, transition.newState.phase)
        val bind = transition.effects.single() as PatchEffect.Bind
        assertEquals("user-1", bind.userId)
        assertEquals("AA:01", bind.deviceId)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", bind.code.patchId)
    }

    @Test
    fun `a rejected code keeps the scanner open with the reason`() {
        val scanning = reduce(session(), PatchEvent.ScanRequested).newState

        val transition = reduce(scanning, PatchEvent.CodeScanned("https://example.com", null))

        assertEquals(PatchPhase.SCANNING, transition.newState.phase)
        assertEquals("这不是贴片码", transition.newState.message)
        assertEquals(emptyList<PatchEffect>(), transition.effects)
    }

    @Test
    fun `a late scan result is ignored once the page is closed`() {
        val idle = session()

        val transition = reduce(idle, PatchEvent.CodeScanned(sampleA, null))

        assertEquals(idle, transition.newState)
        assertEquals(emptyList<PatchEffect>(), transition.effects)
    }

    @Test
    fun `binding closes the page and reloads the list`() {
        val binding = reduce(
            reduce(session(), PatchEvent.ScanRequested).newState,
            PatchEvent.CodeScanned(sampleA, null)
        ).newState

        val transition = reduce(binding, PatchEvent.Bound)

        assertEquals(PatchPhase.IDLE, transition.newState.phase)
        assertNull(transition.newState.message)
        assertEquals(
            listOf(PatchEffect.LoadBindings("user-1", "AA:01")),
            transition.effects
        )
    }

    @Test
    fun `a failed write stays on the scanner so the user can retry`() {
        val binding = reduce(
            reduce(session(), PatchEvent.ScanRequested).newState,
            PatchEvent.CodeScanned(sampleA, null)
        ).newState

        val transition = reduce(binding, PatchEvent.BindFailed("磁盘已满"))

        assertEquals(PatchPhase.SCANNING, transition.newState.phase)
        assertEquals("磁盘已满", transition.newState.message)
    }

    @Test
    fun `back is ignored while the write is in flight`() {
        val binding = reduce(
            reduce(session(), PatchEvent.ScanRequested).newState,
            PatchEvent.CodeScanned(sampleA, null)
        ).newState

        val transition = reduce(binding, PatchEvent.ScanDismissed())

        assertEquals(
            "写一下就完,让页面自己关,免得页关了而绑定成了",
            PatchPhase.BINDING, transition.newState.phase
        )
    }

    @Test
    fun `closing with a reason leaves it on screen`() {
        val scanning = reduce(session(), PatchEvent.ScanRequested).newState

        val closed = reduce(scanning, PatchEvent.ScanDismissed("没有相机权限,无法扫码")).newState

        assertEquals(PatchPhase.IDLE, closed.phase)
        assertEquals("没有相机权限,无法扫码", closed.message)

        val reopened = reduce(closed, PatchEvent.ScanRequested).newState
        assertNull("下次开扫码页时旧原因要清掉", reopened.message)
    }

    @Test
    fun `opening twice does not restart the session`() {
        val scanning = reduce(session(), PatchEvent.ScanRequested).newState

        val again = reduce(scanning, PatchEvent.ScanRequested)

        assertEquals(scanning, again.newState)
        assertEquals(emptyList<PatchEffect>(), again.effects)
    }
}
