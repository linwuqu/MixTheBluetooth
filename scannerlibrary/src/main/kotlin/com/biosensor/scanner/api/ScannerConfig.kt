package com.biosensor.scanner.api

import android.util.Size

data class ScannerConfig(
    val formats: Set<BarcodeFormat> = ScannerProfile.COMMON.formats,
    val tryHarder: Boolean = true,
    val tryRotate: Boolean = true,
    val tryInvert: Boolean = true,
    val tryDownscale: Boolean = true,
    val tryDenoise: Boolean = false,
    val confirmationFrames: Int = 2,
    val duplicateCooldownMillis: Long = 1_500L,
    val maxSymbolsPerFrame: Int = 1,
    val targetResolution: Size = Size(1920, 1080),
) {
    init {
        require(formats.isNotEmpty()) { "At least one barcode format is required" }
        require(confirmationFrames > 0) { "confirmationFrames must be positive" }
        require(duplicateCooldownMillis >= 0) { "duplicateCooldownMillis cannot be negative" }
        require(maxSymbolsPerFrame > 0) { "maxSymbolsPerFrame must be positive" }
        require(targetResolution.width > 0 && targetResolution.height > 0) {
            "targetResolution must be positive"
        }
    }

    companion object {
        fun forProfile(profile: ScannerProfile): ScannerConfig = ScannerConfig(formats = profile.formats)
    }
}
