package com.biosensor.scanner.internal

import com.biosensor.scanner.api.ScanResult

/**
 * 一枚码的身份:同一枚码在连续帧里解出来的结果应当算同一枚。
 *
 * 不用 [ScanResult.bounds] 参与比较——手抖一下四角坐标就变了,拿它当身份会让确认帧数永远攒不够。
 * 也不用 `timestampMillis` / `decodeTimeMillis`:那是每次解码都变的。
 */
internal data class ResultKey(
    val formatName: String,
    val text: String?,
    val bytes: List<Byte>,
) {
    companion object {
        fun from(result: ScanResult): ResultKey = ResultKey(
            formatName = result.format.name,
            text = result.text,
            bytes = result.rawBytes?.toList().orEmpty(),
        )
    }
}
