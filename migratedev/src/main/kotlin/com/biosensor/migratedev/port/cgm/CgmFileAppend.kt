package com.biosensor.migratedev.port.cgm

import com.biosensor.migratedev.decisioncore.cgm.CgmFileAppendResult
import com.biosensor.migratedev.decisioncore.cgm.CgmRecord

/**
 * 入库校验:追加写之前,把本次记录和文件里已有的比一遍,整段重复的丢掉。
 * 例:文件已有 LOG A;本次收到 A、B。跳过整段 A,只追加完整的 B。
 */
internal object CgmFileAppend {
    data class Plan(val text: String, val result: CgmFileAppendResult)

    /**
     * @param existing 文件里已有的记录(已解析,不是原始文本)
     * @param records 本次待写入的记录
     * @param appendLeadingNewline 文件非空且结尾没有换行时为 true——先补一个换行再接新段
     */
    fun plan(
        path: String,
        existing: List<CgmRecord>,
        records: List<CgmRecord>,
        appendLeadingNewline: Boolean,
    ): Plan {
        val existingSegments = segments(existing)
        val seen = existingSegments.toMutableSet()
        val added = mutableListOf<List<String>>()
        val duplicates = mutableListOf<List<String>>()
        for (segment in segments(records)) {
            if (seen.add(segment)) added += segment else duplicates += segment
        }
        val newLines = added.flatten()
        val text = if (newLines.isEmpty()) "" else buildString {
            if (appendLeadingNewline) append('\n')
            newLines.forEach { appendLine(it) }
        }
        return Plan(text, CgmFileAppendResult(
            path = path,
            addedSegments = added.count { it.first().startsWith("LOG:") },
            duplicateSegments = duplicates.count { it.first().startsWith("LOG:") },
            addedPoints = pointCount(newLines),
            duplicatePoints = pointCount(duplicates.flatten()),
            totalPoints = pointCount(existingSegments.flatten()) + pointCount(newLines)
        ))
    }

    /** 按 LOG 行切段:一个 LOG 声明带它后面的数据点。边界标记不是数据,不参与分段。 */
    private fun segments(records: List<CgmRecord>): List<List<String>> =
        records.asSequence()
            .filter { it !is CgmRecord.Marker }
            .map { it.text }
            .fold(mutableListOf<MutableList<String>>()) { acc, line ->
                if (line.startsWith("LOG:") || acc.isEmpty()) acc.add(mutableListOf())
                acc.last() += line
                acc
            }

    private fun pointCount(lines: List<String>): Int =
        lines.count { it.startsWith("EIS:") || it.startsWith("CA:") }
}
