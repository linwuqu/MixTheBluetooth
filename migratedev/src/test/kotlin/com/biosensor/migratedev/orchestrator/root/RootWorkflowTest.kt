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
import com.biosensor.migratedev.translation.connection.ConnectionIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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
