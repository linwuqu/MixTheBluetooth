package com.biosensor.scanner.internal

import androidx.camera.core.ImageProxy
import com.biosensor.scanner.api.ScanResult
import com.biosensor.scanner.api.ScannerConfig

internal interface BarcodeEngine {
    fun decode(image: ImageProxy, config: ScannerConfig): List<ScanResult>
}
