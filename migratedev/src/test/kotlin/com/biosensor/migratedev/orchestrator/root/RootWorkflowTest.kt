package com.biosensor.migratedev.orchestrator.root

import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.decisioncore.root.RootState
import com.biosensor.migratedev.port.auth.AuthCommand
import com.biosensor.migratedev.port.auth.AuthPort
import com.biosensor.migratedev.port.auth.AuthResult
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import com.biosensor.migratedev.port.connection.ConnectionCommand
import com.biosensor.migratedev.port.connection.ConnectionPort
import com.biosensor.migratedev.port.connection.ConnectionResult
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
            command: AuthCommand
        ): Flow<AuthResult> = when (command) {
            AuthCommand.Local.ReadSession ->
                flowOf(AuthResult.Local.SessionFound(session))

            is AuthCommand.Remote.ValidateSession ->
                flowOf(AuthResult.Remote.SessionVerified(session))

            AuthCommand.Local.ClearSession ->
                flowOf(AuthResult.Local.SessionCleared)

            else -> emptyFlow()
        }
    }

    private object EmptyConnectionPort : ConnectionPort {
        override fun scanDevices():
            Flow<BluetoothDeviceInfo> = emptyFlow()

        override fun execute(
            command: ConnectionCommand
        ): Flow<ConnectionResult> = emptyFlow()
    }
}
