package com.biosensor.migratedev.orchestrator.root

import com.biosensor.migratedev.decisioncore.auth.AuthEffect
import com.biosensor.migratedev.decisioncore.auth.AuthEvent
import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.decisioncore.connection.ConnectionEffect
import com.biosensor.migratedev.decisioncore.connection.ConnectionEvent
import com.biosensor.migratedev.decisioncore.root.RootState
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import com.biosensor.migratedev.port.auth.AuthPort
import com.biosensor.migratedev.port.connection.BindingSnapshot
import com.biosensor.migratedev.port.connection.ConnectionPort
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
}
