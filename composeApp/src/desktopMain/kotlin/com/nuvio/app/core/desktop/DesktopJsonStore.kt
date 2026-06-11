package com.nuvio.app.core.desktop

import com.nuvio.app.desktop.DesktopRuntimeLog
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

internal class DesktopJsonStore(
    private val file: Path,
) {
    fun readTextOrNull(): String? = readTextOrNull(maxBytes = Long.MAX_VALUE)

    /**
     * Reads the file, but if it has grown past [maxBytes] it is treated as corrupt: the file is
     * quarantined (renamed to `.corrupt-<ts>`) and `null` is returned so the caller starts fresh
     * instead of loading a multi-hundred-MB blob into the heap and crashing the app on startup.
     * Settings/secure-store files are only ever a few KB, so a multi-MB file is always corruption.
     */
    fun readTextOrNull(maxBytes: Long): String? {
        if (!file.exists()) return null
        val size = runCatching { Files.size(file) }.getOrDefault(0L)
        if (size > maxBytes) {
            quarantineCorruptFile(size)
            return null
        }
        return Files.readString(file, StandardCharsets.UTF_8)
    }

    private fun quarantineCorruptFile(size: Long) {
        runCatching {
            DesktopRuntimeLog.warn(
                "DesktopJsonStore quarantined oversized file name=${file.fileName} sizeBytes=$size",
            )
        }
        val moved = runCatching {
            val backup = file.resolveSibling("${file.fileName}.corrupt-${System.currentTimeMillis()}")
            Files.move(file, backup, REPLACE_EXISTING)
        }.isSuccess
        if (!moved) {
            runCatching { Files.deleteIfExists(file) }
        }
    }

    fun writeText(payload: String) {
        val parent = file.parent
        parent?.createDirectories()
        val temp = Files.createTempFile(parent, file.fileName.toString(), ".tmp")
        Files.writeString(temp, payload, StandardCharsets.UTF_8)
        try {
            Files.move(temp, file, REPLACE_EXISTING, ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, file, REPLACE_EXISTING)
        }
    }

    fun clear() {
        file.deleteIfExists()
    }
}
