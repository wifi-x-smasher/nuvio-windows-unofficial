package com.nuvio.app.core.desktop

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
    fun readTextOrNull(): String? {
        if (!file.exists()) return null
        return Files.readString(file, StandardCharsets.UTF_8)
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
