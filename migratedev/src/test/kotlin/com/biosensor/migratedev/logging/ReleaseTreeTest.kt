package com.biosensor.migratedev.logging

import com.biosensor.migratedev.port.adapter.localport.FileSpace
import com.biosensor.migratedev.port.adapter.localport.file.OkioFileStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class ReleaseTreeTest {
    private lateinit var fileSystem: FakeFileSystem
    private lateinit var files: OkioFileStore

    @Before
    fun setUp() {
        Timber.uprootAll()
        fileSystem = FakeFileSystem()
        files = OkioFileStore(
            fileSystem = fileSystem,
            roots = FileSpace.entries.associateWith {
                "/app/${it.name.lowercase()}".toPath()
            }
        )
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
        fileSystem.checkNoOpenFiles()
        fileSystem.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `release tree keeps info hides debug and redacts token`() = runTest {
        val tree = ReleaseTree(
            files = files,
            scope = this,
            nowMillis = { 1234L }
        )
        Timber.plant(tree)

        Timber.tag("Connection.Workflow").d("debug-only")
        Timber.tag("Connection.Workflow").i(
            "connected token=secret-token"
        )
        tree.close()
        advanceUntilIdle()

        val entries = files.list(FileSpace.LOGS)
        assertEquals(1, entries.size)
        val text = files.read(
            FileSpace.LOGS,
            entries.single().relativePath
        ).buffer().use { it.readUtf8() }

        assertTrue(text.contains("Connection.Workflow"))
        assertTrue(text.contains("connected"))
        assertTrue(text.contains("token=***"))
        assertFalse(text.contains("secret-token"))
        assertFalse(text.contains("debug-only"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `rotation keeps the configured total file count`() = runTest {
        var now = 2_000L
        val tree = ReleaseTree(
            files = files,
            scope = this,
            nowMillis = { now++ },
            maxFileBytes = 1,
            maxFiles = 2
        )
        Timber.plant(tree)

        repeat(3) { index ->
            Timber.tag("Connection.Workflow").i("message-$index")
        }
        tree.close()
        advanceUntilIdle()

        assertEquals(2, files.list(FileSpace.LOGS).size)
    }
}
