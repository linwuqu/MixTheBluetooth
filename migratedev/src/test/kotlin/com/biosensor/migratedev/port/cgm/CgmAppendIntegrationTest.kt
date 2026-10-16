package com.biosensor.migratedev.port.cgm

import com.biosensor.migratedev.decisioncore.cgm.*
import com.biosensor.migratedev.orchestrator.cgm.SequentialCgmParsePipeline
import com.biosensor.migratedev.port.adapter.bluetoothport.*
import com.biosensor.migratedev.port.adapter.localport.FileSpace
import com.biosensor.migratedev.port.adapter.localport.file.OkioFileStore
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CgmAppendIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `real sample appends to a real file and repeated read leaves it unchanged`() = runBlocking {
        val sample = File("../docs/数据样例/2026-07-15CGM_Cache_data.txt")
        val pipeline = SequentialCgmParsePipeline()
        val received = mutableListOf<CgmRecord>()
        sample.readBytes().toList().chunked(100).forEach { received += pipeline.parse(it.toByteArray()) }
        val validated = CacheValidators.validate(received)
        assertTrue(validated.reason, validated.complete)
        val files = OkioFileStore(FileSystem.SYSTEM, FileSpace.entries.associateWith {
            temporary.root.resolve(it.name).absolutePath.toPath()
        })
        val bluetooth = object : BluetoothPort {
            override fun execute(effect: BluetoothEffect): Flow<BluetoothEvent> = flowOf()
            override fun scanDevices(): Flow<BluetoothDeviceInfo> = flowOf()
        }
        val port = CgmPortAdapter(bluetooth, files)
        val session = CgmSession("s1", "AA:01")
        val first = port.execute(CgmReadEffect.WriteFile(session, validated.records)).toList().single() as CgmReadEvent.FileWritten
        val target = temporary.root.resolve("RECEIVED/${first.path}")
        val original = target.readText()
        assertFalse(original.contains("Start Playback"))
        assertFalse(original.contains("Playback all done"))
        assertEquals(validated.records.joinToString("\n", postfix = "\n") { it.text }, original)
        val repeated = port.execute(CgmReadEffect.WriteFile(session.copy(id = "s2"), validated.records)).toList().single() as CgmReadEvent.FileWritten
        assertEquals(0, repeated.result!!.addedPoints)
        assertEquals(first.result!!.addedPoints, repeated.result!!.duplicatePoints)
        assertEquals(original, target.readText())
        val newSegment = listOf(CgmRecord.Log("LOG:999,1,95,2026-10-09 12:00:00")) +
            validated.records.drop(1).take(95)
        val appended = port.execute(CgmReadEffect.WriteFile(session.copy(id = "s3"), newSegment))
            .toList().single() as CgmReadEvent.FileWritten
        assertEquals(95, appended.result!!.addedPoints)
        assertEquals(first.result!!.totalPoints + 95, appended.result!!.totalPoints)
        assertEquals(original + newSegment.joinToString("\n", postfix = "\n") { it.text }, target.readText())
    }
}
