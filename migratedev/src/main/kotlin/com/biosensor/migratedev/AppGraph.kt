package com.biosensor.migratedev

import android.app.Application
import androidx.lifecycle.ViewModelProvider
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
import com.biosensor.migratedev.translation.auth.AuthTranslation
import com.biosensor.migratedev.translation.connection.ConnectionTranslation
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppGraph(application: Application) {
    internal val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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

    val authTranslationFactory: ViewModelProvider.Factory = AuthTranslation.factory(authPort)

    fun connectionTranslationFactory(
        userId: String
    ): ViewModelProvider.Factory = ConnectionTranslation.factory(
        userId = userId, port = connectionPort
    )
}
