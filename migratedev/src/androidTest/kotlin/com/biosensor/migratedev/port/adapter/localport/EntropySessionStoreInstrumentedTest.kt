package com.biosensor.migratedev.port.adapter.localport

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.decisioncore.auth.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntropySessionStoreInstrumentedTest {

    @Test
    fun sessionRoundTripUsesTinkAndAndroidKeystore() =
        runBlocking {
            val context =
                ApplicationProvider.getApplicationContext<Context>()
            val scope = CoroutineScope(
                SupervisorJob() + Dispatchers.IO
            )
            try {
                val local = AndroidLocalPort.create(
                    context,
                    scope
                )
                val store = EntropySessionStore(local.entropy)
                val session = AuthSession(
                    User(
                        "instrumented-user",
                        "instrumented",
                        "13800000000"
                    ),
                    "instrumented-token",
                    9_999_999_999L
                )

                assertTrue(store.clear())
                assertTrue(store.save(session))
                assertEquals(
                    SessionRead.Found(session),
                    store.read()
                )
                assertTrue(store.clear())
            } finally {
                scope.cancel()
            }
        }
}
