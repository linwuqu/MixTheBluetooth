package com.biosensor.scanner.api

enum class ScannerProfile(val formats: Set<BarcodeFormat>) {
    QR_ONLY(
        setOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.MICRO_QR,
            BarcodeFormat.RMQR,
        ),
    ),
    COMMON(
        setOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.MICRO_QR,
            BarcodeFormat.RMQR,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.CODE_128,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
        ),
    ),
    MEDICAL(
        setOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.MICRO_QR,
            BarcodeFormat.RMQR,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.DATA_BAR,
            BarcodeFormat.DATA_BAR_EXPANDED,
        ),
    ),
    RETAIL(
        setOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.DATA_BAR,
            BarcodeFormat.DATA_BAR_EXPANDED,
        ),
    ),
    ALL(BarcodeFormat.entries.filterNot { it == BarcodeFormat.UNKNOWN }.toSet()),
}
