package com.biosensor.migratedev.orchestrator.auth

import com.biosensor.migratedev.decisioncore.auth.AuthDecisionCore
import com.biosensor.migratedev.decisioncore.auth.AuthEvent
import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.decisioncore.auth.AuthState
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.orchestrator.WorkflowOrchestrator
import com.biosensor.migratedev.port.auth.AuthCommand
import com.biosensor.migratedev.port.auth.AuthPort
import com.biosensor.migratedev.port.auth.AuthResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthWorkflowTest {

    private val session = AuthSession(
        User("1", "alice", "13800000000"),
        "token",
        100_000L
    )

    @Test
    fun appStartedRestoresAndVerifiesTheSavedSession() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val orchestrator = WorkflowOrchestrator(
            initialState = AuthState.Idle,
            decisionCore = AuthDecisionCore,
            effectExecutor = AuthEffectExecutor(FakeAuthPort(session)),
            scope = scope
        )

        orchestrator.dispatch(AuthEvent.AuthCreated)
        advanceUntilIdle()

        assertEquals(AuthState.Authenticated(session), orchestrator.state.value)
        orchestrator.close()
        scope.cancel()
    }

    @Test
    fun acceptedLoginIsSavedBeforeAuthentication() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val port = FakeAuthPort(null, loginSession = session)
        val orchestrator = WorkflowOrchestrator(
            initialState = AuthState.Idle,
            decisionCore = AuthDecisionCore,
            effectExecutor = AuthEffectExecutor(port),
            scope = scope
        )

        orchestrator.dispatch(AuthEvent.SubmitLogin("13800000000", "password"))
        advanceUntilIdle()

        assertEquals(AuthState.Authenticated(session), orchestrator.state.value)
        assertEquals(session, port.savedSession)
        orchestrator.close()
        scope.cancel()
    }

    private class FakeAuthPort(
        private val restoredSession: AuthSession?,
        private val loginSession: AuthSession? = null
    ) : AuthPort {
        var savedSession: AuthSession? = null

        override fun execute(command: AuthCommand): Flow<AuthResult> {
            return flowOf(
                when (command) {
                    AuthCommand.Local.ReadSession -> restoredSession?.let {
                        AuthResult.Local.SessionFound(it)
                    } ?: AuthResult.Local.SessionMissing

                    is AuthCommand.Local.SaveSession -> {
                        savedSession = command.session
                        AuthResult.Local.SessionSaved
                    }

                    AuthCommand.Local.ClearSession -> AuthResult.Local.SessionCleared
                    is AuthCommand.Remote.ValidateSession -> {
                        AuthResult.Remote.SessionVerified(command.session)
                    }

                    is AuthCommand.Remote.Login -> {
                        AuthResult.Remote.Accepted(requireNotNull(loginSession))
                    }

                    is AuthCommand.Remote.Register -> AuthResult.Remote.RegistrationAccepted
                }
            )
        }
    }
}
