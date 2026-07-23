package com.biosensor.migratedev.translation.auth

import com.biosensor.migratedev.decisioncore.auth.AuthDecisionCore
import com.biosensor.migratedev.decisioncore.auth.AuthEffect
import com.biosensor.migratedev.decisioncore.auth.AuthEvent
import com.biosensor.migratedev.decisioncore.auth.AuthState
import com.biosensor.migratedev.orchestrator.EffectExecutor
import com.biosensor.migratedev.orchestrator.WorkflowOrchestrator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthTranslationTest {

    @Test
    fun appStartedLifecycleSignalEntersSessionRestoration() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val orchestrator = WorkflowOrchestrator(
            initialState = AuthState.Idle,
            decisionCore = AuthDecisionCore,
            effectExecutor = EffectExecutor<AuthEffect, AuthEvent> { emptyFlow() },
            scope = scope
        )
        val translation = AuthTranslation(orchestrator, scope)

        translation.onLifecycle(AuthLifecycleEvent.AppStarted)
        advanceUntilIdle()

        assertEquals(AuthUiState.RestoringSession, translation.uiState.value)
        orchestrator.close()
        scope.cancel()
    }
}
