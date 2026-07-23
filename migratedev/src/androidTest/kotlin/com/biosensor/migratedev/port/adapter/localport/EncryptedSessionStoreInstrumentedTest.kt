package com.biosensor.migratedev.port.adapter.localport

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.decisioncore.auth.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedSessionStoreInstrumentedTest {

    @Test
    fun sessionRoundTripUsesAndroidKeystore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("auth_session_secure", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val session = AuthSession(
            User("instrumented-user", "instrumented", "13800000000"),
            "instrumented-token",
            9_999_999_999L
        )
        val store = EncryptedSessionStore(context)

        assertTrue(store.save(session))
        assertEquals(SessionRead.Found(session), store.read())
        assertEquals(SessionRead.Found(session), EncryptedSessionStore(context).read())
        assertTrue(store.clear())
    }
}
