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
            AuthEvent.SubmitLogin("13800000000", "password")
        )
        assertEquals(AuthState.Loading, loading.newState)
        assertEquals(
            listOf(AuthEffect.LoginRemote(phone = "13800000000", password = "password")),
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

    @Test
    fun appStartedReadsAndValidatesAnExistingSession() {
        val restoring = AuthDecisionCore.reduce(
            AuthState.Idle,
            AuthEvent.AuthCreated
        )
        assertEquals(AuthState.RestoringSession, restoring.newState)
        assertEquals(listOf(AuthEffect.ReadSession), restoring.effects)

        val validating = AuthDecisionCore.reduce(
            restoring.newState,
            AuthEvent.SessionFound(session)
        )
        assertEquals(AuthState.Loading, validating.newState)
        assertEquals(listOf(AuthEffect.ValidateSession(session)), validating.effects)

        val authenticated = AuthDecisionCore.reduce(
            validating.newState,
            AuthEvent.SessionVerified(session)
        )
        assertEquals(AuthState.Authenticated(session), authenticated.newState)
    }

    @Test
    fun missingSessionReturnsToIdle() {
        val result = AuthDecisionCore.reduce(
            AuthState.RestoringSession,
            AuthEvent.SessionMissing
        )

        assertEquals(AuthState.Idle, result.newState)
        assertEquals(emptyList<AuthEffect>(), result.effects)
    }

    @Test
    fun expiredOrRejectedSessionIsCleared() {
        val expired = AuthDecisionCore.reduce(
            AuthState.RestoringSession,
            AuthEvent.SessionExpired
        )
        assertEquals(AuthState.Idle, expired.newState)
        assertEquals(listOf(AuthEffect.ClearSession), expired.effects)

        val rejected = AuthDecisionCore.reduce(
            AuthState.Loading,
            AuthEvent.SessionRejected("token expired")
        )
        assertEquals(AuthState.Idle, rejected.newState)
        assertEquals(listOf(AuthEffect.ClearSession), rejected.effects)
    }

    @Test
    fun registrationSuccessReturnsToLoginForm() {
        val result = AuthDecisionCore.reduce(
            AuthState.Loading,
            AuthEvent.RegistrationAccepted
        )

        assertEquals(AuthState.Registered("注册成功，请使用新账号登录"), result.newState)
        assertEquals(emptyList<AuthEffect>(), result.effects)
    }

    @Test
    fun registrationKeepsPhoneAndNicknameInTheirOwnFields() {
        val result = AuthDecisionCore.reduce(
            AuthState.Idle,
            AuthEvent.SubmitRegister(
                phone = "13800000000",
                password = "password",
                nickname = "alice"
            )
        )

        assertEquals(
            listOf(
                AuthEffect.RegisterRemote(
                    phone = "13800000000",
                    password = "password",
                    nickname = "alice"
                )
            ),
            result.effects
        )
    }
}
