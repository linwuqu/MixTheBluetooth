package com.biosensor.migratedev.decisioncore.cgm

/** 校验链:边界规整 → marker → 去重 → 结构。全部纯函数,由 CgmReadDecision.onRecords 在 END 到达时调用。 */
object CacheValidators {
    fun validate(records: List<CgmRecord>): ValidationConclusion {
        // ① 边界规整:真实设备在首个 Start Playback 前可能残留杂散行(实测样本首行 EIS:2,70),
        //    只取首个 START 到末个 END 之间的记录,保证后续校验看的就是一次完整回放
        val bounded = CacheBoundaryTrimmer.trim(records)
        if (bounded.isEmpty()) return ValidationConclusion(false, "缺少 Start Playback / Playback all done 边界")
        // ② marker 校验:必须在去重之前(去重会删 marker,见下)
        val marker = CacheMarkerValidator.validate(bounded)
        if (!marker.ok) return ValidationConclusion(false, marker.message)
        // ③ 去重:数据行内容全等(原始行文本)保序保留首条;marker 全部剔除(边界已由 render 补齐)
        val deduped = CacheRecordDeduper.dedupe(bounded)
        // ④ 结构校验:按 LOG 段声明骨架逐段验证(实测:每段 seq 从 1 连续、点数与声明一致)
        val structure = CacheStructureValidator.validate(deduped)
        if (!structure.ok) return ValidationConclusion(false, structure.message)
        return ValidationConclusion(true, null, deduped)
    }
}

data class ValidationConclusion(
    val complete: Boolean, val reason: String? = null, val records: List<CgmRecord> = emptyList()
)

/**
 * 边界规整:首个 START 到**首个** END 之间的记录。
 * 语义与状态机对齐(§5 onRecords):首个 END 到达即触发校验,此时 accumulated 只含第一份完整推送;
 * 之后的重复推送/推间杂散(实测 07-21 每份推送后有散落 EIS 行)在 Completed 后被幂等忽略,不入校验。
 * 同时兜底 reduce 未过滤的杂散行(实测 07-15/07-20 首个 START 前有 `EIS:2,70`)。
 */
object CacheBoundaryTrimmer {
    fun trim(records: List<CgmRecord>): List<CgmRecord> {
        val startIdx = records.indexOfFirst {
            it is CgmRecord.Marker && it.kind == MarkerKind.START
        }
        val endIdx = records.indexOfFirst {
            it is CgmRecord.Marker && it.kind == MarkerKind.END
        }
        if (startIdx < 0 || endIdx < 0 || startIdx > endIdx) return emptyList()
        return records.subList(startIdx, endIdx + 1)
    }
}

/**
 * 去重:只删**相邻**重复行(蓝牙字节块重发会在相邻位置产生相同记录),保序保留首条;
 * 跨段相同文本(实测:不同快照中某点的值可能恰巧相同,如 EIS:18 同时出现在两个段)是合法数据,不能删;
 * marker 剔除(边界由 render 补齐,不参与去重判据)。
 */
object CacheRecordDeduper {
    fun dedupe(records: List<CgmRecord>): List<CgmRecord> {
        val data = records.filter { it !is CgmRecord.Marker }
        if (data.isEmpty()) return data
        val result = mutableListOf(data.first())
        for (record in data.drop(1)) {
            if (record.text != result.last().text) result += record
        }
        return result
    }
}

/** marker 校验:边界成对(首 START / 尾 END)。对规整后的序列做,此时首尾必然各有一个。 */
object CacheMarkerValidator {
    data class MarkerResult(val ok: Boolean, val message: String? = null)

    fun validate(records: List<CgmRecord>): MarkerResult {
        val first = records.firstOrNull()
        val last = records.lastOrNull()
        return if (first is CgmRecord.Marker && first.kind == MarkerKind.START &&
            last is CgmRecord.Marker && last.kind == MarkerKind.END
        ) {
            MarkerResult(ok = true)
        } else {
            MarkerResult(false, "marker 边界异常(缺 Start Playback / Playback all done)")
        }
    }
}

/**
 * 结构校验:以 LOG 段声明为骨架——`LOG:段号,类型,点数,时间`,类型 1=EIS 段(seq 1..95 连续、ts 60 步进 10)、
 * 2=CA 段(seq 1..N 连续,N 由声明给出,实测 213/266/268)。每段实际记录数必须与声明点数一致;
 * summarize 是段间隔记录,跳过。与实测三样本(07-15/07-20/07-21)严格对应。
 */
object CacheStructureValidator {
    data class StructureResult(val ok: Boolean, val message: String? = null)

    /** 解析 `LOG:段号,类型,点数,时间` → (type, count);格式异常返回 null。 */
    private fun parseLog(text: String): Pair<Int, Int>? {
        val m = LOG_SEGMENT_PATTERN.find(text) ?: return null
        return m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }

    fun validate(records: List<CgmRecord>): StructureResult {
        var expect: SegmentExpect? = null
        for (record in records) {
            when (record) {
                is CgmRecord.Log -> {
                    // 上一段结算:新声明到来时,若上一段未收满声明点数则失败
                    expect?.let {
                        if (it.seen != it.count) {
                            return StructureResult(false, "段 ${it.type} 声明 ${it.count} 点,实际 ${it.seen} 点")
                        }
                    }
                    val decl = parseLog(record.text) ?: return StructureResult(false, "LOG 段声明格式异常: ${record.text}")
                    val (type, count) = decl
                    if (type != EIS_TYPE && type != CA_TYPE) return StructureResult(false, "未知段类型: $type")
                    expect = SegmentExpect(type, count)
                }
                is CgmRecord.Summarize -> Unit   // 段间隔记录,跳过
                is CgmRecord.Marker -> Unit      // 去重已剔除,防御性跳过
                else -> {
                    val cur = expect ?: return StructureResult(false, "数据行前无段声明: ${record.text}")
                    when (cur.type) {
                        EIS_TYPE -> {
                            val eis = record as? CgmRecord.Eis
                                ?: return StructureResult(false, "EIS 段第 ${cur.seen + 1} 条非 EIS: ${record.text}")
                            if (eis.seq != cur.seen + 1) {
                                return StructureResult(false, "EIS seq ${eis.seq} 应为 ${cur.seen + 1}(连续)")
                            }
                            val expectedTs = 60 + cur.seen * 10
                            if (eis.ts != expectedTs) {
                                return StructureResult(false, "EIS ts ${eis.ts} 应为 $expectedTs(60 起步步进 10)")
                            }
                        }
                        else -> {   // CA_TYPE
                            val ca = record as? CgmRecord.Ca
                                ?: return StructureResult(false, "CA 段第 ${cur.seen + 1} 条非 CA: ${record.text}")
                            if (ca.seq != cur.seen + 1) {
                                return StructureResult(false, "CA seq ${ca.seq} 应为 ${cur.seen + 1}(连续)")
                            }
                        }
                    }
                    cur.seen += 1
                }
            }
        }
        // 尾部结算:数据结束,最后一段未收满声明点数 → 不完整
        expect?.let {
            if (it.seen != it.count) {
                return StructureResult(false, "段 ${it.type} 声明 ${it.count} 点,实际 ${it.seen} 点")
            }
        }
        // 空回放(缓存已消费,无段声明无数据点)→ 合法完成,零记录,不判失败不做重传
        // (有段声明但数据不足已在尾部结算失败;有数据无声明已在循环内失败——此分支只达空回放)
        if (records.none { it is CgmRecord.Eis || it is CgmRecord.Ca }) {
            return StructureResult(ok = true)
        }
        return StructureResult(ok = true)
    }

    private data class SegmentExpect(val type: Int, val count: Int, var seen: Int = 0)

    private const val EIS_TYPE = 1
    private const val CA_TYPE = 2
    private val LOG_SEGMENT_PATTERN = Regex("""LOG:\d+,(\d+),(\d+),""")
}
