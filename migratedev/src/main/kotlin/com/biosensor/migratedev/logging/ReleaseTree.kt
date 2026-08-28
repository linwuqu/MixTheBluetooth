package com.biosensor.migratedev.logging

import com.biosensor.migratedev.port.adapter.localport.FileSpace
import com.biosensor.migratedev.port.adapter.localport.FileStore
import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okio.buffer
import okio.use
import timber.log.Timber
import java.io.Closeable

class ReleaseTree(
    private val files: FileStore,
    scope: CoroutineScope,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS
) : Timber.Tree(), Closeable {
    private val messages = Channel<LogMessage>(capacity = 256)
    private var currentFile: String? = null
    private var fileSequence = 0L

    init {
        scope.launch {
            for (message in messages) {
                write(message)
            }
        }
    }

    override fun isLoggable(
        tag: String?, priority: Int
    ): Boolean = priority >= Log.INFO

    override fun log(
        priority: Int, tag: String?, message: String, t: Throwable?
    ) {
        if (!isLoggable(tag, priority)) {
            return
        }
        messages.trySend(
            LogMessage(
                timestampMillis = nowMillis(),
                priority = priority,
                tag = tag ?: DEFAULT_TAG,
                message = redact(message),
                throwable = t
            )
        )
    }

    override fun close() {
        messages.close()
    }

    @SuppressLint("LogNotTimber")
    private fun write(message: LogMessage) {
        try {
            rotateIfNecessary(message.timestampMillis)
            val fileName = currentFile ?: newFileName(
                message.timestampMillis
            ).also { currentFile = it }
            files.write(
                FileSpace.LOGS, fileName, append = true
            ).buffer().use { sink ->
                sink.writeUtf8(message.format())
                sink.writeUtf8("\n")
            }
        } catch (failure: Exception) {
            // Timber would call this Tree again; platform Log is the fallback.
            Log.e(
                DEFAULT_TAG, "Release log file write failed", failure
            )
        }
    }

    private fun rotateIfNecessary(now: Long) {
        val fileName = currentFile ?: return
        val size =
            files.list(FileSpace.LOGS).firstOrNull { it.relativePath == fileName }?.size ?: return
        if (size < maxFileBytes) {
            return
        }
        currentFile = null
        pruneIfNecessary(now)
    }

    // 轮转策略(原契约里 prune 的语义):保留 maxFiles - 1 个,超龄优先删;失败不影响日志写入
    private fun pruneIfNecessary(now: Long) {
        try {
            val expiredBefore = now - maxAgeMillis
            files.list(FileSpace.LOGS)
                .sortedByDescending { it.lastModifiedAtMillis ?: Long.MIN_VALUE }
                .filterIndexed { index, entry ->
                    index >= maxFiles - 1 || (entry.lastModifiedAtMillis
                        ?: Long.MAX_VALUE) < expiredBefore
                }
                .forEach { entry ->
                    files.delete(FileSpace.LOGS, entry.relativePath)
                }
        } catch (_: Exception) {
            // 轮转失败不丢弃当前日志
        }
    }

    private fun newFileName(now: Long): String = "migratedev-$now-${fileSequence++}.log"

    private fun redact(message: String): String = SECRET_PATTERN.replace(message) { match ->
        "${match.groupValues[1]}=***"
    }

    private fun LogMessage.format(): String {
        val throwableText = throwable?.stackTraceToString()?.let { "\n$it" }.orEmpty()
        return "$timestampMillis ${priorityName(priority)}/$tag: " + "$message$throwableText"
    }

    private fun priorityName(priority: Int): String = when (priority) {
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        else -> priority.toString()
    }

    private data class LogMessage(
        val timestampMillis: Long,
        val priority: Int,
        val tag: String,
        val message: String,
        val throwable: Throwable?
    )

    private companion object {
        const val DEFAULT_TAG = "MigrateDev"
        const val DEFAULT_MAX_FILE_BYTES = 5L * 1024 * 1024
        const val DEFAULT_MAX_FILES = 5
        const val DEFAULT_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
        val SECRET_PATTERN = Regex(
            "(?i)\\b(token|password|userId|deviceId)=" + "\\S+"
        )
    }
}
