package com.biosensor.migratedev.decisioncore.cgm

/** END 到达后检查整次回放。例子见 docs/SOP/Workflow/03-缓存明文流.md §3.2。 */
object CacheValidators {
    fun validate(records: List<CgmRecord>): ValidationConclusion {
        val playback = CacheBoundaryTrimmer.trim(records)
        if (playback.isEmpty()) {
            val reason = "缺少 Start Playback / Playback all done 边界"
            return ValidationConclusion(false, reason, checks = listOf(ValidationCheck("回放边界", false, reason)))
        }
        val data = CacheRecordDeduper.dedupe(playback)
        val duplicateCount = playback.count { it !is CgmRecord.Marker } - data.size
        val checks = listOf(
            ValidationCheck("回放边界", true, "START 和 END 齐全，边界外忽略 ${records.size - playback.size} 条记录"),
            ValidationCheck("接收重发", true, "合并 $duplicateCount 条相邻重复记录，边界标记不写入文件")
        )
        val result = CacheStructureValidator.validate(data)
        return ValidationConclusion(result.ok, result.message, if (result.ok) data else emptyList(), checks + result.checks)
    }
}

data class ValidationCheck(val name: String, val passed: Boolean, val detail: String)

data class ValidationConclusion(
    val complete: Boolean, val reason: String? = null, val records: List<CgmRecord> = emptyList(),
    val checks: List<ValidationCheck> = emptyList()
)

/** 例：杂散 EIS → START → 本次数据 → END → 下次数据。只取 START 到首个 END。 */
object CacheBoundaryTrimmer {
    fun trim(records: List<CgmRecord>): List<CgmRecord> {
        val start = records.indexOfFirst { it is CgmRecord.Marker && it.kind == MarkerKind.START }
        val end = records.indexOfFirst { it is CgmRecord.Marker && it.kind == MarkerKind.END }
        if (start < 0 || end < start) return emptyList()
        return records.subList(start, end + 1)
    }
}

/** 例：EIS:1 原文连续收到两次，只留一次；不同 LOG 段中的相同点仍保留。 */
object CacheRecordDeduper {
    fun dedupe(records: List<CgmRecord>): List<CgmRecord> {
        val result = mutableListOf<CgmRecord>()
        for (record in records) {
            if (record is CgmRecord.Marker) continue
            if (record.text != result.lastOrNull()?.text) result += record
        }
        return result
    }
}

/**
 * LOG:1,1,95,... 后必须有 95 个 EIS 点，seq 按接收顺序为 1..95，ts 从 60 起每点加 10。CA 只查前两项。
 *
 * 逐段检查但只报一条结论:段数多时逐段上报会把界面刷满,而读者只需要"过没过、哪段没过"。
 */
object CacheStructureValidator {
    data class StructureResult(
        val ok: Boolean, val message: String? = null, val checks: List<ValidationCheck> = emptyList()
    )

    fun validate(records: List<CgmRecord>): StructureResult {
        var declaration: SegmentDeclaration? = null
        val points = mutableListOf<CgmRecord>()
        var segments = 0
        var pointTotal = 0

        /** 结算上一段。返回失败原因；null 表示通过。 */
        fun closeSegment(): String? {
            val segment = declaration ?: return null
            val reason = segmentFailure(segment, points)
            if (reason == null) {
                segments += 1
                pointTotal += points.size
            }
            return reason
        }

        for (record in records) {
            when (record) {
                is CgmRecord.Log -> {
                    closeSegment()?.let { return structureFailure(it) }
                    val parsed = parseDeclaration(record.text)
                        ?: return structureFailure("LOG 段声明格式异常: ${record.text}")
                    if (parsed.type != 1 && parsed.type != 2) {
                        return structureFailure("LOG:${parsed.number} 未知段类型: ${parsed.type}")
                    }
                    declaration = parsed
                    points.clear()
                }
                is CgmRecord.Eis, is CgmRecord.Ca -> {
                    if (declaration == null) {
                        return structureFailure("数据行前无 LOG 段声明: ${record.text}")
                    }
                    points += record
                }
                is CgmRecord.Summarize, is CgmRecord.Marker -> Unit
            }
        }
        // 新 LOG 结束上一段；回放结尾结束最后一段。没有 LOG 和数据点的空回放合法。
        closeSegment()?.let { return structureFailure(it) }

        val detail = if (segments == 0) "空缓存，0 个数据点"
        else "$segments 段 · $pointTotal 点 · seq 与 ts 连续"
        return StructureResult(true, checks = listOf(ValidationCheck("缓存结构", true, detail)))
    }

    /** 返回本段第一处不合格的原因；null 表示本段通过。 */
    private fun segmentFailure(
        declaration: SegmentDeclaration, points: List<CgmRecord>
    ): String? {
        val label = "LOG:${declaration.number} ${if (declaration.type == 1) "EIS" else "CA"} 段"
        if (points.size != declaration.pointCount) {
            return "$label 声明 ${declaration.pointCount} 点，实际 ${points.size} 点"
        }
        val wrongType = points.firstOrNull {
            if (declaration.type == 1) it !is CgmRecord.Eis else it !is CgmRecord.Ca
        }
        if (wrongType != null) return "$label 数据点类型错误: ${wrongType.text}"

        for ((index, point) in points.withIndex()) {
            val seq = if (point is CgmRecord.Eis) point.seq else (point as CgmRecord.Ca).seq
            val expected = index + 1
            if (seq != expected) return "${label}第 $expected 点 seq 应为 $expected，实际 $seq"
        }
        if (declaration.type == 1) {
            for ((index, point) in points.withIndex()) {
                val eis = point as CgmRecord.Eis
                val expectedTs = 60 + index * 10
                if (eis.ts != expectedTs) {
                    return "$label seq=${eis.seq} 的 ts 应为 $expectedTs，实际 ${eis.ts}"
                }
            }
        }
        return null
    }

    private fun structureFailure(reason: String): StructureResult =
        StructureResult(false, reason, listOf(ValidationCheck("缓存结构", false, reason)))

    private fun parseDeclaration(text: String): SegmentDeclaration? {
        val match = LOG_PATTERN.matchEntire(text) ?: return null
        val type = match.groupValues[2].toIntOrNull() ?: return null
        val count = match.groupValues[3].toIntOrNull() ?: return null
        return SegmentDeclaration(match.groupValues[1], type, count)
    }

    private data class SegmentDeclaration(val number: String, val type: Int, val pointCount: Int)
    private val LOG_PATTERN = Regex("""LOG:(\d+),(\d+),(\d+),.*""")
}
