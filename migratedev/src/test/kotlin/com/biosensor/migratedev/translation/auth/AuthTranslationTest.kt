package com.biosensor.migratedev.translation.auth

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.biosensor.migratedev.port.auth.AuthCommand
import com.biosensor.migratedev.port.auth.AuthPort
import com.biosensor.migratedev.port.auth.AuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthTranslationTest {

    @Test
    fun `creation enters session restoration once`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val translation = ViewModelProvider(
                store,
                AuthTranslation.factory(EmptyAuthPort)
            )[AuthTranslation::class.java]

            advanceUntilIdle()

            assertEquals(
                AuthUiState.RestoringSession,
                translation.uiState.value
            )
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private object EmptyAuthPort : AuthPort {
        override fun execute(
            command: AuthCommand
        ): Flow<AuthResult> = emptyFlow()
    }
}
