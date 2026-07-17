package com.biosensor.migratedev.decisioncore

/**
 * 状态机的纯决策合约，只计算状态变化，不执行 IO。
 */
fun interface DecisionCore<State, Event, Effect> {

    /**
     * 根据当前状态和事件，计算下一状态以及随后需要执行的副作用。
     *
     * ` // reduce(currentState, event) -> newState + effects`
     */
    fun reduce(
        currentState: State,
        event: Event
    ): Transition<State, Effect>
}

/**
 * 一次状态机决策的完整输出：下一状态，以及随后需要执行的副作用。
 */
data class Transition<State, Effect>(
    val newState: State,
    val effects: List<Effect> = emptyList()
)
