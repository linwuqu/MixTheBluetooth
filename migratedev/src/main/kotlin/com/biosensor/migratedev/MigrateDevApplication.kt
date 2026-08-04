package com.biosensor.migratedev

import android.app.Application
import com.biosensor.migratedev.logging.LoggingInitializer

/**
 * Android 项目从 Application 开始
 * 这里实例化了 appGraph
 * 同时重写了 onCreate 方法
 */
class MigrateDevApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = AppGraph(this)
        LoggingInitializer.install(
            debug = BuildConfig.DEBUG,
            files = appGraph.files,
            applicationScope = appGraph.applicationScope
        )
    }
}
