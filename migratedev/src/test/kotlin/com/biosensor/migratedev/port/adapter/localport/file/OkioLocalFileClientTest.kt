package com.biosensor.migratedev.port.adapter.localport.file

import com.biosensor.migratedev.port.adapter.localport.FileDeleteResult
import com.biosensor.migratedev.port.adapter.localport.FileSpace
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OkioLocalFileClientTest {
    private lateinit var fileSystem: FakeFileSystem
    private lateinit var client: OkioLocalFileClient

    @Before
    fun setUp() {
        fileSystem = FakeFileSystem()
        client = OkioLocalFileClient(
            fileSystem = fileSystem,
            roots = mapOf(
                FileSpace.LOGS to "/app/logs".toPath(),
                FileSpace.RECEIVED to "/app/received".toPath(),
                FileSpace.OUTGOING to "/app/outgoing".toPath(),
                FileSpace.CACHE to "/app/cache".toPath()
            )
        )
    }

    @After
    fun tearDown() {
        fileSystem.checkNoOpenFiles()
        fileSystem.close()
    }

    @Test
    fun `write read list and delete stay inside selected file space`() {
        client.sink(FileSpace.LOGS, "workflow/connection.log")
            .buffer()
            .use { it.writeUtf8("connected") }

        val text = client.source(
            FileSpace.LOGS,
            "workflow/connection.log"
        ).buffer().use { it.readUtf8() }

        assertEquals("connected", text)
        assertEquals(
            listOf("workflow/connection.log"),
            client.list(FileSpace.LOGS, "workflow")
                .map { it.relativePath }
        )
        assertEquals(
            FileDeleteResult.Deleted,
            client.delete(
                FileSpace.LOGS,
                "workflow/connection.log"
            )
        )
        assertEquals(
            FileDeleteResult.Missing,
            client.delete(
                FileSpace.LOGS,
                "workflow/connection.log"
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `relative path cannot escape its file space`() {
        client.sink(FileSpace.LOGS, "../outgoing/secret.bin")
    }
}
