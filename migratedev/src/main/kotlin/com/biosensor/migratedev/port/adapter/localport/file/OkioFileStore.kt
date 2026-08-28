package com.biosensor.migratedev.port.adapter.localport.file

import android.content.Context
import com.biosensor.migratedev.port.adapter.localport.FileDeleteResult
import com.biosensor.migratedev.port.adapter.localport.FileEntry
import com.biosensor.migratedev.port.adapter.localport.FileSpace
import com.biosensor.migratedev.port.adapter.localport.FileStore
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source

class OkioFileStore internal constructor(
    private val fileSystem: FileSystem,
    roots: Map<FileSpace, Path>,
) : FileStore {

    private val roots = roots.mapValues { (_, path) ->
        path.toString().toPath(normalize = true)
    }

    init {
        require(FileSpace.entries.all(roots::containsKey)) {
            "每个文件分区都需要根目录"
        }
    }

    companion object {
        /** 分区根目录:业务数据放 filesDir(持久),临时产物放 cacheDir(系统可清)。 */
        fun create(context: Context): FileStore {
            val application = context.applicationContext
            return OkioFileStore(
                fileSystem = FileSystem.SYSTEM,
                roots = mapOf(
                    FileSpace.LOGS to application.filesDir.resolve("logs").absolutePath.toPath(),
                    FileSpace.RECEIVED to application.filesDir.resolve("received").absolutePath.toPath(),
                    FileSpace.OUTGOING to application.filesDir.resolve("outgoing").absolutePath.toPath(),
                    FileSpace.CACHE to application.cacheDir.resolve("cache").absolutePath.toPath(),
                ),
            )
        }
    }

    override fun read(space: FileSpace, relativePath: String): Source =
        fileSystem.source(resolve(space, relativePath))

    override fun write(space: FileSpace, relativePath: String, append: Boolean): Sink {
        val path = resolve(space, relativePath)
        path.parent?.let(fileSystem::createDirectories)
        return if (append) {
            fileSystem.appendingSink(path)
        } else {
            fileSystem.sink(path)
        }
    }

    override fun list(space: FileSpace, relativePath: String): List<FileEntry> {
        val root = roots.getValue(space)
        val directory = resolve(space, relativePath)
        return collectFiles(directory).mapNotNull { path ->
            val metadata = fileSystem.metadataOrNull(path) ?: return@mapNotNull null
            if (metadata.isDirectory) {
                null
            } else {
                FileEntry(
                    relativePath = path.relativeTo(root).toString(),
                    size = metadata.size,
                    lastModifiedAtMillis = metadata.lastModifiedAtMillis,
                )
            }
        }.sortedBy(FileEntry::relativePath)
    }

    override fun delete(space: FileSpace, relativePath: String): FileDeleteResult {
        val path = resolve(space, relativePath)
        if (!fileSystem.exists(path)) {
            return FileDeleteResult.None
        }
        return try {
            fileSystem.delete(path)
            FileDeleteResult.Done
        } catch (failure: Exception) {
            FileDeleteResult.Failed(failure.message ?: "文件删除失败")
        }
    }

    private fun resolve(space: FileSpace, relativePath: String): Path {
        val root = roots.getValue(space)
        val candidate = root.resolve(
            relativePath.toPath(), normalize = true
        )
        require(
            candidate.segments.size >= root.segments.size && candidate.segments.take(root.segments.size) == root.segments
        ) {
            "路径逃逸出 ${space.name} 根目录"
        }
        return candidate
    }

    private fun collectFiles(directory: Path): List<Path> {
        val children = fileSystem.listOrNull(directory).orEmpty()
        return children.flatMap { child ->
            if (fileSystem.metadata(child).isDirectory) {
                collectFiles(child)
            } else {
                listOf(child)
            }
        }
    }
}
