package com.biosensor.scanner.api

interface ScannerListener {
    fun onScannerReady(hasFlash: Boolean) = Unit

    fun onScanResult(result: ScanResult)

    fun onScannerError(error: ScannerError) = Unit
}
