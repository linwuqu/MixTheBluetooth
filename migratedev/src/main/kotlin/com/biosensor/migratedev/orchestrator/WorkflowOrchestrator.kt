package com.biosensor.migratedev.orchestrator

import com.biosensor.migratedev.decisioncore.DecisionCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 所有工作流共用的事件回环，统一负责事件排队、状态发布和副作用生命周期。
 *
 * `event -> DecisionCore -> newState + effects -> EffectExecutor -> event`
 */
class WorkflowOrchestrator<State, Event, Effect>(
    initialState: State,
    private val decisionCore: DecisionCore<State, Event, Effect>,
    private val effectExecutor: EffectExecutor<Effect, Event>,
    scope: CoroutineScope
) : AutoCloseable {

    // 所有事件统一进入 Channel 排队，再由 loopJob 按到达顺序逐个交给状态机。
    private val events = Channel<Event>(capacity = Channel.UNLIMITED)

    /**
     * 主事件循环：
     *
     * Event -> reduce -> newState
     *
     * 必须快速完成。
     *
     *
     * Effect可能很慢：
     *
     * 网络请求
     * 蓝牙通信
     * 文件 IO
     *
     *
     * 所以Effect必须异步执行。
     */
    private val effectScope = CoroutineScope(
        scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])
    )

    // _state 是内部状态存储，Orchestrator 可以修改 _state.value
    private val _state = MutableStateFlow(initialState)

    /**
     * state 是对外暴露的只读视图，其他层可以观察，但不能直接赋值
     *
     * state 不是静态不变的，它会随着 _state.value 改变而持续发出新状态
     */
    val state: StateFlow<State> = _state.asStateFlow()

    private val loopJob = scope.launch {
        for (event in events) {
            val transition = decisionCore.reduce(_state.value, event)
            _state.value = transition.newState
            transition.effects.forEach { effect ->
                effectScope.launch {
                    effectExecutor.execute(effect).collect(events::send)
                }
            }
        }
    }

    // 分配任务进入Channel队列中
    fun dispatch(event: Event) {
        check(events.trySend(event).isSuccess) { "WorkflowOrchestrator 已关闭" }
    }

    override fun close() {
        effectScope.coroutineContext[Job]?.cancel()
        events.close()
        loopJob.cancel()
    }
}
