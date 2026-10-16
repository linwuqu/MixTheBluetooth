package com.biosensor.scanner.api

import androidx.lifecycle.LifecycleOwner

interface ScannerController {
    var config: ScannerConfig
    var scannerListener: ScannerListener?

    fun bindToLifecycle(lifecycleOwner: LifecycleOwner)

    fun stop()

    fun setTorch(enabled: Boolean)

    fun release()
}
