package com.biosensor.scanner.api

enum class ScannerErrorCode {
    NO_CAMERA,
    CAMERA_BIND_FAILED,
    DECODER_FAILED,
    TORCH_UNAVAILABLE,
}

data class ScannerError(
    val code: ScannerErrorCode,
    val message: String,
    val cause: Throwable? = null,
)
