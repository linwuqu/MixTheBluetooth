package com.biosensor.scanner.api

data class ScanPoint(val x: Int, val y: Int)

data class ScanBounds(
    val topLeft: ScanPoint,
    val topRight: ScanPoint,
    val bottomRight: ScanPoint,
    val bottomLeft: ScanPoint,
)

enum class ScanContentType {
    TEXT,
    BINARY,
    MIXED,
    GS1,
    ISO_15434,
    UNKNOWN_ECI,
    UNKNOWN,
}

data class ScanResult(
    val text: String?,
    val rawBytes: ByteArray?,
    val format: BarcodeFormat,
    val contentType: ScanContentType,
    val bounds: ScanBounds,
    val rotationDegrees: Int,
    val errorCorrectionLevel: String?,
    val symbologyIdentifier: String?,
    val decodeTimeMillis: Int,
    val timestampMillis: Long,
)
