package com.biosensor.migratedev.decisioncore.root

import com.biosensor.migratedev.decisioncore.DecisionCore
import com.biosensor.migratedev.decisioncore.Transition

sealed interface RootState {
    data object Starting : RootState
    data object StartingAuth : RootState
    data object Authenticating : RootState
    data class PreparingConnection(val userId: String) : RootState
    data class RunningConnection(val userId: String) : RootState
    data class EndingConnection(val userId: String) : RootState
    data class ClearingSession(val userId: String) : RootState
    data class ReleasingConnection(val userId: String) : RootState
}

sealed interface RootEvent {
    data object AppStartedEvent : RootEvent
    data object AuthStartedEvent : RootEvent
    data class AuthenticatedEvent(val userId: String) : RootEvent
    data object ConnectionStartedEvent : RootEvent
    data object LogoutRequestedEvent : RootEvent
    data object ConnectionStoppedEvent : RootEvent
    data object AuthSessionClearedEvent : RootEvent
    data object ConnectionReleasedEvent : RootEvent
}

sealed interface RootEffect {
    data object StartAuthEffect : RootEffect
    data class StartConnectionEffect(val userId: String) : RootEffect
    data object ClearAuthSessionEffect : RootEffect
    data object ReleaseConnectionEffect : RootEffect
}

sealed interface RootScreen {
    data object Auth : RootScreen
    data class Connection(val userId: String) : RootScreen
}

fun RootState.screen(): RootScreen = when (this) {
    RootState.Starting,
    RootState.StartingAuth,
    RootState.Authenticating,
    is RootState.PreparingConnection -> RootScreen.Auth

    is RootState.RunningConnection ->
        RootScreen.Connection(userId)

    is RootState.EndingConnection ->
        RootScreen.Connection(userId)

    is RootState.ClearingSession ->
        RootScreen.Connection(userId)

    is RootState.ReleasingConnection ->
        RootScreen.Connection(userId)
}

object RootDecisionCore : DecisionCore<RootState, RootEvent, RootEffect> {
    override fun reduce(
        currentState: RootState,
        event: RootEvent
    ): Transition<RootState, RootEffect> = when (event) {
        RootEvent.AppStartedEvent -> when (currentState) {
            RootState.Starting -> Transition(
                RootState.StartingAuth,
                listOf(RootEffect.StartAuthEffect)
            )

            else -> unchanged(currentState)
        }

        RootEvent.AuthStartedEvent -> when (currentState) {
            RootState.StartingAuth -> Transition(
                RootState.Authenticating
            )

            else -> unchanged(currentState)
        }

        is RootEvent.AuthenticatedEvent -> when (currentState) {
            RootState.StartingAuth,
            RootState.Authenticating -> Transition(
                RootState.PreparingConnection(event.userId),
                listOf(
                    RootEffect.StartConnectionEffect(event.userId)
                )
            )

            else -> unchanged(currentState)
        }

        RootEvent.ConnectionStartedEvent -> when (currentState) {
            is RootState.PreparingConnection -> Transition(
                RootState.RunningConnection(currentState.userId)
            )

            else -> unchanged(currentState)
        }

        RootEvent.LogoutRequestedEvent -> when (currentState) {
            is RootState.RunningConnection -> Transition(
                RootState.EndingConnection(currentState.userId)
            )

            else -> unchanged(currentState)
        }

        RootEvent.ConnectionStoppedEvent -> when (currentState) {
            is RootState.EndingConnection -> Transition(
                RootState.ClearingSession(currentState.userId),
                listOf(RootEffect.ClearAuthSessionEffect)
            )

            else -> unchanged(currentState)
        }

        RootEvent.AuthSessionClearedEvent -> when (currentState) {
            is RootState.ClearingSession -> Transition(
                RootState.ReleasingConnection(currentState.userId),
                listOf(RootEffect.ReleaseConnectionEffect)
            )

            else -> unchanged(currentState)
        }

        RootEvent.ConnectionReleasedEvent -> when (currentState) {
            is RootState.ReleasingConnection -> Transition(
                RootState.Authenticating
            )

            else -> unchanged(currentState)
        }
    }

    private fun unchanged(
        state: RootState
    ): Transition<RootState, RootEffect> = Transition(state)
}
