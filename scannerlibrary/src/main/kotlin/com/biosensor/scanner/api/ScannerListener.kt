package com.biosensor.scanner.api

interface ScannerListener {
    fun onScannerReady(hasFlash: Boolean) = Unit

    fun onScanResult(result: ScanResult)

    /**
     * 用户点了画面里某枚码的框。仅在 `ScannerConfig.tapToSelect` 打开时会回调。
     *
     * 与 [onScanResult] 是两条独立的通道:自动确认(画面里只有一枚)走 [onScanResult],
     * 手指点选走这里。消费方通常把两者接到同一个处理函数上。
     */
    fun onCodeSelected(result: ScanResult) = Unit

    fun onScannerError(error: ScannerError) = Unit
}
