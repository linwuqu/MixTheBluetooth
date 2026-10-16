package com.biosensor.scanner.api

/** Barcode symbologies exposed by scannerlibrary without leaking the decoder SDK. */
enum class BarcodeFormat {
    QR_CODE,
    MICRO_QR,
    RMQR,
    DATA_MATRIX,
    AZTEC,
    PDF_417,
    CODE_128,
    CODE_93,
    CODE_39,
    CODABAR,
    ITF,
    EAN_13,
    EAN_8,
    UPC_A,
    UPC_E,
    DATA_BAR,
    DATA_BAR_EXPANDED,
    MAXI_CODE,
    UNKNOWN,
}
