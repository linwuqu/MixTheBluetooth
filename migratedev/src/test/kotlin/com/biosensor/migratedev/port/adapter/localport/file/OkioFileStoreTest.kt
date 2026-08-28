package com.biosensor.migratedev.port.adapter.localport.file

import com.biosensor.migratedev.port.adapter.localport.FileDeleteResult
import com.biosensor.migratedev.port.adapter.localport.FileSpace
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OkioFileStoreTest {
    private lateinit var fileSystem: FakeFileSystem
    private lateinit var store: OkioFileStore

    @Before
    fun setUp() {
        fileSystem = FakeFileSystem()
        store = OkioFileStore(
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
        store.write(FileSpace.LOGS, "workflow/connection.log")
            .buffer()
            .use { it.writeUtf8("connected") }

        val text = store.read(
            FileSpace.LOGS,
            "workflow/connection.log"
        ).buffer().use { it.readUtf8() }

        assertEquals("connected", text)
        assertEquals(
            listOf("workflow/connection.log"),
            store.list(FileSpace.LOGS, "workflow")
                .map { it.relativePath }
        )
        assertEquals(
            FileDeleteResult.Done,
            store.delete(
                FileSpace.LOGS,
                "workflow/connection.log"
            )
        )
        assertEquals(
            FileDeleteResult.None,
            store.delete(
                FileSpace.LOGS,
                "workflow/connection.log"
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `relative path cannot escape its file space`() {
        store.write(FileSpace.LOGS, "../outgoing/secret.bin")
    }
}
