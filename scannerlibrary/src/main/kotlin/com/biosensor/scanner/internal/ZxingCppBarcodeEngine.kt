package com.biosensor.scanner.internal

import androidx.camera.core.ImageProxy
import com.biosensor.scanner.api.BarcodeFormat
import com.biosensor.scanner.api.ScanBounds
import com.biosensor.scanner.api.ScanContentType
import com.biosensor.scanner.api.ScanPoint
import com.biosensor.scanner.api.ScanResult
import com.biosensor.scanner.api.ScannerConfig
import zxingcpp.BarcodeReader

@Suppress("DEPRECATION")
internal class ZxingCppBarcodeEngine : BarcodeEngine {
    private val reader: BarcodeReader by lazy { BarcodeReader() }

    override fun decode(image: ImageProxy, config: ScannerConfig): List<ScanResult> {
        reader.options = BarcodeReader.Options(
            formats = config.formats.mapTo(mutableSetOf(), ::toZxingFormat),
            tryHarder = config.tryHarder,
            tryRotate = config.tryRotate,
            tryInvert = config.tryInvert,
            tryDownscale = config.tryDownscale,
            tryDenoise = config.tryDenoise,
            maxNumberOfSymbols = config.maxSymbolsPerFrame,
            textMode = BarcodeReader.TextMode.PLAIN,
        )

        val results = reader.read(image)
        val decodeTime = reader.lastReadTime
        val timestamp = System.currentTimeMillis()
        return results.map { result ->
            ScanResult(
                text = result.text,
                rawBytes = result.bytes,
                format = fromZxingFormat(result.format),
                contentType = when (result.contentType) {
                    BarcodeReader.ContentType.TEXT -> ScanContentType.TEXT
                    BarcodeReader.ContentType.BINARY -> ScanContentType.BINARY
                    BarcodeReader.ContentType.MIXED -> ScanContentType.MIXED
                    BarcodeReader.ContentType.GS1 -> ScanContentType.GS1
                    BarcodeReader.ContentType.ISO15434 -> ScanContentType.ISO_15434
                    BarcodeReader.ContentType.UNKNOWN_ECI -> ScanContentType.UNKNOWN_ECI
                },
                bounds = ScanBounds(
                    topLeft = result.position.topLeft.toScanPoint(),
                    topRight = result.position.topRight.toScanPoint(),
                    bottomRight = result.position.bottomRight.toScanPoint(),
                    bottomLeft = result.position.bottomLeft.toScanPoint(),
                ),
                // zxing-cpp 3.1.1 calls this orientation; 3.2 renames it to rotation.
                rotationDegrees = result.orientation,
                errorCorrectionLevel = result.ecLevel,
                symbologyIdentifier = result.symbologyIdentifier,
                decodeTimeMillis = decodeTime,
                timestampMillis = timestamp,
            )
        }
    }

    private fun android.graphics.Point.toScanPoint() = ScanPoint(x, y)

    private fun toZxingFormat(format: BarcodeFormat): BarcodeReader.Format = when (format) {
        BarcodeFormat.QR_CODE -> BarcodeReader.Format.QR_CODE
        BarcodeFormat.MICRO_QR -> BarcodeReader.Format.MICRO_QR_CODE
        BarcodeFormat.RMQR -> BarcodeReader.Format.RMQR_CODE
        BarcodeFormat.DATA_MATRIX -> BarcodeReader.Format.DATA_MATRIX
        BarcodeFormat.AZTEC -> BarcodeReader.Format.AZTEC
        BarcodeFormat.PDF_417 -> BarcodeReader.Format.PDF_417
        BarcodeFormat.CODE_128 -> BarcodeReader.Format.CODE_128
        BarcodeFormat.CODE_93 -> BarcodeReader.Format.CODE_93
        BarcodeFormat.CODE_39 -> BarcodeReader.Format.CODE_39
        BarcodeFormat.CODABAR -> BarcodeReader.Format.CODABAR
        BarcodeFormat.ITF -> BarcodeReader.Format.ITF
        BarcodeFormat.EAN_13 -> BarcodeReader.Format.EAN_13
        BarcodeFormat.EAN_8 -> BarcodeReader.Format.EAN_8
        BarcodeFormat.UPC_A -> BarcodeReader.Format.UPC_A
        BarcodeFormat.UPC_E -> BarcodeReader.Format.UPC_E
        BarcodeFormat.DATA_BAR -> BarcodeReader.Format.DATA_BAR
        BarcodeFormat.DATA_BAR_EXPANDED -> BarcodeReader.Format.DATA_BAR_EXP
        BarcodeFormat.MAXI_CODE -> BarcodeReader.Format.MAXI_CODE
        BarcodeFormat.UNKNOWN -> BarcodeReader.Format.NONE
    }

    private fun fromZxingFormat(format: BarcodeReader.Format): BarcodeFormat = when (format) {
        BarcodeReader.Format.QR_CODE,
        BarcodeReader.Format.QR_CODE_MODEL_1,
        BarcodeReader.Format.QR_CODE_MODEL_2 -> BarcodeFormat.QR_CODE
        BarcodeReader.Format.MICRO_QR_CODE -> BarcodeFormat.MICRO_QR
        BarcodeReader.Format.RMQR_CODE -> BarcodeFormat.RMQR
        BarcodeReader.Format.DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
        BarcodeReader.Format.AZTEC,
        BarcodeReader.Format.AZTEC_CODE,
        BarcodeReader.Format.AZTEC_RUNE -> BarcodeFormat.AZTEC
        BarcodeReader.Format.PDF_417,
        BarcodeReader.Format.COMPACT_PDF_417,
        BarcodeReader.Format.MICRO_PDF_417 -> BarcodeFormat.PDF_417
        BarcodeReader.Format.CODE_128 -> BarcodeFormat.CODE_128
        BarcodeReader.Format.CODE_93 -> BarcodeFormat.CODE_93
        BarcodeReader.Format.CODE_39,
        BarcodeReader.Format.CODE_39_STD,
        BarcodeReader.Format.CODE_39_EXT,
        BarcodeReader.Format.CODE_32,
        BarcodeReader.Format.PZN -> BarcodeFormat.CODE_39
        BarcodeReader.Format.CODABAR -> BarcodeFormat.CODABAR
        BarcodeReader.Format.ITF,
        BarcodeReader.Format.ITF_14 -> BarcodeFormat.ITF
        BarcodeReader.Format.EAN_13 -> BarcodeFormat.EAN_13
        BarcodeReader.Format.EAN_8 -> BarcodeFormat.EAN_8
        BarcodeReader.Format.UPC_A -> BarcodeFormat.UPC_A
        BarcodeReader.Format.UPC_E -> BarcodeFormat.UPC_E
        BarcodeReader.Format.DATA_BAR,
        BarcodeReader.Format.DATA_BAR_OMNI,
        BarcodeReader.Format.DATA_BAR_STK,
        BarcodeReader.Format.DATA_BAR_STK_OMNI,
        BarcodeReader.Format.DATA_BAR_LTD -> BarcodeFormat.DATA_BAR
        BarcodeReader.Format.DATA_BAR_EXP,
        BarcodeReader.Format.DATA_BAR_EXP_STK -> BarcodeFormat.DATA_BAR_EXPANDED
        BarcodeReader.Format.MAXI_CODE -> BarcodeFormat.MAXI_CODE
        else -> BarcodeFormat.UNKNOWN
    }
}
