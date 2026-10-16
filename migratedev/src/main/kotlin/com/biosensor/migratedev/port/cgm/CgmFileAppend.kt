package com.biosensor.migratedev.port.cgm

import com.biosensor.migratedev.decisioncore.cgm.CgmFileAppendResult
import com.biosensor.migratedev.decisioncore.cgm.CgmRecord

/** 例：文件已有 LOG A；本次收到 A、B。跳过整段 A，只追加完整的 B。 */
internal object CgmFileAppend {
    data class Plan(val text: String, val result: CgmFileAppendResult)

    fun plan(path: String, existing: String, records: List<CgmRecord>): Plan {
        val existingSegments = segments(existing.lineSequence().toList())
        val seen = existingSegments.toMutableSet()
        val received = segments(records.filter { it !is CgmRecord.Marker }.map { it.text })
        val added = mutableListOf<List<String>>()
        val duplicates = mutableListOf<List<String>>()
        for (segment in received) {
            if (seen.add(segment)) added += segment else duplicates += segment
        }
        val newLines = added.flatten()
        val text = if (newLines.isEmpty()) "" else buildString {
            if (existing.isNotEmpty() && !existing.endsWith('\n')) append('\n')
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

    private fun segments(lines: List<String>): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        for (line in lines) {
            if (line.isBlank() || line == "Start Playback" || line == "Playback all done") continue
            if (line.startsWith("LOG:") && current.isNotEmpty()) {
                result += current
                current = mutableListOf()
            }
            current += line
        }
        if (current.isNotEmpty()) result += current
        return result
    }

    private fun pointCount(lines: List<String>): Int =
        lines.count { it.startsWith("EIS:") || it.startsWith("CA:") }
}
