package com.nuvio.app.core.diagnostics

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

internal class DesktopDiagnosticLogFile(
    private val directory: Path,
    private val maxBytes: Long = 512L * 1024L,
    private val maxBackups: Int = 3,
) {
    private val fileName = "nuvio.log"
    private val activeFile: Path = directory.resolve(fileName)

    fun append(text: String) {
        directory.createDirectories()
        if (activeFile.exists() && Files.size(activeFile) + text.toByteArray(StandardCharsets.UTF_8).size > maxBytes) {
            rotate()
        }
        Files.writeString(
            activeFile,
            text,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    fun activePath(): Path = activeFile

    private fun rotate() {
        for (index in maxBackups downTo 1) {
            val source = if (index == 1) activeFile else directory.resolve("nuvio-${index - 1}.log")
            val target = directory.resolve("nuvio-$index.log")
            if (source.exists()) {
                Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
