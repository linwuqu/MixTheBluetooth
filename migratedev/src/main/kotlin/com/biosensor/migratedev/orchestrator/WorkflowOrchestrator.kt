package com.biosensor.migratedev.orchestrator

import com.biosensor.migratedev.decisioncore.DecisionCore
import com.biosensor.migratedev.port.CommandPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 抓大放小 简单的理解一下 Orchestrator 即
 * decisionCore.reduce(initialState, onEvent(event)) -> newState, effectExecutor.execute(effects).collect { newEvent -> onEvent(newEvent)}
 *
 * 1. initialState 和 newState 不会向外通知变更 所以进行包装 形成 StateFlow(Readable) MutableStateFlow(Readable & Writeable)
 * 这样所有收集这个信息的主体都可以收到变更通知 by using .collectAsStateWithLifecycle()
 *
 * 2. onEvent应该排队处理 避免并发问题 给出一个足够大的队列 events: Channel<Event>(Channel.UNLIMITED)
 * 通过 dispatch 挂到 events 中 表示受理
 * 启动协程来处理 event
 * scope.launch {
 *     for (event in events) {
 *         // 处理 event
 *     }
 * }
 *
 * 3. effectExecutor.execute(effects).collect { newEvent -> onEvent(newEvent) 这个过程是异步的 可能会很慢 它不应该拖累主循环
 * 所以单独赋予一个子作用域
 * private val effectScope = CoroutineScope(
 *     scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])
 * )
 * 这样就可以做到
 * transition.effects.forEach { effect ->
 *       effectScope.launch {
 *              effectExecutor.execute(effect).collect(events::send)
 *      }
 * }
 *
 * 4. 记得清理资源 使用 AutoCloseable::close 将 子作用域、队列、主循环及时清理
 *
 * 5. onTransition 是还原状态机瞬间的最小切面 用于将情况 report 给 RootWorkflow
 */
class WorkflowOrchestrator<State, Event, Effect>(
    initialState: State,
    private val decisionCore: DecisionCore<State, Event, Effect>,
    private val effectExecutor: CommandPort<Effect, Event>,
    scope: CoroutineScope,
    private val logTag: String = "Workflow",
    private val onTransition: (
        previousState: State, event: Event, currentState: State
    ) -> Unit = { _, _, _ -> }
) : AutoCloseable {

    private val events = Channel<Event>(capacity = Channel.UNLIMITED)

    private val effectScope = CoroutineScope(
        scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])
    )

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    private val loopJob = scope.launch {
        for (event in events) {
            val previousState = _state.value
            val startedAt = System.nanoTime()
            val transition = decisionCore.reduce(
                previousState, event
            )
            _state.value = transition.newState
            onTransition(
                previousState, event, transition.newState
            )
            Timber.tag(logTag).i(
                "previous=%s event=%s new=%s effects=%s durationMicros=%d",
                previousState.safeTypeName(),
                event.safeTypeName(),
                transition.newState.safeTypeName(),
                transition.effects.map { it.safeTypeName() },
                (System.nanoTime() - startedAt) / 1_000
            )
            transition.effects.forEach { effect ->
                effectScope.launch {
                    effectExecutor.execute(effect).collect(events::send)
                }
            }
        }
    }

    fun dispatch(event: Event) {
        check(events.trySend(event).isSuccess) { "WorkflowOrchestrator 已关闭" }
    }

    override fun close() {
        effectScope.coroutineContext[Job]?.cancel()
        events.close()
        loopJob.cancel()
    }
}

private fun Any?.safeTypeName(): String = this?.javaClass?.simpleName ?: "null"
