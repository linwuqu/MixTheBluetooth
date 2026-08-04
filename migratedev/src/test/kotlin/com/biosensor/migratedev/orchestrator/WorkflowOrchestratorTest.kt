package com.biosensor.migratedev.orchestrator

import com.biosensor.migratedev.decisioncore.DecisionCore
import com.biosensor.migratedev.decisioncore.Transition
import com.biosensor.migratedev.port.CommandPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkflowOrchestratorTest {

    @Test
    fun processedEventIsReportedAfterStateUpdate() = runTest {
        val observed = mutableListOf<Triple<TestState, TestEvent, TestState>>()
        val orchestrator =
            WorkflowOrchestrator<TestState, TestEvent, TestEffect>(
            initialState = TestState.Idle,
            decisionCore = DecisionCore { state: TestState, event: TestEvent ->
                when (state to event) {
                    TestState.Idle to TestEvent.Start ->
                        Transition(TestState.Running)

                    else -> Transition(state)
                }
            },
            effectExecutor = CommandPort { flowOf<TestEvent>() },
            scope = this,
            onTransition = {
                    previous: TestState,
                    event: TestEvent,
                    current: TestState ->
                observed += Triple(previous, event, current)
            }
        )

        orchestrator.dispatch(TestEvent.Start)
        advanceUntilIdle()

        assertEquals(
            listOf(
                Triple(
                    TestState.Idle,
                    TestEvent.Start,
                    TestState.Running
                )
            ),
            observed
        )
        assertEquals(TestState.Running, orchestrator.state.value)
        orchestrator.close()
    }

    @Test
    fun effectResultReturnsThroughTheEventLoop() = runTest {
        val decisionCore = DecisionCore<TestState, TestEvent, TestEffect> { state, event ->
            when (state to event) {
                TestState.Idle to TestEvent.Start -> {
                    Transition(TestState.Running, listOf(TestEffect.Finish))
                }

                TestState.Running to TestEvent.Finished -> Transition(TestState.Done)
                else -> Transition(state)
            }
        }
        val effectExecutor = CommandPort<TestEffect, TestEvent> { effect ->
            when (effect) {
                TestEffect.Finish -> flowOf(TestEvent.Finished)
            }
        }
        val orchestrator = WorkflowOrchestrator(
            initialState = TestState.Idle,
            decisionCore = decisionCore,
            effectExecutor = effectExecutor,
            scope = this
        )

        orchestrator.dispatch(TestEvent.Start)
        advanceUntilIdle()

        assertEquals(TestState.Done, orchestrator.state.value)
        orchestrator.close()
    }

    private sealed interface TestState {
        data object Idle : TestState
        data object Running : TestState
        data object Done : TestState
    }

    private sealed interface TestEvent {
        data object Start : TestEvent
        data object Finished : TestEvent
    }

    private sealed interface TestEffect {
        data object Finish : TestEffect
    }
}
