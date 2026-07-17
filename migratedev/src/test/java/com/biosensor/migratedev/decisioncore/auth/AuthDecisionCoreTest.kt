package com.biosensor.migratedev.decisioncore.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthDecisionCoreTest {

    private val session = AuthSession(
        user = User(
            id = "user-1",
            userName = "alice",
            telephone = "13800000000"
        ),
        token = "token-1"
    )

    @Test
    fun acceptedLoginPersistsSessionBeforeAuthentication() {
        val loading = AuthDecisionCore.reduce(
            AuthState.Idle,
            AuthEvent.SubmitLogin("alice", "password")
        )
        assertEquals(AuthState.Loading, loading.newState)
        assertEquals(
            listOf(AuthEffect.LoginRemote("alice", "password")),
            loading.effects
        )

        val saving = AuthDecisionCore.reduce(
            loading.newState,
            AuthEvent.RemoteAccepted(session)
        )
        assertEquals(AuthState.SavingSession(session), saving.newState)
        assertEquals(listOf(AuthEffect.SaveSession(session)), saving.effects)

        val authenticated = AuthDecisionCore.reduce(
            saving.newState,
            AuthEvent.SessionSaved
        )
        assertEquals(AuthState.Authenticated(session), authenticated.newState)
    }

    @Test
    fun rejectedLoginReturnsToErrorWithoutAnEffect() {
        val result = AuthDecisionCore.reduce(
            AuthState.Loading,
            AuthEvent.RemoteRejected("invalid credentials")
        )

        assertEquals(AuthState.Error("invalid credentials"), result.newState)
        assertEquals(emptyList<AuthEffect>(), result.effects)
    }

    @Test
    fun logoutClearsTheSessionAndReturnsToIdle() {
        val result = AuthDecisionCore.reduce(
            AuthState.Authenticated(session),
            AuthEvent.Logout
        )

        assertEquals(AuthState.Idle, result.newState)
        assertEquals(listOf(AuthEffect.ClearSession), result.effects)
    }
}
