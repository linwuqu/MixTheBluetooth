package com.biosensor.migratedev

import android.app.Application
import com.biosensor.migratedev.decisioncore.auth.AuthDecisionCore
import com.biosensor.migratedev.decisioncore.auth.AuthState
import com.biosensor.migratedev.orchestrator.WorkflowOrchestrator
import com.biosensor.migratedev.orchestrator.auth.AuthEffectExecutor
import com.biosensor.migratedev.port.adapter.localport.EncryptedSessionStore
import com.biosensor.migratedev.port.adapter.localport.PersistenceLocalPort
import com.biosensor.migratedev.port.adapter.remoteport.RetrofitRemotePort
import com.biosensor.migratedev.port.auth.AuthPort
import com.biosensor.migratedev.port.auth.DefaultAuthPort
import com.biosensor.migratedev.translation.auth.AuthTranslation
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppGraph(application: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val clock = Clock.systemUTC()
    private val sessionStore = EncryptedSessionStore(application)
    private val persistenceLocalPort = PersistenceLocalPort(sessionStore, clock)
    private val accountApi = RetrofitRemotePort.createAccountApi(BuildConfig.API_BASE_URL)
    private val authRemotePort = RetrofitRemotePort.Auth(accountApi, clock)
    private val authPort: AuthPort = DefaultAuthPort(persistenceLocalPort, authRemotePort)
    private val authOrchestrator = WorkflowOrchestrator(
        initialState = AuthState.Idle,
        decisionCore = AuthDecisionCore,
        effectExecutor = AuthEffectExecutor(authPort),
        scope = appScope
    )

    val authTranslation = AuthTranslation(authOrchestrator, appScope)
}
