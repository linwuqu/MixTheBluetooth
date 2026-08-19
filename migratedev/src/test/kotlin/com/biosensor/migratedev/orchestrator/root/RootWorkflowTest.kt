package com.biosensor.migratedev.orchestrator.root

import com.biosensor.migratedev.decisioncore.auth.AuthEffect
import com.biosensor.migratedev.decisioncore.auth.AuthEvent
import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.decisioncore.cgm.CgmCommandPurpose
import com.biosensor.migratedev.decisioncore.cgm.CgmReadEffect
import com.biosensor.migratedev.decisioncore.cgm.CgmReadEvent
import com.biosensor.migratedev.decisioncore.cgm.CgmRecord
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEffect
import com.biosensor.migratedev.decisioncore.cgm.CgmShortEvent
import com.biosensor.migratedev.decisioncore.cgm.MarkerKind
import com.biosensor.migratedev.decisioncore.connection.ConnectionEffect
import com.biosensor.migratedev.decisioncore.connection.ConnectionEvent
import com.biosensor.migratedev.decisioncore.root.RootState
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import com.biosensor.migratedev.port.auth.AuthPort
import com.biosensor.migratedev.port.cgm.CgmPort
import com.biosensor.migratedev.port.connection.BindingSnapshot
import com.biosensor.migratedev.port.connection.ConnectionPort
import com.biosensor.migratedev.translation.cgm.CgmIntent
import com.biosensor.migratedev.translation.cgm.CgmReadPhase
import com.biosensor.migratedev.translation.connection.ConnectionIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootWorkflowTest {

    @Test
    fun `keeps auth and releases connection after logout`() =
        runTest {
            Dispatchers.setMain(
                StandardTestDispatcher(testScheduler)
            )
            val root = RootWorkflow(
                authPort = RestoringAuthPort(),
                connectionPort = EmptyConnectionPort,
                cgmPort = FakeCgmPort(),
                scope = this
            )
            try {
                advanceUntilIdle()

                assertEquals(
                    RootState.RunningConnection("user-1"),
                    root.state.value
                )
                val auth = root.authOrNull()
                val connection = root.connectionOrNull()
                assertNotNull(auth)
                assertNotNull(connection)

                requireNotNull(connection).submit(
                    ConnectionIntent.Logout
                )
                advanceUntilIdle()

                assertEquals(
                    RootState.Authenticating,
                    root.state.value
                )
                assertSame(auth, root.authOrNull())
                assertNull(root.connectionOrNull())
            } finally {
                root.close()
                Dispatchers.resetMain()
            }
        }

    private class RestoringAuthPort : AuthPort {
        private val session = AuthSession(
            user = User(
                id = "user-1",
                userName = "alice",
                telephone = "13800000000"
            ),
            token = "token-1"
        )

        override fun execute(
            effect: AuthEffect
        ): Flow<AuthEvent> = when (effect) {
            is AuthEffect.ReadSession ->
                flowOf(AuthEvent.SessionFound(session))

            is AuthEffect.ValidateSession ->
                flowOf(AuthEvent.SessionVerified(session))

            is AuthEffect.ClearSession ->
                flowOf(AuthEvent.SessionCleared)

            else -> emptyFlow()
        }
    }

    private object EmptyConnectionPort : ConnectionPort {
        override fun readBinding(
            userId: String
        ): Flow<BindingSnapshot> = flowOf(BindingSnapshot.Missing)

        override fun scanDevices():
            Flow<BluetoothDeviceInfo> = emptyFlow()

        override fun execute(
            effect: ConnectionEffect
        ): Flow<ConnectionEvent> = emptyFlow()
    }

    /** 端到端断线重连:读到一半断线 → Cgm Reconnecting → 上报 ConnectionLost → Root 转发
     * Reconnect → 连接域补断线信号并自动重连 → 重连成功转发 → Cgm 自动重读 → 完成
     * (数据快照保留,Root 全程 RunningCgm 不切页)。
     * 断线只由 Cgm 侧(命令期消费者)发出——连接域在消费权转移后听不到断线(实测 2026-08-07)。 */
    @Test
    fun `disconnect auto reconnects and cgm resumes reading`() =
        runTest {
            Dispatchers.setMain(
                StandardTestDispatcher(testScheduler)
            )
            val device = BluetoothDeviceInfo(
                id = "AA:01", name = "BT24-S", isBle = true, rssi = -40
            )
            val cgmPort = FakeCgmPort()
            val cgmCache = buildList {
                add(CgmRecord.Marker(MarkerKind.START, "Start Playback"))
                add(CgmRecord.Log("LOG:1,1,95,2026-07-21 12:10:45"))
                for (i in 1..95) {
                    add(
                        CgmRecord.Eis(
                            i, 60 + (i - 1) * 10, "a", "b", "c", "d",
                            "EIS:$i,${60 + (i - 1) * 10},a,b,c,d"
                        )
                    )
                }
                add(CgmRecord.Marker(MarkerKind.END, "Playback all done"))
            }
            // 读流程:第一次读到一半断线(命令期消费者发现),重读(RetryRead)给完整缓存
            cgmPort.readResults = { effect ->
                when (effect) {
                    is CgmReadEffect.StartRead -> flowOf(
                        CgmReadEvent.CommandAccepted(effect.session.id),
                        CgmReadEvent.RecordsProduced(
                            effect.session.id, cgmCache.take(3)   // 只到 3 条就断线
                        ),
                        CgmReadEvent.DeviceDisconnected(effect.session.id)
                    )
                    is CgmReadEffect.RetryRead -> flowOf(
                        CgmReadEvent.CommandAccepted(effect.session.id),
                        CgmReadEvent.RecordsProduced(effect.session.id, cgmCache)
                    )
                    is CgmReadEffect.WriteFile -> flowOf(
                        CgmReadEvent.FileWritten(effect.session.id, "cgm/s1.txt"))
                    is CgmReadEffect.SendDelete -> flowOf(
                        CgmReadEvent.AckReceived(effect.session.id, CgmCommandPurpose.DELETE))
                    is CgmReadEffect.StopRuntime -> flowOf()
                }
            }
            val connectPort = AutoConnectPort(device)
            val root = RootWorkflow(
                authPort = RestoringAuthPort(),
                connectionPort = connectPort,
                cgmPort = cgmPort,
                scope = this
            )
            try {
                advanceUntilIdle()
                val connection = requireNotNull(root.connectionOrNull())

                connection.submit(ConnectionIntent.BecameVisible)
                connection.submit(ConnectionIntent.BluetoothAccessGranted)
                advanceUntilIdle()
                connection.submit(ConnectionIntent.SelectDevice(device.id))
                advanceUntilIdle()

                assertEquals(RootState.RunningCgm("user-1", "AA:01"), root.state.value)
                assertEquals(1, connectPort.connectCount)   // 手动连接一次

                // 读缓存:读到一半断线 → 断线只发生在 Cgm 域(消费权转移后连接域听不到)——
                // Root 转发 Reconnect → 连接域补断线信号 + 自动重连 → 重连成功转发 →
                // Cgm 自动重读 → 完成。链路同步推进,一次 advanceUntilIdle 内全部完成
                val cgm = requireNotNull(root.cgmOrNull())
                cgm.submit(CgmIntent.ReadCache)
                advanceUntilIdle()

                assertEquals("重连后应自动重读并完成",
                    CgmReadPhase.Stopped, cgm.uiState.value.readPhase)
                assertEquals("重连成功应完成(非失败),实际: ${cgm.uiState.value.readMessage}",
                    null, cgm.uiState.value.readMessage?.takeIf { it.contains("失败") })
                assertEquals("重连应是第二次连接", 2, connectPort.connectCount)
                assertEquals("Root 全程保持 Cgm 页",
                    RootState.RunningCgm("user-1", "AA:01"), root.state.value)
            } finally {
                root.close()
                Dispatchers.resetMain()
            }
        }

    /** 每次 ConnectDevice 都成功连接(自动重连由 Root 转发 Reconnect 驱动,断线事件不经过连接端口)。 */
    private class AutoConnectPort(
        private val device: BluetoothDeviceInfo
    ) : ConnectionPort {
        var connectCount = 0

        override fun readBinding(
            userId: String
        ): Flow<BindingSnapshot> = flowOf(BindingSnapshot.Missing)

        override fun scanDevices():
            Flow<BluetoothDeviceInfo> = flowOf(device)

        override fun execute(
            effect: ConnectionEffect
        ): Flow<ConnectionEvent> = when (effect) {
            is ConnectionEffect.ConnectDevice -> flowOf(
                ConnectionEvent.DeviceConnected(device)
            ).onStart { connectCount++ }

            is ConnectionEffect.SaveBinding ->
                flowOf(ConnectionEvent.BindingSaved(effect.deviceId))

            ConnectionEffect.DisconnectDevice -> emptyFlow()
        }
    }

    /** 端到端:连接成功 → Root 挂载 Cgm → 读缓存完成上报 Root(只记录不切页)。 */
    @Test
    fun `connection success mounts cgm and completed read reports to root`() =
        runTest {
            Dispatchers.setMain(
                StandardTestDispatcher(testScheduler)
            )
            val device = BluetoothDeviceInfo(
                id = "AA:01", name = "BT24-S", isBle = true, rssi = -40
            )
            val cgmPort = FakeCgmPort()
            val connectingPort = object : ConnectionPort {
                override fun readBinding(
                    userId: String
                ): Flow<BindingSnapshot> = flowOf(BindingSnapshot.Missing)

                override fun scanDevices():
                    Flow<BluetoothDeviceInfo> = flowOf(device)

                override fun execute(
                    effect: ConnectionEffect
                ): Flow<ConnectionEvent> = when (effect) {
                    is ConnectionEffect.ConnectDevice ->
                        flowOf(ConnectionEvent.DeviceConnected(device))

                    is ConnectionEffect.SaveBinding ->
                        flowOf(ConnectionEvent.BindingSaved(effect.deviceId))

                    ConnectionEffect.DisconnectDevice -> emptyFlow()
                }
            }
            val root = RootWorkflow(
                authPort = RestoringAuthPort(),
                connectionPort = connectingPort,
                cgmPort = cgmPort,
                scope = this
            )
            try {
                advanceUntilIdle()
                val connection = requireNotNull(root.connectionOrNull())

                connection.submit(ConnectionIntent.BecameVisible)
                connection.submit(ConnectionIntent.BluetoothAccessGranted)
                advanceUntilIdle()
                connection.submit(ConnectionIntent.SelectDevice(device.id))
                advanceUntilIdle()

                // 连接成功 → Connected 上报 → Root 进入 Cgm 页面并挂载翻译
                assertEquals(
                    RootState.RunningCgm("user-1", "AA:01"),
                    root.state.value
                )
                val cgm = requireNotNull(root.cgmOrNull())

                // 读缓存端到端:ReadCompleted → RootEvent.CgmCompletedEvent(只记录)
                cgmPort.completeReadFlow()
                cgm.submit(CgmIntent.ReadCache)
                advanceUntilIdle()

                assertEquals(
                    RootState.RunningCgm("user-1", "AA:01"),
                    root.state.value
                )
            } finally {
                root.close()
                Dispatchers.resetMain()
            }
        }

    private class FakeCgmPort : CgmPort {
        var readResults: (CgmReadEffect) -> Flow<CgmReadEvent> = { flowOf() }
        var shortResults: (CgmShortEffect) -> Flow<CgmShortEvent> = { flowOf() }

        override fun execute(effect: CgmReadEffect): Flow<CgmReadEvent> =
            readResults(effect)

        override fun execute(effect: CgmShortEffect): Flow<CgmShortEvent> =
            shortResults(effect)
    }

    /** 完整单段缓存:EIS 段 95 点,校验必过 → 双闸门完成。 */
    private fun FakeCgmPort.completeReadFlow() {
        val cache = buildList {
            add(CgmRecord.Marker(MarkerKind.START, "Start Playback"))
            add(CgmRecord.Log("LOG:1,1,95,2026-07-21 12:10:45"))
            for (i in 1..95) {
                add(
                    CgmRecord.Eis(
                        i, 60 + (i - 1) * 10, "a", "b", "c", "d",
                        "EIS:$i,${60 + (i - 1) * 10},a,b,c,d"
                    )
                )
            }
            add(CgmRecord.Marker(MarkerKind.END, "Playback all done"))
        }
        readResults = { effect ->
            when (effect) {
                is CgmReadEffect.StartRead -> flowOf(
                    CgmReadEvent.CommandAccepted(effect.session.id),
                    CgmReadEvent.RecordsProduced(effect.session.id, cache)
                )
                is CgmReadEffect.RetryRead -> flowOf(
                    CgmReadEvent.CommandAccepted(effect.session.id),
                    CgmReadEvent.RecordsProduced(effect.session.id, cache)
                )
                is CgmReadEffect.WriteFile -> flowOf(
                    CgmReadEvent.FileWritten(effect.session.id, "cgm/s1.txt"))
                is CgmReadEffect.SendDelete -> flowOf(
                    CgmReadEvent.AckReceived(effect.session.id, CgmCommandPurpose.DELETE))
                is CgmReadEffect.StopRuntime -> flowOf()
            }
        }
    }
}
