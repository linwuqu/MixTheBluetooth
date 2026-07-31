package com.biosensor.migratedev

import android.app.Application
import com.biosensor.migratedev.orchestrator.root.RootWorkflow
import com.biosensor.migratedev.port.adapter.bluetoothport.AndroidBluetoothPort
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothPort
import com.biosensor.migratedev.port.adapter.localport.AndroidLocalPort
import com.biosensor.migratedev.port.adapter.localport.EntropySessionStore
import com.biosensor.migratedev.port.adapter.localport.LocalPort
import com.biosensor.migratedev.port.adapter.localport.PersistenceLocalPort
import com.biosensor.migratedev.port.adapter.remoteport.RetrofitRemotePort
import com.biosensor.migratedev.port.auth.AuthPort
import com.biosensor.migratedev.port.auth.DefaultAuthPort
import com.biosensor.migratedev.port.connection.ConnectionPort
import com.biosensor.migratedev.port.connection.DefaultConnectionPort
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 这里进行模块装载
 * TODO: 还需要进一步优化这里的模块设计将初始化过程变得更加清晰
 */
class AppGraph(application: Application) {
    internal val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val rootScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val clock = Clock.systemUTC()

    internal val localPort: LocalPort = AndroidLocalPort.create(application, applicationScope)

    private val persistenceLocalPort = PersistenceLocalPort(
        sessionStore = EntropySessionStore(localPort.entropy), clock = clock
    )
    private val accountApi = RetrofitRemotePort.createAccountApi(
        BuildConfig.API_BASE_URL
    )
    private val remoteAuth = RetrofitRemotePort.Auth(accountApi, clock)
    private val authPort: AuthPort = DefaultAuthPort(persistenceLocalPort, remoteAuth)
    private val bluetoothPort: BluetoothPort = AndroidBluetoothPort(
        application = application, scope = bluetoothScope
    )
    private val connectionPort: ConnectionPort = DefaultConnectionPort(localPort, bluetoothPort)

    val rootWorkflow = RootWorkflow(
        authPort = authPort,
        connectionPort = connectionPort,
        scope = rootScope
    )
}
