package com.biosensor.migratedev.port.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.port.adapter.localport.AndroidLocalPort
import com.biosensor.migratedev.port.adapter.remoteport.HttpOutcome
import com.biosensor.migratedev.port.adapter.remoteport.HttpRemote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthPortAdapterInstrumentedTest {

    @Test
    fun sessionRoundTripUsesTinkAndAndroidKeystore() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val local = AndroidLocalPort.create(context, scope)
                val port = AuthPortAdapter(
                    kv = local.entropy,
                    http = NoopHttpRemote,
                    clock = java.time.Clock.systemUTC()
                )
                val session = AuthSession(
                    User("instrumented-user", "instrumented", "13800000000"),
                    "instrumented-token",
                    9_999_999_999L
                )

                assertEquals(
                    AuthResult.Local.SessionCleared,
                    port.execute(AuthCommand.Local.ClearSession).first()
                )
                assertEquals(
                    AuthResult.Local.SessionSaved,
                    port.execute(AuthCommand.Local.SaveSession(session)).first()
                )
                assertEquals(
                    AuthResult.Local.SessionFound(session),
                    port.execute(AuthCommand.Local.ReadSession).first()
                )
                assertEquals(
                    AuthResult.Local.SessionCleared,
                    port.execute(AuthCommand.Local.ClearSession).first()
                )
            } finally {
                scope.cancel()
            }
        }

    private object NoopHttpRemote : HttpRemote {
        override fun <T> api(apiClass: Class<T>): T = error("仪器测试不会使用远端")
        override suspend fun <T> invoke(block: suspend () -> T): HttpOutcome<T> = error("仪器测试不会使用远端")
    }
}
