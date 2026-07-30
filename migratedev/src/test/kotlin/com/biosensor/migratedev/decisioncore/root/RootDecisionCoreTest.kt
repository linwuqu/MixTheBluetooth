package com.biosensor.migratedev.decisioncore.root

import org.junit.Assert.assertEquals
import org.junit.Test

class RootDecisionCoreTest {

    @Test
    fun `app start creates auth task`() {
        val starting = RootDecisionCore.reduce(
            RootState.Starting,
            RootEvent.AppStartedEvent
        )

        assertEquals(RootState.StartingAuth, starting.newState)
        assertEquals(
            listOf(RootEffect.StartAuthEffect),
            starting.effects
        )

        val started = RootDecisionCore.reduce(
            starting.newState,
            RootEvent.AuthStartedEvent
        )
        assertEquals(RootState.Authenticating, started.newState)
        assertEquals(emptyList<RootEffect>(), started.effects)
    }

    @Test
    fun `authentication prepares connection before changing page`() {
        val result = RootDecisionCore.reduce(
            RootState.Authenticating,
            RootEvent.AuthenticatedEvent("user-1")
        )

        assertEquals(
            RootState.PreparingConnection("user-1"),
            result.newState
        )
        assertEquals(RootScreen.Auth, result.newState.screen())
        assertEquals(
            listOf(RootEffect.StartConnectionEffect("user-1")),
            result.effects
        )
    }

    @Test
    fun `fast authentication may arrive before auth started event`() {
        val result = RootDecisionCore.reduce(
            RootState.StartingAuth,
            RootEvent.AuthenticatedEvent("user-1")
        )

        assertEquals(
            RootState.PreparingConnection("user-1"),
            result.newState
        )
        assertEquals(
            listOf(RootEffect.StartConnectionEffect("user-1")),
            result.effects
        )
    }

    @Test
    fun `connection page starts only after translation exists`() {
        val result = RootDecisionCore.reduce(
            RootState.PreparingConnection("user-1"),
            RootEvent.ConnectionStartedEvent
        )

        assertEquals(
            RootState.RunningConnection("user-1"),
            result.newState
        )
        assertEquals(
            RootScreen.Connection("user-1"),
            result.newState.screen()
        )
    }

    @Test
    fun `logout keeps connection page until auth session is cleared`() {
        val ending = RootDecisionCore.reduce(
            RootState.RunningConnection("user-1"),
            RootEvent.LogoutRequestedEvent
        )
        assertEquals(
            RootState.EndingConnection("user-1"),
            ending.newState
        )
        assertEquals(
            RootScreen.Connection("user-1"),
            ending.newState.screen()
        )
        assertEquals(emptyList<RootEffect>(), ending.effects)

        val clearing = RootDecisionCore.reduce(
            ending.newState,
            RootEvent.ConnectionStoppedEvent
        )
        assertEquals(
            RootState.ClearingSession("user-1"),
            clearing.newState
        )
        assertEquals(
            RootScreen.Connection("user-1"),
            clearing.newState.screen()
        )
        assertEquals(
            listOf(RootEffect.ClearAuthSessionEffect),
            clearing.effects
        )

        val duplicateStopped = RootDecisionCore.reduce(
            clearing.newState,
            RootEvent.ConnectionStoppedEvent
        )
        assertEquals(clearing.newState, duplicateStopped.newState)
        assertEquals(emptyList<RootEffect>(), duplicateStopped.effects)

        val releasing = RootDecisionCore.reduce(
            clearing.newState,
            RootEvent.AuthSessionClearedEvent
        )
        assertEquals(
            RootState.ReleasingConnection("user-1"),
            releasing.newState
        )
        assertEquals(
            RootScreen.Connection("user-1"),
            releasing.newState.screen()
        )
        assertEquals(
            listOf(RootEffect.ReleaseConnectionEffect),
            releasing.effects
        )

        val finished = RootDecisionCore.reduce(
            releasing.newState,
            RootEvent.ConnectionReleasedEvent
        )
        assertEquals(RootState.Authenticating, finished.newState)
        assertEquals(RootScreen.Auth, finished.newState.screen())
        assertEquals(emptyList<RootEffect>(), finished.effects)
    }
}
