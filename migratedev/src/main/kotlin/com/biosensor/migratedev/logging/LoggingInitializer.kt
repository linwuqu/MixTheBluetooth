package com.biosensor.migratedev.logging

import com.biosensor.migratedev.port.adapter.localport.LocalFileClient
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

object LoggingInitializer {
    fun install(
        debug: Boolean, files: LocalFileClient, applicationScope: CoroutineScope
    ) {
        Timber.uprootAll()
        if (debug) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(
                ReleaseTree(
                    files = files, scope = applicationScope
                )
            )
        }
    }
}
