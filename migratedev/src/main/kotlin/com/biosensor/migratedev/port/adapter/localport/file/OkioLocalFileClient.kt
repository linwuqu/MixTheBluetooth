package com.biosensor.migratedev.port.adapter.localport.file

import com.biosensor.migratedev.port.adapter.localport.FileDeleteResult
import com.biosensor.migratedev.port.adapter.localport.FileEntry
import com.biosensor.migratedev.port.adapter.localport.FilePruneResult
import com.biosensor.migratedev.port.adapter.localport.FileSpace
import com.biosensor.migratedev.port.adapter.localport.LocalFileClient
import com.biosensor.migratedev.port.adapter.localport.RetentionPolicy
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source

class OkioLocalFileClient(
    private val fileSystem: FileSystem, roots: Map<FileSpace, Path>
) : LocalFileClient {
    private val roots = roots.mapValues { (_, path) ->
        path.toString().toPath(normalize = true)
    }

    init {
        require(FileSpace.entries.all(roots::containsKey)) {
            "Every FileSpace must have a root"
        }
    }

    override fun source(
        space: FileSpace, relativePath: String
    ): Source = fileSystem.source(resolve(space, relativePath))

    override fun sink(
        space: FileSpace, relativePath: String, append: Boolean
    ): Sink {
        val path = resolve(space, relativePath)
        path.parent?.let(fileSystem::createDirectories)
        return if (append) {
            fileSystem.appendingSink(path)
        } else {
            fileSystem.sink(path)
        }
    }

    override fun list(
        space: FileSpace, relativePath: String
    ): List<FileEntry> {
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
                    lastModifiedAtMillis = metadata.lastModifiedAtMillis
                )
            }
        }.sortedBy(FileEntry::relativePath)
    }

    override fun delete(
        space: FileSpace, relativePath: String
    ): FileDeleteResult {
        val path = resolve(space, relativePath)
        if (!fileSystem.exists(path)) {
            return FileDeleteResult.Missing
        }
        return try {
            fileSystem.delete(path)
            FileDeleteResult.Deleted
        } catch (failure: Exception) {
            FileDeleteResult.Failed(
                failure.message ?: "文件删除失败"
            )
        }
    }

    override fun prune(
        space: FileSpace, policy: RetentionPolicy
    ): FilePruneResult {
        return try {
            require(policy.maxFiles >= 0)
            require(policy.maxAgeMillis >= 0)
            val entries = list(space).sortedByDescending {
                it.lastModifiedAtMillis ?: Long.MIN_VALUE
            }
            val expiredBefore = policy.nowMillis - policy.maxAgeMillis
            val toDelete = entries.filterIndexed { index, entry ->
                index >= policy.maxFiles || (entry.lastModifiedAtMillis
                    ?: Long.MAX_VALUE) < expiredBefore
            }
            val deleted = toDelete.count { entry ->
                delete(space, entry.relativePath) == FileDeleteResult.Deleted
            }
            FilePruneResult.Pruned(deleted)
        } catch (failure: Exception) {
            FilePruneResult.Failed(
                failure.message ?: "文件清理失败"
            )
        }
    }

    private fun resolve(
        space: FileSpace, relativePath: String
    ): Path {
        val root = roots.getValue(space)
        val candidate = root.resolve(
            relativePath.toPath(), normalize = true
        )
        require(
            candidate.segments.size >= root.segments.size && candidate.segments.take(root.segments.size) == root.segments
        ) {
            "Path escapes ${space.name} root"
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
