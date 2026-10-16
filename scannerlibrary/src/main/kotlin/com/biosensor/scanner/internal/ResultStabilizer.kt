package com.biosensor.scanner.internal

import com.biosensor.scanner.api.ScanResult

internal class ResultStabilizer(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private var pendingKey: ResultKey? = null
    private var pendingCount = 0
    private var lastEmittedKey: ResultKey? = null
    private var lastEmittedAt = Long.MIN_VALUE

    fun accept(
        result: ScanResult?,
        requiredFrames: Int,
        duplicateCooldownMillis: Long,
    ): ScanResult? {
        if (result == null) {
            pendingKey = null
            pendingCount = 0
            return null
        }

        val key = ResultKey.from(result)
        if (key == pendingKey) {
            pendingCount += 1
        } else {
            pendingKey = key
            pendingCount = 1
        }

        if (pendingCount < requiredFrames) return null

        val now = nowMillis()
        val sameAsLast = key == lastEmittedKey
        val stillCoolingDown = sameAsLast && elapsed(now, lastEmittedAt) < duplicateCooldownMillis
        if (stillCoolingDown) return null

        lastEmittedKey = key
        lastEmittedAt = now
        return result
    }

    fun reset() {
        pendingKey = null
        pendingCount = 0
        lastEmittedKey = null
        lastEmittedAt = Long.MIN_VALUE
    }

    private fun elapsed(now: Long, then: Long): Long =
        if (then == Long.MIN_VALUE) Long.MAX_VALUE else (now - then).coerceAtLeast(0L)

    private data class ResultKey(
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
}
