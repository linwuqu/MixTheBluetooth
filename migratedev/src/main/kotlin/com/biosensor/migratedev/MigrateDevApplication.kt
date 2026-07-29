package com.biosensor.migratedev

import android.app.Application
import com.biosensor.migratedev.logging.LoggingInitializer

class MigrateDevApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = AppGraph(this)
        LoggingInitializer.install(
            debug = BuildConfig.DEBUG,
            files = appGraph.localPort.files,
            applicationScope = appGraph.applicationScope
        )
    }
}
